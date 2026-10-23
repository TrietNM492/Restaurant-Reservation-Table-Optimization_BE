package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiTableAllocationRequest {
    @NotNull(message = "Reservation ID is required")
    @Positive(message = "Reservation ID must be positive")
    private Long reservationId;

    @NotNull(message = "Table IDs are required")
    @Size(min = 2, message = "A combination requires at least two tables")
    private List<@NotNull @Positive Long> tableIds;
}
