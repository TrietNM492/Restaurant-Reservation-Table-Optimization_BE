package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateOrderItemRequestDTO {

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "Quantity must be greater than or equal to 0")
    Integer quantity;
}
