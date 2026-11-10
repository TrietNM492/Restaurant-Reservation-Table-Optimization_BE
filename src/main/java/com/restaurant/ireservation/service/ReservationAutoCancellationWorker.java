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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReservationAutoCancellationWorker {

    ReservationRepository reservationRepository;
    UserRepository userRepository;
    NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationResult cancelAndRecordNotification(Long reservationId, Instant cutoffTime, Instant now) {
        int updated = reservationRepository.updateStatusIfCurrentStatusAndStartTimeBefore(
                reservationId,
                ReservationStatus.PENDING,
                ReservationStatus.CANCELLED,
                cutoffTime,
                now,
                "SYSTEM"
        );

        if (updated == 0) {
            return new CancellationResult.ConcurrentlyModified(reservationId);
        }

        // Reload fresh managed entity after bulk update cleared persistence context
        Reservation freshReservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found after auto-cancellation update: " + reservationId));

        String username = freshReservation.getCreatedBy();
        Optional<User> userOpt = (username != null && !username.isBlank())
                ? userRepository.findUserByUsername(username)
                : Optional.empty();

        if (userOpt.isEmpty()) {
            return new CancellationResult.CancelledWithoutNotification(
                    freshReservation.getId(),
                    freshReservation.getReservationNo(),
                    username != null ? username : "UNKNOWN"
            );
        }

        User customer = userOpt.get();

        Notification notification = Notification.builder()
                .user(customer)
                .reservation(freshReservation)
                .title("Thông báo hủy đặt bàn")
                .content("Đặt bàn #" + freshReservation.getReservationNo() + " đã bị hủy tự động do quá giờ nhận bàn.")
                .type(NotificationType.IN_APP)
                .status(NotificationStatus.OK)
                .deliveryStatus(NotificationDeliveryStatus.PENDING)
                .scheduledAt(now)
                .retryCount(0)
                .build();

        Notification savedNotification = notificationRepository.save(notification);

        return new CancellationResult.CancelledWithNotification(
                freshReservation.getId(),
                freshReservation.getReservationNo(),
                customer.getEmail(),
                customer.getFullname(),
                freshReservation.getStartTime(),
                savedNotification.getId()
        );
    }
}
