package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceResponseDTO {

    String restaurantName;

    String restaurantAddress;

    String restaurantPhone;

    String invoiceNo;

    Long paymentId;

    PaymentStatus paymentStatus;

    Instant paymentDate;

    BigDecimal paymentAmount;

    int discountRate;

    Long orderId;

    String orderNo;

    Instant orderDate;

    String customerName;

    String customerPhone;

    String customerEmail;

    List<InvoiceItemResponseDTO> items;

    BigDecimal orderTotalAmount;

    BigDecimal orderPaidAmount;

    BigDecimal orderRemainingAmount;

    boolean isFullyPaid;

    String remainingMessage;
}
