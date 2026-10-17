package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.AllocationOptimizationRequest;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse.Metrics;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse.ReservationMove;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.TableSummary;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Demand;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.OptTable;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Params;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Plan;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AllocationOptimizationService {

    private static final int DEFAULT_MAX_MOVES = 10;
    private static final int DEFAULT_LOCK_WINDOW_MINUTES = 60;
    private static final int TURNAROUND_BUFFER_MINUTES = 15;
    private static final int MAX_TABLES_PER_COMBINATION = 3;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofHours(72);
    /** Bookings just outside the window still block tables near its edges, so they are loaded as pinned. */
    private static final Duration EDGE_PADDING = Duration.ofHours(6);
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.IN_PROGRESS
    );

    RestaurantTableRepository restaurantTableRepository;
    ReservationTableRepository reservationTableRepository;

    /** Computes the plan without changing anything. */
    @Transactional(readOnly = true)
    public AllocationOptimizationResponse preview(AllocationOptimizationRequest request) {
        Computation c = compute(request);
        return buildResponse(c, false);
    }

    @Transactional
    public AllocationOptimizationResponse apply(AllocationOptimizationRequest request) {
        Computation c = compute(request);
        if (c.plan.moves().isEmpty()) {
            return buildResponse(c, false);
        }

        Duration buffer = Duration.ofMinutes(TURNAROUND_BUFFER_MINUTES);

        // 1) release the old links of every moved reservation first, so moves may reuse each other's tables
        for (AllocationOptimizationEngine.Move m : c.plan.moves()) {
            reservationTableRepository.deleteByReservationId(m.reservationId());
        }
        reservationTableRepository.flush();

        // 2) re-validate against the database and write the new links
        for (AllocationOptimizationEngine.Move m : c.plan.moves()) {
            Reservation reservation = c.reservations.get(m.reservationId());
            List<ReservationTable> conflicts = reservationTableRepository
                    .findConflictingReservationsExcludingReservation(
                            m.toTableIds(),
                            m.reservationId(),
                            BLOCKING_STATUSES,
                            reservation.getStartTime().minus(buffer),
                            reservation.getEndTime().plus(buffer));
            if (!conflicts.isEmpty()) {
                throw new ReservationConflictException("Reservations changed while optimizing; "
                        + "reservation " + reservation.getReservationNo()
                        + " can no longer be moved. Please run the optimization again.");
            }
            List<ReservationTable> links = m.toTableIds().stream()
                    .map(id -> ReservationTable.builder()
                            .reservation(reservation)
                            .table(c.tables.get(id))
                            .build())
                    .toList();
            reservationTableRepository.saveAll(links);
            reservationTableRepository.flush();
        }
        return buildResponse(c, true);
    }

    public static int requiredSeats(PartySize partySize) {
        if (partySize == null) return 0;
        return switch (partySize) {
            case SINGLE -> 1;
            case SMALL -> 4;
            case MEDIUM -> 8;
            case LARGE -> 12;
            case XLARGE -> 16;
        };
    }

    // internals

    private record Computation(Instant windowStart, Instant windowEnd, int inScope, int movable, Plan plan,
                               Map<Long, Reservation> reservations, Map<Long, RestaurantTable> tables) {
    }

    private Computation compute(AllocationOptimizationRequest request) {
        Instant now = Instant.now();
        Instant windowStart = request.getWindowStart() != null ? request.getWindowStart() : now;
        if (windowStart.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new RequestInvalidException("Window start cannot be in the past");
        }
        Instant windowEnd = request.getWindowEnd() != null ? request.getWindowEnd() : windowStart.plus(DEFAULT_WINDOW);
        if (!windowEnd.isAfter(windowStart)) {
            throw new RequestInvalidException("Window end must be after window start");
        }
        if (Duration.between(windowStart, windowEnd).compareTo(MAX_WINDOW) > 0) {
            throw new RequestInvalidException("Optimization window cannot exceed 72 hours");
        }

        int lockMinutes = request.getLockWindowMinutes() != null
                ? request.getLockWindowMinutes() : DEFAULT_LOCK_WINDOW_MINUTES;
        int maxMoves = request.getMaxMoves() != null ? request.getMaxMoves() : DEFAULT_MAX_MOVES;
        boolean includeConfirmed = request.getIncludeConfirmed() == null || request.getIncludeConfirmed();
        Instant lockUntil = now.plus(Duration.ofMinutes(lockMinutes));

        Map<Long, RestaurantTable> tables = new LinkedHashMap<>();
        List<OptTable> optTables = new ArrayList<>();
        for (RestaurantTable t : restaurantTableRepository.findAllByOrderByTableNumberAsc()) {
            tables.put(t.getId(), t);
            optTables.add(new OptTable(
                    t.getId(),
                    t.getTableNumber() != null ? t.getTableNumber() : 0,
                    t.getCapacity(),
                    t.getArea() != null ? t.getArea().getId() : null,
                    t.getStatusCode() != TableStatus.LOCKED));
        }

        List<ReservationTable> rows = tables.isEmpty() ? List.of()
                : reservationTableRepository.findActiveBookingsInWindow(
                tables.keySet(), BLOCKING_STATUSES,
                windowStart.minus(EDGE_PADDING), windowEnd.plus(EDGE_PADDING));

        Map<Long, Reservation> reservations = new LinkedHashMap<>();
        Map<Long, List<Long>> tablesByReservation = new LinkedHashMap<>();
        for (ReservationTable rt : rows) {
            Reservation r = rt.getReservation();
            reservations.putIfAbsent(r.getId(), r);
            tablesByReservation.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(rt.getTable().getId());
        }

        List<Demand> demands = new ArrayList<>();
        int inScopeCount = 0;
        int movableCount = 0;
        for (Map.Entry<Long, Reservation> e : reservations.entrySet()) {
            Reservation r = e.getValue();
            List<Long> assigned = tablesByReservation.get(e.getKey());
            boolean inScope = r.getStartTime().isBefore(windowEnd) && r.getEndTime().isAfter(windowStart);
            boolean pinned = !inScope
                    || r.getReservationStatus() == ReservationStatus.IN_PROGRESS
                    || (r.getReservationStatus() == ReservationStatus.CONFIRMED && !includeConfirmed)
                    || r.getStartTime().isBefore(lockUntil);
            int seats = requiredSeats(r.getPartySize());
            if (seats == 0) {
                // unknown party size: assume the current tables are right-sized
                seats = assigned.stream().mapToInt(id -> tables.get(id).getCapacity()).sum();
            }
            if (inScope) inScopeCount++;
            if (!pinned) movableCount++;
            demands.add(new Demand(r.getId(), seats, r.getStartTime(), r.getEndTime(), assigned, !pinned, inScope));
        }

        Plan plan = AllocationOptimizationEngine.optimize(
                new Params(TURNAROUND_BUFFER_MINUTES, maxMoves, MAX_TABLES_PER_COMBINATION), optTables, demands);
        return new Computation(windowStart, windowEnd, inScopeCount, movableCount, plan, reservations, tables);
    }

    private AllocationOptimizationResponse buildResponse(Computation c, boolean applied) {
        List<ReservationMove> moves = new ArrayList<>();
        for (AllocationOptimizationEngine.Move m : c.plan.moves()) {
            Reservation r = c.reservations.get(m.reservationId());
            moves.add(ReservationMove.builder()
                    .reservationId(r.getId())
                    .reservationNo(r.getReservationNo())
                    .reservationStatus(r.getReservationStatus())
                    .partySize(r.getPartySize())
                    .requiredSeats(requiredSeats(r.getPartySize()))
                    .startTime(r.getStartTime())
                    .endTime(r.getEndTime())
                    .fromTables(summaries(m.fromTableIds(), c.tables))
                    .toTables(summaries(m.toTableIds(), c.tables))
                    .wastedSeatsBefore(m.wastedSeatsBefore())
                    .wastedSeatsAfter(m.wastedSeatsAfter())
                    .reason(reason(m, c.tables))
                    .build());
        }

        return AllocationOptimizationResponse.builder()
                .windowStart(c.windowStart)
                .windowEnd(c.windowEnd)
                .applied(applied)
                .reservationsInScope(c.inScope)
                .movableReservations(c.movable)
                .before(toMetrics(c.plan.before()))
                .after(toMetrics(c.plan.after()))
                .movesCount(moves.size())
                .moves(moves)
                .message(message(c, moves.size(), applied))
                .build();
    }

    private String message(Computation c, int moves, boolean applied) {
        if (c.inScope == 0) {
            return "There are no reservations in the selected window.";
        }
        if (c.movable == 0) {
            return "No reservation in the window can be moved (all are in progress, about to start or pinned).";
        }
        if (moves == 0) {
            return "The current allocation is already good: no table change improves it enough.";
        }
        int saved = c.plan.before().wastedSeats() - c.plan.after().wastedSeats();
        String base = moves + (moves == 1 ? " reservation" : " reservations")
                + (applied ? " moved" : " can be moved") + ", wasted seats " + c.plan.before().wastedSeats()
                + " -> " + c.plan.after().wastedSeats() + " (" + (saved >= 0 ? "-" : "+") + Math.abs(saved) + ")";
        return base + ".";
    }

    private String reason(AllocationOptimizationEngine.Move m, Map<Long, RestaurantTable> tables) {
        int fromCap = m.fromTableIds().stream().mapToInt(id -> tables.get(id).getCapacity()).sum();
        int toCap = m.toTableIds().stream().mapToInt(id -> tables.get(id).getCapacity()).sum();
        if (m.fromTableIds().size() > 1 && m.toTableIds().size() == 1) {
            return "Replaces " + m.fromTableIds().size() + " merged tables with a single table";
        }
        if (m.toTableIds().size() > m.fromTableIds().size()) {
            return "Uses a table combination so a larger single table is freed";
        }
        if (toCap < fromCap) {
            return "Fits a smaller table (saves " + (fromCap - toCap) + " seats), freeing capacity for larger parties";
        }
        return "Removes a short idle gap in the table schedule so the table can be resold";
    }

    private List<TableSummary> summaries(List<Long> ids, Map<Long, RestaurantTable> tables) {
        return ids.stream().map(id -> {
            RestaurantTable t = tables.get(id);
            return TableSummary.builder()
                    .id(t.getId())
                    .tableNumber(t.getTableNumber())
                    .capacity(t.getCapacity())
                    .areaId(t.getArea() != null ? t.getArea().getId() : null)
                    .areaCode(t.getArea() != null ? t.getArea().getCode() : null)
                    .areaName(t.getArea() != null ? t.getArea().getName() : null)
                    .status(t.getStatusCode())
                    .build();
        }).toList();
    }

    private Metrics toMetrics(AllocationOptimizationEngine.Metrics m) {
        return Metrics.builder()
                .reservations(m.reservations())
                .requiredSeats(m.demandSeats())
                .assignedSeats(m.assignedSeats())
                .wastedSeats(m.wastedSeats())
                .seatUtilizationPercent(m.seatUtilizationPercent())
                .idleGaps(m.idleGaps())
                .multiTableReservations(m.multiTableReservations())
                .build();
    }
}
