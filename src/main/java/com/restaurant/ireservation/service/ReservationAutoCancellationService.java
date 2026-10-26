package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.CancellationResult;
import com.restaurant.ireservation.repository.ReservationRepository;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ReservationAutoCancellationService {

    final ReservationRepository reservationRepository;
    final ReservationAutoCancellationWorker worker;
    final MailService mailService;
    final NotificationProcessingService notificationProcessingService;

    @Value("${app.reservation.auto-cancellation.batch-size:100}")
    int batchSize = 100;

    @Getter
    @Setter
    Clock clock = Clock.systemUTC();

    public void processAutoCancellations(Instant cutoffTime) {
        Instant effectiveCutoff = (cutoffTime != null) ? cutoffTime : Instant.now(clock);

        List<Long> candidateIds = reservationRepository.findExpiredPendingReservationIds(
                ReservationStatus.PENDING,
                effectiveCutoff,
                PageRequest.of(0, Math.max(1, batchSize))
        );

        for (Long reservationId : candidateIds) {
            try {
                CancellationResult result = worker.cancelAndRecordNotification(reservationId, effectiveCutoff, effectiveCutoff);
                switch (result) {
                    case CancellationResult.ConcurrentlyModified cm -> {
                        log.debug("Reservation id {} was concurrently updated or rescheduled. Skipping cancellation.", cm.reservationId());
                    }
                    case CancellationResult.CancelledWithoutNotification cwn -> {
                        log.warn("Customer user '{}' for auto-cancelled reservation id {} (reservationNo: {}) not found in database. Cancellation committed; notification skipped.",
                                cwn.missingUsername(), cwn.reservationId(), cwn.reservationNo());
                    }
                    case CancellationResult.CancelledWithNotification cwn -> {
                        log.info("Reservation id {} (reservationNo: {}) auto-cancelled. Proceeding to email dispatch.",
                                cwn.reservationId(), cwn.reservationNo());
                        deliverEmail(cwn);
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected error in auto-cancellation pipeline for reservation id {}", reservationId, e);
            }
        }
    }

    private void deliverEmail(CancellationResult.CancelledWithNotification details) {
        if (details.customerEmail() == null || details.customerEmail().isBlank()) {
            log.warn("Customer email is blank for reservation id {}. Skipping email delivery.", details.reservationId());
            return;
        }

        try {
            mailService.sendReservationAutoCancellation(
                    details.customerEmail(),
                    details.customerName(),
                    details.reservationNo(),
                    details.startTime()
            );

            // Phase 2a: Email succeeded -> update notification deliveryStatus to SENT
            try {
                notificationProcessingService.markSent(details.notificationId());
            } catch (Exception updateSentEx) {
                log.error("Email sent successfully for reservation id {}, but failed to update notification id {} delivery status to SENT: {}",
                        details.reservationId(), details.notificationId(), updateSentEx.getMessage(), updateSentEx);
            }
        } catch (Exception mailEx) {
            // Phase 2b: Email failed -> update notification deliveryStatus to FAILED
            log.error("Failed to send cancellation email for reservation id {}", details.reservationId(), mailEx);
            try {
                notificationProcessingService.markFail(details.notificationId(), mailEx);
            } catch (Exception updateFailEx) {
                log.error("CRITICAL: Failed to send cancellation email for reservation id {} (Reason: {}). Subsequently failed to update notification id {} to FAILED (Reason: {})",
                        details.reservationId(), mailEx.getMessage(), details.notificationId(), updateFailEx.getMessage(), updateFailEx);
            }
        }
    }
}
