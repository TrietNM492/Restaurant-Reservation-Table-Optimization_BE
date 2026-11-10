package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.CancellationResult;
import com.restaurant.ireservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationAutoCancellationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationAutoCancellationWorker worker;

    @Mock
    private MailService mailService;

    @Mock
    private NotificationProcessingService notificationProcessingService;

    @InjectMocks
    private ReservationAutoCancellationService service;

    private final Instant now = Instant.parse("2026-10-24T15:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        service.setClock(fixedClock);
    }

    @Test
    @DisplayName("TC-01: Expired reservation cancelled, notification created, email sent and marked SENT")
    void processAutoCancellations_success_emailSentAndMarkedSent() throws Exception {
        Long reservationId = 1L;
        Long notificationId = 100L;
        Instant startTime = now.minusSeconds(600);

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithNotification result = new CancellationResult.CancelledWithNotification(
                reservationId,
                "RES-001",
                "customer@example.com",
                "John Doe",
                startTime,
                notificationId
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);

        service.processAutoCancellations(now);

        verify(mailService).sendReservationAutoCancellation(
                eq("customer@example.com"),
                eq("John Doe"),
                eq("RES-001"),
                eq(startTime)
        );
        verify(notificationProcessingService).markSent(notificationId);
        verify(notificationProcessingService, never()).markFail(any(), any());
    }

    @Test
    @DisplayName("TC-02: Missing customer user skips notification and email, cancellation remains committed")
    void processAutoCancellations_missingCustomerUser_skipsNotificationAndEmail() throws Exception {
        Long reservationId = 2L;

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithoutNotification result = new CancellationResult.CancelledWithoutNotification(
                reservationId,
                "RES-002",
                "missing_user"
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);

        service.processAutoCancellations(now);

        verify(mailService, never()).sendReservationAutoCancellation(any(), any(), any(), any());
        verify(notificationProcessingService, never()).markSent(any());
        verify(notificationProcessingService, never()).markFail(any(), any());
    }

    @Test
    @DisplayName("TC-03: Concurrent status or start time change aborts cancellation cleanly")
    void processAutoCancellations_concurrentlyModified_skipsCleanly() throws Exception {
        Long reservationId = 3L;

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.ConcurrentlyModified result = new CancellationResult.ConcurrentlyModified(reservationId);

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);

        service.processAutoCancellations(now);

        verify(mailService, never()).sendReservationAutoCancellation(any(), any(), any(), any());
        verify(notificationProcessingService, never()).markSent(any());
        verify(notificationProcessingService, never()).markFail(any(), any());
    }

    @Test
    @DisplayName("TC-04: Email failure marks notification FAILED without reverting cancellation")
    void processAutoCancellations_emailFails_marksNotificationFailed() throws Exception {
        Long reservationId = 4L;
        Long notificationId = 104L;
        Instant startTime = now.minusSeconds(600);

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithNotification result = new CancellationResult.CancelledWithNotification(
                reservationId,
                "RES-004",
                "customer@example.com",
                "Jane Doe",
                startTime,
                notificationId
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);
        doThrow(new MailSendException("SMTP timeout")).when(mailService)
                .sendReservationAutoCancellation(any(), any(), any(), any());

        service.processAutoCancellations(now);

        verify(notificationProcessingService, never()).markSent(any());
        verify(notificationProcessingService).markFail(eq(notificationId), any(MailSendException.class));
    }

    @Test
    @DisplayName("TC-05: Email succeeds but markSent fails with DB error, logs error and does not crash")
    void processAutoCancellations_emailSucceeds_markSentFails_handledGracefully() throws Exception {
        Long reservationId = 5L;
        Long notificationId = 105L;
        Instant startTime = now.minusSeconds(600);

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithNotification result = new CancellationResult.CancelledWithNotification(
                reservationId,
                "RES-005",
                "customer@example.com",
                "Jane Doe",
                startTime,
                notificationId
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);
        doThrow(new CannotAcquireLockException("DB lock error")).when(notificationProcessingService)
                .markSent(notificationId);

        service.processAutoCancellations(now);

        verify(mailService).sendReservationAutoCancellation(any(), any(), any(), any());
        verify(notificationProcessingService).markSent(notificationId);
    }

    @Test
    @DisplayName("TC-06: Email fails and markFail also fails with DB error, logs both and does not crash")
    void processAutoCancellations_emailFails_markFailFails_handledGracefully() throws Exception {
        Long reservationId = 6L;
        Long notificationId = 106L;
        Instant startTime = now.minusSeconds(600);

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithNotification result = new CancellationResult.CancelledWithNotification(
                reservationId,
                "RES-006",
                "customer@example.com",
                "Jane Doe",
                startTime,
                notificationId
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);
        doThrow(new MailSendException("SMTP connection dropped")).when(mailService)
                .sendReservationAutoCancellation(any(), any(), any(), any());
        doThrow(new CannotAcquireLockException("DB lock error")).when(notificationProcessingService)
                .markFail(eq(notificationId), any());

        service.processAutoCancellations(now);

        verify(mailService).sendReservationAutoCancellation(any(), any(), any(), any());
        verify(notificationProcessingService).markFail(eq(notificationId), any());
    }

    @Test
    @DisplayName("TC-07: Batch processing error on one reservation does not block subsequent reservations")
    void processAutoCancellations_batchResilience_failureOnOneDoesNotBlockOthers() throws Exception {
        Long id1 = 10L;
        Long id2 = 20L;
        Long id3 = 30L;

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(id1, id2, id3));

        CancellationResult.CancelledWithNotification result1 = new CancellationResult.CancelledWithNotification(
                id1, "RES-010", "c1@example.com", "User 1", now.minusSeconds(600), 110L
        );
        CancellationResult.CancelledWithNotification result3 = new CancellationResult.CancelledWithNotification(
                id3, "RES-030", "c3@example.com", "User 3", now.minusSeconds(600), 130L
        );

        when(worker.cancelAndRecordNotification(id1, now, now)).thenReturn(result1);
        when(worker.cancelAndRecordNotification(id2, now, now)).thenThrow(new RuntimeException("Unexpected DB crash on id 2"));
        when(worker.cancelAndRecordNotification(id3, now, now)).thenReturn(result3);

        service.processAutoCancellations(now);

        verify(worker).cancelAndRecordNotification(id1, now, now);
        verify(worker).cancelAndRecordNotification(id2, now, now);
        verify(worker).cancelAndRecordNotification(id3, now, now);

        verify(mailService).sendReservationAutoCancellation(eq("c1@example.com"), eq("User 1"), eq("RES-010"), any());
        verify(mailService).sendReservationAutoCancellation(eq("c3@example.com"), eq("User 3"), eq("RES-030"), any());
        verify(notificationProcessingService).markSent(110L);
        verify(notificationProcessingService).markSent(130L);
    }

    @Test
    @DisplayName("TC-08: Blank or null email skips email sending cleanly")
    void processAutoCancellations_blankEmail_skipsEmailSending() throws Exception {
        Long reservationId = 8L;
        Long notificationId = 108L;

        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(List.of(reservationId));

        CancellationResult.CancelledWithNotification result = new CancellationResult.CancelledWithNotification(
                reservationId,
                "RES-008",
                "",
                "No Email User",
                now.minusSeconds(600),
                notificationId
        );

        when(worker.cancelAndRecordNotification(reservationId, now, now)).thenReturn(result);

        service.processAutoCancellations(now);

        verify(mailService, never()).sendReservationAutoCancellation(any(), any(), any(), any());
        verify(notificationProcessingService, never()).markSent(any());
        verify(notificationProcessingService, never()).markFail(any(), any());
    }

    @Test
    @DisplayName("TC-09: Empty candidate list completes without any invocations")
    void processAutoCancellations_emptyCandidates_completesCleanly() {
        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        service.processAutoCancellations(now);

        verify(worker, never()).cancelAndRecordNotification(any(), any(), any());
    }

    @Test
    @DisplayName("TC-10: Confirmed reservations are strictly immune to auto-cancellation query")
    void processAutoCancellations_confirmedReservations_neverQueriedOrCancelled() {
        when(reservationRepository.findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        service.processAutoCancellations(now);

        verify(reservationRepository).findExpiredPendingReservationIds(eq(ReservationStatus.PENDING), eq(now), any(PageRequest.class));
        verify(reservationRepository, never()).findExpiredPendingReservationIds(eq(ReservationStatus.CONFIRMED), any(), any());
        verify(worker, never()).cancelAndRecordNotification(any(), any(), any());
    }
}
