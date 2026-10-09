package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: Table Management [3]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableRequest {

    Long id;

    @NotNull(message = "Table number is required")
    @Positive(message = "Table number must be positive")
    Integer tableNumber;

    @Min(value = 1, message = "Capacity must be at least 1")
    int capacity;

    @NotNull(message = "Area ID is required")
    Long areaId;

    TableStatus statusCode;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description;
}
