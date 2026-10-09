package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemResponseDTO {

    Long id;
    Long orderId;
    Long menuItemId;
    String menuItemName;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal subtotal;
    Instant createdDate;
}
