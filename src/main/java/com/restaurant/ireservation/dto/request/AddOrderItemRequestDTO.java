package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddOrderItemRequestDTO {

    @NotNull(message = "orderId is required")
    Long orderId;

    @NotNull(message = "menuItemId is required")
    Long menuItemId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Quantity must be greater than or equal to 1")
    Integer quantity;

    @Size(max = 150, message = "Customer note must not exceed 150 characters")
    String customerNote;
}
