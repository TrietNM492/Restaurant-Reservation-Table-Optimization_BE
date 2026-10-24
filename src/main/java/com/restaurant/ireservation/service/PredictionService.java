package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.domain.enumerations.WaitStatus;
import com.restaurant.ireservation.dto.request.WaitingTimeRequestDTO;
import com.restaurant.ireservation.dto.response.WaitingTimeResponseDTO;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PredictionService {

    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
    static final Duration TURNAROUND_BUFFER = Duration.ofMinutes(15);

    final ReservationRepository reservationRepository;
    final ReservationTableRepository reservationTableRepository;

    @Setter
    Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public WaitingTimeResponseDTO predictWaitingTime(WaitingTimeRequestDTO request, String username) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + request.getReservationId()));

        if (reservation.getCreatedBy() == null || !reservation.getCreatedBy().equals(username)) {
            throw new AccessDeniedException("You are not allowed to view waiting time for this reservation");
        }

        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED
                || reservation.getReservationStatus() == ReservationStatus.COMPLETED
                || reservation.getReservationStatus() == ReservationStatus.NO_SHOW) {
            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.INACTIVE)
                    .estimatedWaitMinutes(null)
                    .estimatedReadyTime(null)
                    .message("Reservation is no longer active.")
                    .build();
        }

        if (reservation.getReservationType() != ReservationType.TABLE) {
            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.NOT_SUPPORTED)
                    .estimatedWaitMinutes(null)
                    .estimatedReadyTime(null)
                    .message("Waiting time prediction is only supported for table reservations.")
                    .build();
        }

        List<ReservationTable> reservationTables = reservationTableRepository.findByReservationId(reservation.getId());
        if (reservationTables.isEmpty()) {
            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.UNASSIGNED)
                    .estimatedWaitMinutes(null)
                    .estimatedReadyTime(null)
                    .message("No tables currently assigned to this reservation.")
                    .build();
        }

        Instant now = clock.instant();
        Instant tArrival = now.isAfter(reservation.getStartTime()) ? now : reservation.getStartTime();
        Instant targetEndTime = reservation.getEndTime();
        Instant tArrivalMinusBuffer = tArrival.minus(TURNAROUND_BUFFER);

        List<TableEvaluation> evaluations = new ArrayList<>();
        for (ReservationTable rt : reservationTables) {
            evaluations.add(evaluateTable(rt.getTable(), reservation, now, tArrival, targetEndTime, tArrivalMinusBuffer));
        }

        // Apply multi-table precedence: UNAVAILABLE > CONFLICT > INDETERMINATE > UNASSIGNED
        for (TableEvaluation eval : evaluations) {
            if (eval.status == WaitStatus.UNAVAILABLE) {
                return WaitingTimeResponseDTO.builder()
                        .reservationId(reservation.getId())
                        .waitStatus(WaitStatus.UNAVAILABLE)
                        .estimatedWaitMinutes(null)
                        .estimatedReadyTime(null)
                        .message("One or more assigned tables are currently locked or unavailable.")
                        .build();
            }
        }

        for (TableEvaluation eval : evaluations) {
            if (eval.status == WaitStatus.CONFLICT) {
                return WaitingTimeResponseDTO.builder()
                        .reservationId(reservation.getId())
                        .waitStatus(WaitStatus.CONFLICT)
                        .estimatedWaitMinutes(null)
                        .estimatedReadyTime(null)
                        .message("One or more assigned tables have a scheduling conflict with another reservation.")
                        .build();
            }
        }

        for (TableEvaluation eval : evaluations) {
            if (eval.status == WaitStatus.INDETERMINATE) {
                return WaitingTimeResponseDTO.builder()
                        .reservationId(reservation.getId())
                        .waitStatus(WaitStatus.INDETERMINATE)
                        .estimatedWaitMinutes(null)
                        .estimatedReadyTime(null)
                        .message(eval.message != null ? eval.message : "Table readiness cannot be determined.")
                        .build();
            }
        }

        // All tables are assessable: find bottleneck ready time
        Instant aggregateReady = now;
        TableEvaluation bottleneckEval = null;
        for (TableEvaluation eval : evaluations) {
            if (bottleneckEval == null || eval.readyTime.isAfter(aggregateReady)) {
                aggregateReady = eval.readyTime;
                bottleneckEval = eval;
            }
        }

        if (aggregateReady.isAfter(tArrival)) {
            long delaySeconds = Duration.between(tArrival, aggregateReady).getSeconds();
            int waitMinutes = (int) Math.ceil(delaySeconds / 60.0);

            String message;
            if (bottleneckEval != null && bottleneckEval.isPolicy2Estimate) {
                message = "Table is currently in use. Estimated ready at " + TIME_FORMATTER.format(aggregateReady)
                        + " based on the current party's scheduled departure (" + TIME_FORMATTER.format(bottleneckEval.policy2Departure)
                        + ") and 15 min turnaround. This is a schedule-based estimate, not confirmation that the table will be vacant.";
            } else if (bottleneckEval != null && bottleneckEval.isCleaningHeuristic) {
                message = "Table is currently being cleaned. Estimated ready at " + TIME_FORMATTER.format(aggregateReady)
                        + " based on standard turnaround.";
            } else {
                message = "Estimated ready at " + TIME_FORMATTER.format(aggregateReady)
                        + " based on prior reservation turnaround.";
            }

            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.WAITING)
                    .estimatedWaitMinutes(waitMinutes)
                    .estimatedReadyTime(aggregateReady)
                    .message(message)
                    .build();
        }

        // Ready or scheduled without delay
        if (now.isBefore(reservation.getStartTime())) {
            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.SCHEDULED)
                    .estimatedWaitMinutes(0)
                    .estimatedReadyTime(reservation.getStartTime())
                    .message("Reservation is scheduled on time. Ready time reflects your reserved slot; table preparation occurs prior to arrival.")
                    .build();
        } else {
            String message;
            if (reservation.getReservationStatus() == ReservationStatus.IN_PROGRESS) {
                message = "Reservation is in progress. No remaining wait to begin seating for this reservation.";
            } else {
                message = "No wait predicted based on current reservation records. Please see the host for seating.";
            }

            return WaitingTimeResponseDTO.builder()
                    .reservationId(reservation.getId())
                    .waitStatus(WaitStatus.READY)
                    .estimatedWaitMinutes(0)
                    .estimatedReadyTime(now)
                    .message(message)
                    .build();
        }
    }

    private TableEvaluation evaluateTable(RestaurantTable table, Reservation targetReservation,
                                         Instant now, Instant tArrival, Instant targetEndTime,
                                         Instant tArrivalMinusBuffer) {
        // Step 1: LOCKED check (Precedence 1)
        if (table.getStatusCode() == TableStatus.LOCKED) {
            return TableEvaluation.unavailable();
        }

        // Step 2: Active conflict check on [tArrival, targetEndTime] (Precedence 2)
        if (targetEndTime.isAfter(tArrival)) {
            List<ReservationStatus> activeStatuses = List.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.IN_PROGRESS
            );
            List<ReservationTable> conflicts = reservationTableRepository.findConflictingReservationsExcludingReservation(
                    List.of(table.getId()),
                    targetReservation.getId(),
                    activeStatuses,
                    tArrival,
                    targetEndTime
            );
            if (!conflicts.isEmpty()) {
                return TableEvaluation.conflict();
            }
        }

        Instant tableReady = now;
        boolean isPolicy2 = false;
        Instant policy2Departure = null;
        boolean isCleaning = false;

        // Step 3: Live occupancy check if IN_USE
        if (table.getStatusCode() == TableStatus.IN_USE) {
            List<ReservationTable> occupants = reservationTableRepository.findByTableIdAndReservationStatusExcludingReservation(
                    table.getId(),
                    targetReservation.getId(),
                    ReservationStatus.IN_PROGRESS
            );

            if (targetReservation.getReservationStatus() == ReservationStatus.IN_PROGRESS) {
                // Target is in progress. Another in_progress on same table is a conflict
                if (!occupants.isEmpty()) {
                    return TableEvaluation.conflict();
                }
                // Table is in use by target reservation itself -> no waiting for others
            } else {
                // Target not yet in progress
                if (occupants.isEmpty() || occupants.size() > 1) {
                    return TableEvaluation.indeterminate("Table status cannot be determined due to untracked occupancy.");
                }
                Reservation activeOccupant = occupants.get(0).getReservation();
                Instant occEnd = activeOccupant.getEndTime();

                if (!occEnd.isAfter(now)) {
                    // Overstay: past scheduled end time while still IN_USE
                    return TableEvaluation.indeterminate("Previous dining party has exceeded their scheduled departure time. Table readiness cannot be determined until vacated.");
                } else if (occEnd.isAfter(tArrival)) {
                    return TableEvaluation.conflict();
                } else {
                    // Policy 2: now < occEnd <= tArrival
                    Instant turnaround = occEnd.plus(TURNAROUND_BUFFER);
                    if (turnaround.isAfter(tableReady)) {
                        tableReady = turnaround;
                    }
                    isPolicy2 = true;
                    policy2Departure = occEnd;
                }
            }
        }

        // Step 4: Physical cleaning check
        if (table.getStatusCode() == TableStatus.CLEANING) {
            Instant cleaningReady = now.plus(TURNAROUND_BUFFER);
            if (cleaningReady.isAfter(tableReady)) {
                tableReady = cleaningReady;
            }
            isCleaning = true;
        }

        // Step 5: Preceding turnaround check
        List<ReservationStatus> turnaroundStatuses = List.of(
                ReservationStatus.COMPLETED,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );
        List<ReservationTable> preceding = reservationTableRepository.findPrecedingBookingsForTurnaround(
                table.getId(),
                targetReservation.getId(),
                turnaroundStatuses,
                tArrival,
                tArrivalMinusBuffer
        );

        for (ReservationTable rtPrev : preceding) {
            Reservation prevRes = rtPrev.getReservation();
            // Disallow overstayed IN_PROGRESS from contributing to turnaround
            if (prevRes.getReservationStatus() == ReservationStatus.IN_PROGRESS && !prevRes.getEndTime().isAfter(now)) {
                continue;
            }
            Instant prevTurnaround = prevRes.getEndTime().plus(TURNAROUND_BUFFER);
            if (prevTurnaround.isAfter(tableReady)) {
                tableReady = prevTurnaround;
            }
        }

        return TableEvaluation.assessable(tableReady, isPolicy2, policy2Departure, isCleaning);
    }

    private static class TableEvaluation {
        WaitStatus status;
        Instant readyTime;
        String message;
        boolean isPolicy2Estimate;
        Instant policy2Departure;
        boolean isCleaningHeuristic;

        static TableEvaluation unavailable() {
            TableEvaluation te = new TableEvaluation();
            te.status = WaitStatus.UNAVAILABLE;
            return te;
        }

        static TableEvaluation conflict() {
            TableEvaluation te = new TableEvaluation();
            te.status = WaitStatus.CONFLICT;
            return te;
        }

        static TableEvaluation indeterminate(String message) {
            TableEvaluation te = new TableEvaluation();
            te.status = WaitStatus.INDETERMINATE;
            te.message = message;
            return te;
        }

        static TableEvaluation assessable(Instant readyTime, boolean isPolicy2, Instant policy2Departure, boolean isCleaning) {
            TableEvaluation te = new TableEvaluation();
            te.readyTime = readyTime;
            te.isPolicy2Estimate = isPolicy2;
            te.policy2Departure = policy2Departure;
            te.isCleaningHeuristic = isCleaning;
            return te;
        }
    }
}
