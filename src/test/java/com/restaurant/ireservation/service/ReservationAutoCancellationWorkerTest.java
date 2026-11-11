package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Notification;
import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.NotificationDeliveryStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationType;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.CancellationResult;
import com.restaurant.ireservation.repository.NotificationRepository;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationAutoCancellationWorkerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private ReservationAutoCancellationWorker worker;

    private final Instant now = Instant.parse("2026-10-24T15:00:00Z");
    private final Instant cutoffTime = Instant.parse("2026-10-24T15:00:00Z");

    @Test
    @DisplayName("Worker TC-01: When atomic update returns 0, returns ConcurrentlyModified")
    void cancelAndRecordNotification_atomicUpdateZero_returnsConcurrentlyModified() {
        Long reservationId = 100L;
        when(reservationRepository.updateStatusIfCurrentStatusAndStartTimeBefore(
                eq(reservationId),
                eq(ReservationStatus.PENDING),
                eq(ReservationStatus.CANCELLED),
                eq(cutoffTime),
                eq(now),
                eq("SYSTEM")
        )).thenReturn(0);

        CancellationResult result = worker.cancelAndRecordNotification(reservationId, cutoffTime, now);

        assertInstanceOf(CancellationResult.ConcurrentlyModified.class, result);
        CancellationResult.ConcurrentlyModified cm = (CancellationResult.ConcurrentlyModified) result;
        assertEquals(reservationId, cm.reservationId());

        verify(reservationRepository, never()).findById(any());
        verify(userRepository, never()).findUserByUsername(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Worker TC-02: When customer user is not found, returns CancelledWithoutNotification")
    void cancelAndRecordNotification_userNotFound_returnsCancelledWithoutNotification() {
        Long reservationId = 200L;
        Reservation freshReservation = Reservation.builder()
                .reservationNo("RES-200")
                .reservationStatus(ReservationStatus.CANCELLED)
                .startTime(now.minusSeconds(600))
                .build();
        freshReservation.setId(reservationId);
        freshReservation.setCreatedBy("non_existent_customer");

        when(reservationRepository.updateStatusIfCurrentStatusAndStartTimeBefore(
                eq(reservationId),
                eq(ReservationStatus.PENDING),
                eq(ReservationStatus.CANCELLED),
                eq(cutoffTime),
                eq(now),
                eq("SYSTEM")
        )).thenReturn(1);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(freshReservation));
        when(userRepository.findUserByUsername("non_existent_customer")).thenReturn(Optional.empty());

        CancellationResult result = worker.cancelAndRecordNotification(reservationId, cutoffTime, now);

        assertInstanceOf(CancellationResult.CancelledWithoutNotification.class, result);
        CancellationResult.CancelledWithoutNotification cwn = (CancellationResult.CancelledWithoutNotification) result;
        assertEquals(reservationId, cwn.reservationId());
        assertEquals("RES-200", cwn.reservationNo());
        assertEquals("non_existent_customer", cwn.missingUsername());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Worker TC-03: When user is found, creates Notification and returns CancelledWithNotification")
    void cancelAndRecordNotification_success_persistsNotificationAndReturnsDetails() {
        Long reservationId = 300L;
        Instant startTime = now.minusSeconds(900);
        Reservation freshReservation = Reservation.builder()
                .reservationNo("RES-300")
                .reservationStatus(ReservationStatus.CANCELLED)
                .startTime(startTime)
                .build();
        freshReservation.setId(reservationId);
        freshReservation.setCreatedBy("alice");

        User customer = User.builder()
                .username("alice")
                .fullname("Alice Wonderland")
                .email("alice@example.com")
                .build();
        customer.setId(50L);

        Notification savedNotification = Notification.builder()
                .user(customer)
                .reservation(freshReservation)
                .title("Thông báo hủy đặt bàn")
                .content("Đặt bàn #RES-300 đã bị hủy tự động do quá giờ nhận bàn.")
                .type(NotificationType.IN_APP)
                .status(NotificationStatus.OK)
                .deliveryStatus(NotificationDeliveryStatus.PENDING)
                .scheduledAt(now)
                .build();
        savedNotification.setId(777L);

        when(reservationRepository.updateStatusIfCurrentStatusAndStartTimeBefore(
                eq(reservationId),
                eq(ReservationStatus.PENDING),
                eq(ReservationStatus.CANCELLED),
                eq(cutoffTime),
                eq(now),
                eq("SYSTEM")
        )).thenReturn(1);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(freshReservation));
        when(userRepository.findUserByUsername("alice")).thenReturn(Optional.of(customer));
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        CancellationResult result = worker.cancelAndRecordNotification(reservationId, cutoffTime, now);

        assertInstanceOf(CancellationResult.CancelledWithNotification.class, result);
        CancellationResult.CancelledWithNotification cwn = (CancellationResult.CancelledWithNotification) result;
        assertEquals(reservationId, cwn.reservationId());
        assertEquals("RES-300", cwn.reservationNo());
        assertEquals("alice@example.com", cwn.customerEmail());
        assertEquals("Alice Wonderland", cwn.customerName());
        assertEquals(startTime, cwn.startTime());
        assertEquals(777L, cwn.notificationId());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification captured = captor.getValue();
        assertEquals(customer, captured.getUser());
        assertEquals(freshReservation, captured.getReservation());
        assertEquals(NotificationType.IN_APP, captured.getType());
        assertEquals(NotificationStatus.OK, captured.getStatus());
        assertEquals(NotificationDeliveryStatus.PENDING, captured.getDeliveryStatus());
        assertEquals(0, captured.getRetryCount());
    }

    @Test
    @DisplayName("Worker TC-04: Confirmed reservation is rejected by conditional update (expected status PENDING)")
    void cancelAndRecordNotification_confirmedReservation_rejectedByAtomicUpdate() {
        Long reservationId = 400L;

        // When reservation is CONFIRMED, DB query where reservationStatus = PENDING matches 0 rows
        when(reservationRepository.updateStatusIfCurrentStatusAndStartTimeBefore(
                eq(reservationId),
                eq(ReservationStatus.PENDING),
                eq(ReservationStatus.CANCELLED),
                eq(cutoffTime),
                eq(now),
                eq("SYSTEM")
        )).thenReturn(0);

        CancellationResult result = worker.cancelAndRecordNotification(reservationId, cutoffTime, now);

        assertInstanceOf(CancellationResult.ConcurrentlyModified.class, result);
        assertEquals(reservationId, ((CancellationResult.ConcurrentlyModified) result).reservationId());
        verify(reservationRepository, never()).findById(any());
        verify(notificationRepository, never()).save(any());
    }
}
