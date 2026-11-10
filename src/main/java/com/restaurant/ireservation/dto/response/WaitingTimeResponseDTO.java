package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.WaitStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaitingTimeResponseDTO {

    Long reservationId;
    WaitStatus waitStatus;
    Integer estimatedWaitMinutes;
    Instant estimatedReadyTime;
    String message;
}
