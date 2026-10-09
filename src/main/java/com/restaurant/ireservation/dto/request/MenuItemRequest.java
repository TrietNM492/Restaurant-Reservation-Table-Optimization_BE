package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemRequest {

    @NotNull(message = "Category id is required")
    Long categoryId;

    @NotBlank(message = "Item name is required")
    @Size(max = 150, message = "Item name must be at most 150 characters")
    String itemName;

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price must be greater than or equal to 0")
    Double price;

    @Size(max = 225, message = "Item picture must be at most 225 characters")
    String itemPicture;

    @PositiveOrZero(message = "Estimated prep time must be greater than or equal to 0")
    Integer estimatedPrepMinutes;

    /** AVAILABLE | OUT_OF_STOCK | ARCHIVED. Optional, default AVAILABLE on create, unchanged on update. */
    String status;

    /** Optional id of an existing FoodOrigin. */
    Long originId;
}
