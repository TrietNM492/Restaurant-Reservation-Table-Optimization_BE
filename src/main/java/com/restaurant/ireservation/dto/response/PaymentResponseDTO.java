package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
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
public class PaymentResponseDTO {

    Long id;
    Long orderId;
    String orderNo;
    BigDecimal totalFinalPrice;
    BigDecimal paidAmount;
    BigDecimal remainingAmount;
    int discountRate;
    PaymentStatus paymentStatus;
    String paymentUrl;
    String createdBy;
    Instant createdDate;
    Instant lastModifiedDate;
}
