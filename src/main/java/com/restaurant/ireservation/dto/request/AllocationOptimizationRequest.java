package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AllocationOptimizationRequest {

    /** Start of the optimisation window. Defaults to now. */
    Instant windowStart;

    /** End of the optimisation window. Defaults to windowStart + 24h (max 72h after windowStart). */
    Instant windowEnd;

    /** Reservations starting within this many minutes from now are never moved. Defaults to 60. */
    @Min(value = 0, message = "lockWindowMinutes cannot be negative")
    @Max(value = 720, message = "lockWindowMinutes cannot exceed 720")
    Integer lockWindowMinutes;

    /** Maximum number of reservations allowed to change table. Defaults to 10. */
    @Min(value = 1, message = "maxMoves must be at least 1")
    @Max(value = 100, message = "maxMoves cannot exceed 100")
    Integer maxMoves;

    /** Whether CONFIRMED reservations may be moved too (PENDING always may). Defaults to true. */
    Boolean includeConfirmed;
}
