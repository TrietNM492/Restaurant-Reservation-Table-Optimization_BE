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
public class PaymentHistoryResponseDTO {

    Long id;

    Long orderId;

    String orderNo;

    PaymentStatus paymentStatus;

    BigDecimal amount;

    int discountRate;

    Instant createdDate;

    BigDecimal orderTotalAmount;

    BigDecimal orderPaidAmount;

    BigDecimal orderRemainingAmount;
}
