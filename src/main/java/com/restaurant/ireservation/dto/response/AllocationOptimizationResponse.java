package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.TableSummary;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AllocationOptimizationResponse {

    Instant windowStart;
    Instant windowEnd;
    boolean applied;
    int reservationsInScope;
    int movableReservations;
    Metrics before;
    Metrics after;
    int movesCount;
    List<ReservationMove> moves;
    String message;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Metrics {
        int reservations;
        int requiredSeats;
        int assignedSeats;
        int wastedSeats;
        /** Seats actually needed / seats assigned, in percent. */
        double seatUtilizationPercent;
        /** Holes between two bookings of a table that are too short to be resold. */
        int idleGaps;
        int multiTableReservations;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ReservationMove {
        Long reservationId;
        String reservationNo;
        ReservationStatus reservationStatus;
        PartySize partySize;
        /** Seats assumed for the party (see PartySize mapping in AllocationOptimizationService). */
        int requiredSeats;
        Instant startTime;
        Instant endTime;
        List<TableSummary> fromTables;
        List<TableSummary> toTables;
        int wastedSeatsBefore;
        int wastedSeatsAfter;
        String reason;
    }
}
