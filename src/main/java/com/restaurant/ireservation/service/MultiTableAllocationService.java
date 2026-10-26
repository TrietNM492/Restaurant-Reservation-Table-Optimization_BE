package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.*;
import com.restaurant.ireservation.dto.request.MultiTableAllocationRequest;
import com.restaurant.ireservation.dto.response.MultiTableAllocationResponse;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO.TableSummaryDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.*;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MultiTableAllocationService {
    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS);
    private static final Duration TURNAROUND_BUFFER = Duration.ofMinutes(15);
    private static final Duration OCCUPANCY_LOOKAHEAD = Duration.ofMinutes(30);
    private static final Duration SCORING_LOOKAROUND = Duration.ofHours(6);

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReservationTableRepository reservationTableRepository;

    /** Replaces the reservation's complete allocation atomically with the selected combination. */
    @Transactional
    public MultiTableAllocationResponse allocate(MultiTableAllocationRequest request) {
        List<Long> ids = request.getTableIds();
        if (ids == null || ids.size() < 2 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new RequestInvalidException("Select at least two tables with positive IDs");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new RequestInvalidException("Duplicate table IDs in request");
        }
        Reservation reservation = reservationRepository.findByIdForAllocation(request.getReservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + request.getReservationId()));
        if (reservation.getReservationType() != ReservationType.TABLE) {
            throw new RequestInvalidException("Only TABLE reservations can use table combinations");
        }
        if (reservation.getReservationStatus() != ReservationStatus.PENDING
                && reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Only PENDING or CONFIRMED reservations can be allocated");
        }
        Instant now = Instant.now();
        if (reservation.getStartTime() == null || reservation.getEndTime() == null
                || !reservation.getEndTime().isAfter(reservation.getStartTime())
                || !reservation.getEndTime().isAfter(now)) {
            throw new RequestInvalidException("Reservation must have a valid time slot that has not ended");
        }
        // Lock tables in ID order so competing combination requests serialize before checking availability.
        List<RestaurantTable> tables = restaurantTableRepository.findAllByIdForAllocation(ids);
        if (tables.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more requested tables do not exist");
        }
        Long areaId = tables.getFirst().getArea() == null ? null : tables.getFirst().getArea().getId();
        if (areaId == null || tables.stream().anyMatch(t -> t.getArea() == null || !areaId.equals(t.getArea().getId()))) {
            throw new RequestInvalidException("All tables in a combination must belong to the same area");
        }
        for (RestaurantTable table : tables) {
            if (table.getStatusCode() == null || table.getStatusCode() == TableStatus.LOCKED) {
                throw new RequestInvalidException("Table " + table.getTableNumber() + " is unavailable or locked");
            }
            if (reservation.getStartTime().isBefore(now.plus(OCCUPANCY_LOOKAHEAD))
                    && (table.getStatusCode() == TableStatus.IN_USE || table.getStatusCode() == TableStatus.CLEANING)) {
                throw new ReservationConflictException("Table " + table.getTableNumber() + " is in use or being cleaned");
            }
        }
        int requiredSeats = AllocationOptimizationService.requiredSeats(reservation.getPartySize());
        if (requiredSeats == 0 || tables.stream().mapToInt(RestaurantTable::getCapacity).sum() < requiredSeats) {
            throw new RequestInvalidException("The combination does not have enough seats for the reservation party size");
        }
        if (!reservationTableRepository.findConflictingReservationsExcludingReservation(ids, reservation.getId(),
                BLOCKING_STATUSES, reservation.getStartTime().minus(TURNAROUND_BUFFER),
                reservation.getEndTime().plus(TURNAROUND_BUFFER)).isEmpty()) {
            throw new ReservationConflictException("One or more tables are already reserved during the requested slot (including 15-minute turnaround)");
        }
        reservationTableRepository.deleteByReservationId(reservation.getId());
        reservationTableRepository.flush();
        reservationTableRepository.saveAll(tables.stream()
                .map(t -> ReservationTable.builder().reservation(reservation).table(t).build()).toList());
        return toResponse(reservation, tables);
    }

    @Transactional(readOnly = true)
    public ResultPaginationDTO getCombinations(Long reservationId, Pageable pageable) {
        if (reservationId != null && reservationId <= 0) {
            throw new RequestInvalidException("Reservation ID must be positive");
        }
        var page = reservationRepository.findTableCombinations(reservationId, pageable);
        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(page.getNumber() + 1);
        meta.setPageSize(page.getSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        meta.setHasNext(page.hasNext());
        meta.setHasPrevious(page.hasPrevious());
        var result = new ResultPaginationDTO();
        result.setMeta(meta);
        result.setResult(page.getContent().stream().map(r -> toResponse(r,
                reservationTableRepository.findByReservationId(r.getId()).stream()
                        .map(ReservationTable::getTable).toList())).toList());
        return result;
    }

    private MultiTableAllocationResponse toResponse(Reservation reservation, List<RestaurantTable> tables) {
        List<TableSummaryDTO> summaries = tables.stream().sorted(Comparator.comparing(RestaurantTable::getId))
                .map(t -> TableSummaryDTO.builder().id(t.getId()).tableNumber(t.getTableNumber())
                        .capacity(t.getCapacity()).build()).toList();
        Set<Long> areas = new HashSet<>();
        tables.forEach(t -> areas.add(t.getArea() == null ? null : t.getArea().getId()));
        return MultiTableAllocationResponse.builder().reservationId(reservation.getId())
                .reservationNo(reservation.getReservationNo()).partySize(reservation.getPartySize())
                .reservationStatus(reservation.getReservationStatus()).startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime()).areaId(areas.size() == 1 ? areas.iterator().next() : null)
                .totalCapacity(tables.stream().mapToInt(RestaurantTable::getCapacity).sum())
                .score(calculateCombinationScore(reservation, tables)).tables(summaries).build();
    }

    private double calculateCombinationScore(Reservation reservation, List<RestaurantTable> tables) {
        int guests = AllocationOptimizationService.requiredSeats(reservation.getPartySize());
        if (tables.isEmpty() || guests <= 0 || reservation.getStartTime() == null || reservation.getEndTime() == null
                || !reservation.getEndTime().isAfter(reservation.getStartTime())) {
            return 0.0;
        }
        List<Long> ids = tables.stream().map(RestaurantTable::getId).toList();
        List<Booking> neighbours = reservationTableRepository.findActiveBookingsInWindow(ids, BLOCKING_STATUSES,
                        reservation.getStartTime().minus(SCORING_LOOKAROUND),
                        reservation.getEndTime().plus(SCORING_LOOKAROUND)).stream()
                .filter(rt -> !Objects.equals(rt.getReservation().getId(), reservation.getId()))
                .map(rt -> new Booking(rt.getTable().getId(), rt.getReservation().getStartTime(),
                        rt.getReservation().getEndTime())).toList();
        List<TableCandidate> candidates = tables.stream().map(t -> new TableCandidate(t.getId(),
                t.getTableNumber() == null ? 0 : t.getTableNumber(), t.getCapacity(),
                t.getArea() == null ? null : t.getArea().getId())).toList();
        // Reservation currently stores neither exact guest count nor a preferred area.
        Query query = new Query(guests, reservation.getStartTime(), reservation.getEndTime(), null,
                1, true, (int) TURNAROUND_BUFFER.toMinutes(), tables.size());
        return TableRecommendationEngine.scoreAllocatedCombination(query, candidates, neighbours);
    }
}
