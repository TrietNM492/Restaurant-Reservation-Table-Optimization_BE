package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Notification;
import com.restaurant.ireservation.domain.enumerations.NotificationDeliveryStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationType;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationReminderScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessingService notificationProcessingService;
    private final MailService mailService;


    @Scheduled(cron = "0 * * * * *")
    public void processReminder() {
        Instant now = Instant.now();
        List<Long> ids = notificationRepository.findDueNotificationIds(
                NotificationType.RESERVATION_REMINDER,
                NotificationDeliveryStatus.PENDING,
                now,
                PageRequest.of(0, 100)
        );
        for (Long id : ids) {
            try {
                if (!notificationProcessingService.claim(id)) {
                    continue;
                }
                Notification notification = notificationRepository
                        .findById(id).orElse(null);
                if (notification == null) {
                    continue;
                }

                mailService.sendReservationReminder(notification);
                notificationProcessingService.markSent(id);

            } catch (Exception exception) {
                log.error(
                        "Reminder email failed for notification ID {}",
                        id,
                        exception
                );

                try {
                    notificationProcessingService.markFail(id, exception);
                } catch (Exception updateException) {
                    log.error(
                            "Could not update notification status for ID {}",
                            id,
                            updateException
                    );
                }
            }
        }
    }
}
