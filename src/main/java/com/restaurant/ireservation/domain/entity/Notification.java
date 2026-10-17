package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.NotificationDeliveryStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Optional: not every notification relates to a reservation.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    // For in-app read/unread state, if applicable.
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    // For email delivery lifecycle.
    @Enumerated(EnumType.STRING)
    private NotificationDeliveryStatus deliveryStatus;

    private Instant scheduledAt;

    private Instant sentAt;

    private Integer retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
