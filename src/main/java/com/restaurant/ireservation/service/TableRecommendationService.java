package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.TableRecommendationRequest;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationOption;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationType;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.ScoreBreakdown;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.TableSummary;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Option;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Result;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableRecommendationService {

    private static final int DEFAULT_DURATION_MINUTES = 90;
    private static final int DEFAULT_MAX_RESULTS = 5;
    /** Cleaning / turnaround time kept free between two bookings of the same table. */
    private static final int TURNAROUND_BUFFER_MINUTES = 15;
    private static final int MAX_TABLES_PER_COMBINATION = 3;
    /** How far around the requested slot we look for neighbouring bookings (for gap scoring). */
    private static final Duration NEIGHBOUR_LOOKAROUND = Duration.ofHours(6);
    /** A slot starting within this time from now is blocked by tables that are physically IN_USE / CLEANING. */
    private static final Duration OCCUPANCY_LOOKAHEAD = Duration.ofMinutes(30);
    /** Small tolerance so a "now" request that spends a few seconds on the wire is not rejected. */
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    /** Same blocking statuses used by ReservationService when it checks conflicts. */
    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.IN_PROGRESS
    );

    RestaurantTableRepository restaurantTableRepository;
    ReservationTableRepository reservationTableRepository;
    TableAreaRepository tableAreaRepository;

    @Transactional(readOnly = true)
    public TableRecommendationResponse recommend(TableRecommendationRequest request) {
        Instant now = Instant.now();
        Instant start = request.getStartTime() != null ? request.getStartTime() : now;
        if (start.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new RequestInvalidException("Start time cannot be in the past");
        }

        int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : DEFAULT_DURATION_MINUTES;
        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : DEFAULT_MAX_RESULTS;
        boolean allowCombination = request.getAllowCombination() == null || request.getAllowCombination();
        Instant end = start.plus(Duration.ofMinutes(duration));
        int guests = request.getGuestCount();

        Long preferredAreaId = request.getPreferredAreaId();
        if (preferredAreaId != null && !tableAreaRepository.existsById(preferredAreaId)) {
            throw new ResourceNotFoundException("Table area not found with id: " + preferredAreaId);
        }

        boolean startsSoon = start.isBefore(now.plus(OCCUPANCY_LOOKAHEAD));
        Map<Long, RestaurantTable> usableTables = new HashMap<>();
        List<TableCandidate> candidates = new ArrayList<>();
        for (RestaurantTable t : restaurantTableRepository.findAllByOrderByTableNumberAsc()) {
            if (!isUsable(t, startsSoon)) {
                continue;
            }
            usableTables.put(t.getId(), t);
            candidates.add(new TableCandidate(
                    t.getId(),
                    t.getTableNumber() != null ? t.getTableNumber() : 0,
                    t.getCapacity(),
                    t.getArea() != null ? t.getArea().getId() : null
            ));
        }

        List<Booking> bookings = candidates.isEmpty() ? List.of() : loadBookings(usableTables.keySet(), start, end);

        Query query = new Query(guests, start, end, preferredAreaId, maxResults, allowCombination,
                TURNAROUND_BUFFER_MINUTES, MAX_TABLES_PER_COMBINATION);
        Result result = TableRecommendationEngine.recommend(query, candidates, bookings);

        List<RecommendationOption> options = new ArrayList<>();
        int rank = 1;
        for (Option o : result.options()) {
            options.add(toOption(rank++, o, guests, usableTables));
        }

        return TableRecommendationResponse.builder()
                .guestCount(guests)
                .suggestedPartySize(toPartySize(guests))
                .startTime(start)
                .endTime(end)
                .durationMinutes(duration)
                .availableTableCount(result.freeTableCount())
                .recommendations(options)
                .message(buildMessage(options, result.freeTableCount(), guests))
                .build();
    }

    /**
     * Maps a head count to the PartySize bucket stored on Reservation.
     * Buckets: SINGLE = 1, SMALL = 2-4, MEDIUM = 5-8, LARGE = 9-12, XLARGE = 13+.
     */
    public static PartySize toPartySize(int guests) {
        if (guests <= 1) return PartySize.SINGLE;
        if (guests <= 4) return PartySize.SMALL;
        if (guests <= 8) return PartySize.MEDIUM;
        if (guests <= 12) return PartySize.LARGE;
        return PartySize.XLARGE;
    }

    private boolean isUsable(RestaurantTable table, boolean startsSoon) {
        TableStatus status = table.getStatusCode();
        if (status == TableStatus.LOCKED) {
            return false;
        }
        // IN_USE / CLEANING describe the table right now, so they only matter for slots starting soon.
        // For later slots, real conflicts are detected from the bookings instead.
        return !(startsSoon && (status == TableStatus.IN_USE || status == TableStatus.CLEANING));
    }

    private List<Booking> loadBookings(Collection<Long> tableIds, Instant start, Instant end) {
        List<ReservationTable> rows = reservationTableRepository.findActiveBookingsInWindow(
                tableIds,
                BLOCKING_STATUSES,
                start.minus(NEIGHBOUR_LOOKAROUND),
                end.plus(NEIGHBOUR_LOOKAROUND)
        );
        return rows.stream()
                .map(rt -> new Booking(
                        rt.getTable().getId(),
                        rt.getReservation().getStartTime(),
                        rt.getReservation().getEndTime()))
                .toList();
    }

    private RecommendationOption toOption(int rank, Option option, int guests, Map<Long, RestaurantTable> tables) {
        List<TableSummary> summaries = option.tables().stream()
                .map(c -> toSummary(tables.get(c.id())))
                .toList();

        return RecommendationOption.builder()
                .rank(rank)
                .type(option.isCombination() ? RecommendationType.TABLE_COMBINATION : RecommendationType.SINGLE_TABLE)
                .tableIds(option.tables().stream().map(TableCandidate::id).toList())
                .tables(summaries)
                .totalCapacity(option.totalCapacity())
                .spareSeats(option.totalCapacity() - guests)
                .score(option.score())
                .scoreBreakdown(ScoreBreakdown.builder()
                        .capacityFit(option.fitScore())
                        .schedulePacking(option.gapScore())
                        .areaMatch(option.areaScore())
                        .build())
                .reasons(option.reasons())
                .build();
    }

    private TableSummary toSummary(RestaurantTable t) {
        return TableSummary.builder()
                .id(t.getId())
                .tableNumber(t.getTableNumber())
                .capacity(t.getCapacity())
                .areaId(t.getArea() != null ? t.getArea().getId() : null)
                .areaCode(t.getArea() != null ? t.getArea().getCode() : null)
                .areaName(t.getArea() != null ? t.getArea().getName() : null)
                .status(t.getStatusCode())
                .build();
    }

    private String buildMessage(List<RecommendationOption> options, int freeTables, int guests) {
        if (options.isEmpty()) {
            if (freeTables == 0) {
                return "No tables are available in the requested time slot. Please try a different time.";
            }
            return "No table or table combination can seat " + guests
                    + " guests in the requested time slot. Please try a different time or consider a room.";
        }
        if (options.get(0).getType() == RecommendationType.TABLE_COMBINATION) {
            return "No single table can seat " + guests + " guests; showing table combinations instead.";
        }
        return null;
    }
}
