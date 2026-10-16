package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Notification;
import com.restaurant.ireservation.domain.enumerations.NotificationDeliveryStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationStatus;
import com.restaurant.ireservation.dto.response.ReservationInformationNotificationResponse;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;


    @Transactional
    public boolean claim(Long notificationId) {
        int updated = notificationRepository.claimNotification(notificationId, NotificationDeliveryStatus.PENDING,
                NotificationDeliveryStatus.PROCESSING);
        return updated == 1;
    }

    @Transactional
    public void markSent(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification Not Found!"));
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification.setSentAt(Instant.now());
        notification.setLastError(null);
    }

    public void markFail(Long notificationId, Exception e) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource Not Found!"));

        notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
        notification.setRetryCount(notification.getRetryCount() == null
                ? 1 : notification.getRetryCount() + 1);
        String message = e.getMessage();
        notification.setLastError(
                message == null ? "Email delivery failed" : message.substring(0, Math.min(message.length(), 200))
        );
    }

    @Transactional(readOnly = true)
    public Page<ReservationInformationNotificationResponse> getHistory(Long userId, Pageable pageable) {
        return notificationRepository.findByUser_IdOrderByCreatedDateDesc(userId, pageable)
                .map(notification -> new ReservationInformationNotificationResponse(
                        notification.getId(),
                        notification.getTitle(),
                        notification.getContent(),
                        notification.getType(),
                        notification.getStatus(),
                        notification.getCreatedDate()

                ));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUser_IdAndStatus(userId, NotificationStatus.OK);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification Not Found!"));
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
        }

    }

}
