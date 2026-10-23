package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

/**
 * Module: Kitchen Performance Analytics [Dashboard]
 * Theo dõi: Average Preparation Time | Kitchen Workload | Most Ordered Dishes
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenPerformanceAnalyticsDTO {

    // ─── Average Preparation Time ──────────────────────────────────────────────

    // Thời gian chế biến trung bình của tất cả đơn đã hoàn thành (phút)
    Double averagePrepTimeMinutes;

    // Thời gian chế biến nhanh nhất (phút)
    Double minPrepTimeMinutes;

    // Thời gian chế biến lâu nhất (phút)
    Double maxPrepTimeMinutes;

    // Tổng số đơn đã hoàn thành được tính vào avg
    long completedOrdersCount;

    // ─── Kitchen Workload ──────────────────────────────────────────────────────

    // Tổng đơn đang hoạt động trong bếp (không tính SERVED / CANCELLED)
    long totalActiveOrders;

    // Số đơn đang chờ (QUEUED / PENDING / ON_HOLD)
    long pendingOrders;

    // Số đơn đang chế biến (COOKING / PREPARING)
    long preparingOrders;

    // Số đơn sẵn sàng (READY / DONE)
    long readyOrders;

    // % tải bếp hiện tại (preparingOrders + readyOrders / MAX_CAPACITY * 100)
    double workloadPercent;

    // Phân bổ đơn theo trạng thái {STATUS: count}
    Map<String, Long> orderCountByStatus;

    // ─── Most Ordered Dishes ──────────────────────────────────────────────────

    // Top món ăn được gọi nhiều nhất
    List<DishRankDTO> mostOrderedDishes;

    // ─── Inner DTO: xếp hạng từng món ────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class DishRankDTO {

        // Thứ hạng (1 = gọi nhiều nhất)
        int rank;

        Long menuItemId;
        String dishName;
        String categoryName;

        // Tổng số lượng được gọi
        long totalQuantityOrdered;

        // Tổng doanh thu từ món này
        double totalRevenue;

        // Số đơn hàng chứa món này
        long orderCount;
    }
}
