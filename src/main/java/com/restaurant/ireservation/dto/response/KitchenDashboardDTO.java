package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

/**
 * Module: Kitchen Load Monitoring [2] — FR-030
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenDashboardDTO {

    // FR-030: Pending Orders — tổng đơn đang chờ chế biến (QUEUED + PENDING + ON_HOLD)
    long pendingOrdersCount;

    // Đang chế biến (COOKING / PREPARING)
    long preparingOrdersCount;

    // Sẵn sàng phục vụ (READY / DONE)
    long readyOrdersCount;

    // Tổng đơn đang hoạt động trong bếp
    long totalActiveOrdersCount;

    // FR-030: Average Preparation Time — tính từ actualStartTime đến actualReadyTime (phút)
    Double averagePrepTimeMinutes;

    // FR-030: Kitchen Utilization — % tải bếp hiện tại (activeOrders / maxCapacity * 100)
    double kitchenUtilizationPercent;

    // Phân bổ đơn theo từng trạng thái
    Map<String, Long> orderCountByStatus;
}
