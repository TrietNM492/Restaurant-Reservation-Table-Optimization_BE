package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenOrderItemDTO {

    Long orderItemId;
    Long menuItemId;
    String itemName;
    int quantity;
    Integer estimatedPrepTimeMinutes;
    String categoryName;
    String unitPrice;
    double subtotal;
}
