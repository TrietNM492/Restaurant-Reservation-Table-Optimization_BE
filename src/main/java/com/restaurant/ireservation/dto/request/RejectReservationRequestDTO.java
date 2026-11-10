package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RejectReservationRequestDTO {

    @Size(max = 200, message = "Rejection reason must not exceed 200 characters")
    String reason;
}
