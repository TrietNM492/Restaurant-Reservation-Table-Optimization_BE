package com.restaurant.ireservation.dto.response;

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
    Long customerInformationId;
    OrderStatus orderStatus;
    boolean isVipRoom;
    BigDecimal totalAmount;
    List<OrderItemResponseDTO> items;
    String createdBy;
    Instant createdDate;
    Instant lastModifiedDate;
}
