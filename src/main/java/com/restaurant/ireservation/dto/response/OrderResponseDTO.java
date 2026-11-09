package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
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
public class OrderResponseDTO {

    Long id;
    String orderNo;
    Long tableId;
    Integer tableNumber;
    Long reservationId;
    String reservationNo;
    Instant reservationStartTime;
    String reservationType;
    boolean isAdvanceReservation;
    Long customerInformationId;
    OrderStatus orderStatus;
    OrderKitchenStatus kitchenStatus;
    Instant estimatedReadyTime;
    boolean isVipRoom;
    Long roomId;
    String roomName;
    BigDecimal roomPrice;
    BigDecimal totalAmount;
    Integer discountRate;
    BigDecimal discountAmount;
    BigDecimal targetTotalAmount;
    BigDecimal alreadyPaidAmount;
    BigDecimal remainingAmount;
    List<OrderItemResponseDTO> items;
    String createdBy;
    Instant createdDate;
    Instant lastModifiedDate;
}
