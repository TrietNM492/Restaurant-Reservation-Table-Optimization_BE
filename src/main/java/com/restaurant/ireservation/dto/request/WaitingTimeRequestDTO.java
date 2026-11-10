package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaitingTimeRequestDTO {

    @NotNull(message = "Reservation ID is required")
    Long reservationId;
}
