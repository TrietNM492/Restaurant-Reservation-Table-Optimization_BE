package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenQueueResponseDTO {

    Long kitchenOrderId;
    Long orderId;
    String orderNo;
    OrderKitchenStatus status;
    Integer priority; // 1: Normal, 2: High, 3: VIP / Urgent
    String priorityLabel; // "NORMAL", "HIGH", "VIP_URGENT"
    boolean isVip;
    String tableOrRoomInfo; // "Table #5 (Indoor)" or "VIP Room: Orchid"
    String customerName;
    Instant orderTime;
    long waitingMinutes; // Real-time calculated wait time in queue
    Integer estimatedPrepTimeMinutes; // Module 12 prediction
    Instant estimatedReadyTime;
    Instant actualStartTime;
    Instant actualReadyTime;
    Instant actualServedTime;
    String kitchenStation;
    String note;
    List<KitchenOrderItemDTO> items;
}
