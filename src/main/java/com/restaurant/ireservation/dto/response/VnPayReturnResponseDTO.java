package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VnPayReturnResponseDTO {

    boolean success;
    String responseCode;
    String transactionStatus;
    String message;
    Long paymentId;
    String txnRef;
    String transactionNo;
    String bankCode;
    String amount;
    String payDate;
    String orderInfo;
    String paymentType;
    boolean isFullyPaid;
    String orderNo;
    boolean signatureValid;
    String paymentStatus;
}
