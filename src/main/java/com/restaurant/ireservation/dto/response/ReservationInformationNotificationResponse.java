package com.restaurant.ireservation.dto.response;



import com.restaurant.ireservation.domain.enumerations.NotificationStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReservationInformationNotificationResponse {

    Long id;
    String title;
    String content;
    NotificationType type;
    NotificationStatus status;
    Instant createdAt;



}
