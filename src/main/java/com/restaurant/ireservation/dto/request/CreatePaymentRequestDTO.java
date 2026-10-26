package com.restaurant.ireservation.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePaymentRequestDTO {

    @NotNull(message = "orderId is required")
    Long orderId;

    @JsonAlias({"requestedAmount", "depositAmount"})
    BigDecimal amount;
}
