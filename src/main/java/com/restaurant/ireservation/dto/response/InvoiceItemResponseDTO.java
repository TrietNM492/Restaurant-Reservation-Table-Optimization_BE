package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceItemResponseDTO {

    String itemName;

    int quantity;

    BigDecimal unitPrice;

    BigDecimal subtotal;
}
