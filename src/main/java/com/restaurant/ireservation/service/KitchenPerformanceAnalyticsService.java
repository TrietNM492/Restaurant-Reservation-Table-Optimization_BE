package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.response.KitchenPerformanceAnalyticsDTO;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module: Kitchen Performance Analytics [Dashboard]
 * Theo dõi: Average Preparation Time | Kitchen Workload | Most Ordered Dishes
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KitchenPerformanceAnalyticsService {

    KitchenOrderRepository kitchenOrderRepository;
    OrderItemRepository orderItemRepository;

    // Công suất tối đa bếp xử lý đồng thời (đơn vị: đơn)
    static final int MAX_KITCHEN_CAPACITY = 20;

    // Số lượng top món trả về mặc định
    static final int TOP_DISHES_LIMIT = 10;

    // Trạng thái đơn hoàn thành (có timestamp đủ để tính avg prep time)
    static final List<OrderKitchenStatus> COMPLETED_STATUSES = List.of(
            OrderKitchenStatus.READY,
            OrderKitchenStatus.DONE,
            OrderKitchenStatus.SERVED,
            OrderKitchenStatus.SERVCED
    );

    // Trạng thái chờ
    static final List<OrderKitchenStatus> PENDING_STATUSES = List.of(
            OrderKitchenStatus.QUEUED,
            OrderKitchenStatus.PENDING,
            OrderKitchenStatus.ON_HOLD
    );

    // Trạng thái đang chế biến
    static final List<OrderKitchenStatus> PREPARING_STATUSES = List.of(
            OrderKitchenStatus.COOKING,
            OrderKitchenStatus.PREPARING
    );

    // Trạng thái sẵn sàng
    static final List<OrderKitchenStatus> READY_STATUSES = List.of(
            OrderKitchenStatus.READY,
            OrderKitchenStatus.DONE
    );

    /**
     * Kitchen Performance Analytics Dashboard:
     * Average Preparation Time | Kitchen Workload | Most Ordered Dishes
     */
    @Transactional(readOnly = true)
    public KitchenPerformanceAnalyticsDTO getKitchenPerformanceAnalytics() {

        // ─── 1. Average Preparation Time ──────────────────────────────────────
        List<KitchenOrder> completedOrders =
                kitchenOrderRepository.findCompletedWithTimestamps(COMPLETED_STATUSES);

        Double avgPrepTime = null;
        Double minPrepTime = null;
        Double maxPrepTime = null;
        long completedCount = completedOrders.size();

        if (!completedOrders.isEmpty()) {
            List<Double> prepTimesMinutes = new ArrayList<>();
            for (KitchenOrder ko : completedOrders) {
                long seconds = Duration.between(ko.getActualStartTime(), ko.getActualReadyTime()).toSeconds();
                prepTimesMinutes.add(seconds / 60.0);
            }

            avgPrepTime = prepTimesMinutes.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            minPrepTime = prepTimesMinutes.stream()
                    .mapToDouble(Double::doubleValue)
                    .min()
                    .orElse(0.0);

            maxPrepTime = prepTimesMinutes.stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0.0);
        }

        // ─── 2. Kitchen Workload ───────────────────────────────────────────────
        long pendingCount   = kitchenOrderRepository.countByStatusIn(PENDING_STATUSES);
        long preparingCount = kitchenOrderRepository.countByStatusIn(PREPARING_STATUSES);
        long readyCount     = kitchenOrderRepository.countByStatusIn(READY_STATUSES);
        long totalActive    = pendingCount + preparingCount + readyCount;

        // Workload % tính theo đơn đang xử lý thực tế (preparing + ready) / capacity
        double workloadPercent = Math.min(100.0,
                (preparingCount + readyCount) * 100.0 / MAX_KITCHEN_CAPACITY);

        // Phân bổ theo trạng thái
        List<Object[]> grouped = kitchenOrderRepository.countGroupedByStatus();
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (Object[] row : grouped) {
            String statusName = row[0] != null ? row[0].toString() : "UNKNOWN";
            Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            countByStatus.put(statusName, count);
        }

        // ─── 3. Most Ordered Dishes ────────────────────────────────────────────
        List<Object[]> dishRows = orderItemRepository.findMostOrderedDishes(TOP_DISHES_LIMIT);

        // Giới hạn kết quả phía Java (JPQL không hỗ trợ LIMIT trực tiếp qua @Param)
        int limit = Math.min(TOP_DISHES_LIMIT, dishRows.size());
        List<KitchenPerformanceAnalyticsDTO.DishRankDTO> mostOrderedDishes = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            Object[] row = dishRows.get(i);

            Long menuItemId     = row[0] instanceof Number ? ((Number) row[0]).longValue() : null;
            String dishName     = row[1] != null ? row[1].toString() : "Unknown Dish";
            String categoryName = row[2] != null ? row[2].toString() : "Uncategorized";
            long totalQty       = row[3] instanceof Number ? ((Number) row[3]).longValue() : 0L;
            double totalRev     = row[4] instanceof Number ? ((Number) row[4]).doubleValue() : 0.0;
            long orderCount     = row[5] instanceof Number ? ((Number) row[5]).longValue() : 0L;

            mostOrderedDishes.add(KitchenPerformanceAnalyticsDTO.DishRankDTO.builder()
                    .rank(i + 1)
                    .menuItemId(menuItemId)
                    .dishName(dishName)
                    .categoryName(categoryName)
                    .totalQuantityOrdered(totalQty)
                    .totalRevenue(totalRev)
                    .orderCount(orderCount)
                    .build());
        }

        log.info("[Kitchen Performance Analytics] avgPrepTime={}min, workload={}%, activeOrders={}, topDishes={}",
                avgPrepTime != null ? String.format("%.1f", avgPrepTime) : "N/A",
                String.format("%.1f", workloadPercent),
                totalActive,
                mostOrderedDishes.size());

        return KitchenPerformanceAnalyticsDTO.builder()
                // Average Preparation Time
                .averagePrepTimeMinutes(avgPrepTime)
                .minPrepTimeMinutes(minPrepTime)
                .maxPrepTimeMinutes(maxPrepTime)
                .completedOrdersCount(completedCount)
                // Kitchen Workload
                .totalActiveOrders(totalActive)
                .pendingOrders(pendingCount)
                .preparingOrders(preparingCount)
                .readyOrders(readyCount)
                .workloadPercent(workloadPercent)
                .orderCountByStatus(countByStatus)
                // Most Ordered Dishes
                .mostOrderedDishes(mostOrderedDishes)
                .build();
    }
}
