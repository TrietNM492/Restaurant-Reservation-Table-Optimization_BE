package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.request.OrderItemPredictionRequestDTO;
import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimeBreakdownDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Module 11: Food Preparation Time Estimation (FR-044) & Logging (FR-045)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PreparationTimeService {

    // Hardcoded parallel kitchen cooking stations/slots
    public static final int DEFAULT_PARALLEL_KITCHEN_SLOTS = 3;

    // Active statuses representing kitchen load
    static final List<OrderKitchenStatus> ACTIVE_STATUSES = List.of(
            OrderKitchenStatus.QUEUED,
            OrderKitchenStatus.PREPARING,
            OrderKitchenStatus.COOKING,
            OrderKitchenStatus.PENDING,
            OrderKitchenStatus.ON_HOLD
    );

    static final List<OrderKitchenStatus> COMPLETED_STATUSES = List.of(
            OrderKitchenStatus.READY,
            OrderKitchenStatus.DONE,
            OrderKitchenStatus.SERVED,
            OrderKitchenStatus.SERVCED
    );

    KitchenOrderRepository kitchenOrderRepository;
    MenuItemRepository menuItemRepository;
    OrderRepository orderRepository;
    OrderItemRepository orderItemRepository;

    /**
     * FR-044: Predict food preparation time
     * FR-045: Log prediction to KitchenOrder if orderId is present
     */
    @Transactional
    public PreparationTimePredictionResponseDTO predictPreparationTime(PreparationTimePredictionRequestDTO request) {
        if (request == null) {
            throw new RequestInvalidException("Request cannot be null");
        }

        Long orderId = request.getOrderId();
        List<OrderItemPredictionRequestDTO> requestItems = request.getItems();

        if (orderId == null && (requestItems == null || requestItems.isEmpty())) {
            throw new RequestInvalidException("Either orderId or a non-empty items list must be provided.");
        }

        // 1. Resolve items and VIP status
        List<DishItemDetail> dishItems = new ArrayList<>();
        boolean isVip = false;

        if (orderId != null) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

            isVip = Boolean.TRUE.equals(request.getIsVip()) || order.isVipRoom();

            List<OrderItem> existingOrderItems = orderItemRepository.findAllByOrderId(orderId);
            if (existingOrderItems.isEmpty()) {
                throw new RequestInvalidException("Order has no items to calculate preparation time.");
            }

            for (OrderItem oi : existingOrderItems) {
                MenuItem mi = oi.getMenuItem();
                if (mi != null) {
                    int prepTime = resolveMenuItemPrepMinutes(mi);
                    dishItems.add(new DishItemDetail(mi.getItemName(), oi.getQuantity(), prepTime));
                }
            }
        } else {
            isVip = Boolean.TRUE.equals(request.getIsVip());

            for (OrderItemPredictionRequestDTO itemDto : requestItems) {
                if (itemDto.getMenuItemId() == null) {
                    throw new RequestInvalidException("menuItemId in items list cannot be null");
                }
                MenuItem mi = menuItemRepository.findById(itemDto.getMenuItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with ID: " + itemDto.getMenuItemId()));

                int prepTime = resolveMenuItemPrepMinutes(mi);
                int qty = (itemDto.getQuantity() != null && itemDto.getQuantity() > 0) ? itemDto.getQuantity() : 1;
                dishItems.add(new DishItemDetail(mi.getItemName(), qty, prepTime));
            }
        }

        if (dishItems.isEmpty()) {
            throw new RequestInvalidException("No valid dish items found to calculate preparation time.");
        }

        // 2. Base Cooking Time (T_dish)
        DishCookingCalculation cookingCalc = calculateBaseCookingMinutes(dishItems);
        int baseCookingMinutes = cookingCalc.baseCookingMinutes();
        String longestDishName = cookingCalc.longestDishName();
        int longestDishMinutes = cookingCalc.longestDishMinutes();

        // 3. Queue Waiting Time (T_queue) with 3 parallel kitchen slots
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findActiveKitchenQueue(ACTIVE_STATUSES, null, null);
        int activeCount = (activeOrders != null) ? activeOrders.size() : 0;
        int queueWaitingMinutes = calculateQueueWaitingMinutes(activeOrders, activeCount, isVip);

        // 4. Historical Calibration / Bias (T_bias)
        int historicalBiasMinutes = calculateHistoricalBiasMinutes();

        // 5. Total Estimated Time
        int totalEstimatedMinutes = Math.max(1, baseCookingMinutes + queueWaitingMinutes + historicalBiasMinutes);
        Instant now = Instant.now();
        Instant estimatedReadyTime = now.plus(Duration.ofMinutes(totalEstimatedMinutes));

        // 6. FR-045: Log prediction into KitchenOrder if orderId is provided
        if (orderId != null) {
            logPredictionToKitchenOrder(orderId, totalEstimatedMinutes, estimatedReadyTime, isVip);
        }

        PreparationTimeBreakdownDTO breakdown = PreparationTimeBreakdownDTO.builder()
                .baseCookingMinutes(baseCookingMinutes)
                .queueWaitingMinutes(queueWaitingMinutes)
                .historicalBiasMinutes(historicalBiasMinutes)
                .activeKitchenOrdersCount(activeCount)
                .parallelKitchenSlots(DEFAULT_PARALLEL_KITCHEN_SLOTS)
                .isVipPriority(isVip)
                .longestDishName(longestDishName)
                .longestDishMinutes(longestDishMinutes)
                .build();

        log.info("[PrepTime Estimation] orderId={}, totalEstimated={}m (base={}m, queue={}m, bias={}m, vip={})",
                orderId, totalEstimatedMinutes, baseCookingMinutes, queueWaitingMinutes, historicalBiasMinutes, isVip);

        return PreparationTimePredictionResponseDTO.builder()
                .orderId(orderId)
                .estimatedMinutes(totalEstimatedMinutes)
                .estimatedReadyTime(estimatedReadyTime)
                .breakdown(breakdown)
                .calculationTimestamp(now)
                .build();
    }

    /**
     * Reusable helper to estimate prep time in minutes without persisting a KitchenOrder.
     * Incorporates base cooking duration, 3 parallel stations queue wait, VIP jump, and historical bias calibration.
     */
    @Transactional(readOnly = true)
    public int estimatePrepMinutes(List<OrderItem> items, boolean isVip) {
        if (items == null || items.isEmpty()) {
            return 15;
        }

        List<DishItemDetail> dishItems = new ArrayList<>();
        for (OrderItem oi : items) {
            MenuItem mi = oi.getMenuItem();
            if (mi != null) {
                int prepTime = resolveMenuItemPrepMinutes(mi);
                int qty = Math.max(1, oi.getQuantity());
                dishItems.add(new DishItemDetail(mi.getItemName(), qty, prepTime));
            }
        }

        if (dishItems.isEmpty()) {
            return 15;
        }

        DishCookingCalculation cookingCalc = calculateBaseCookingMinutes(dishItems);
        int baseCookingMinutes = cookingCalc.baseCookingMinutes();

        List<KitchenOrder> activeOrders = kitchenOrderRepository.findActiveKitchenQueue(ACTIVE_STATUSES, null, null);
        int activeCount = (activeOrders != null) ? activeOrders.size() : 0;
        int queueWaitingMinutes = calculateQueueWaitingMinutes(activeOrders, activeCount, isVip);
        int historicalBiasMinutes = calculateHistoricalBiasMinutes();

        return Math.max(1, baseCookingMinutes + queueWaitingMinutes + historicalBiasMinutes);
    }

    public int resolveMenuItemPrepMinutes(MenuItem mi) {
        if (mi != null && mi.getEstimatedPrepTime() != null) {
            long minutes = mi.getEstimatedPrepTime().toMinutes();
            if (minutes > 0) {
                return (int) minutes;
            }
        }
        return 15; // default fallback 15 mins
    }

    private DishCookingCalculation calculateBaseCookingMinutes(List<DishItemDetail> dishItems) {
        double maxEffectiveTime = 0.0;
        String longestDishName = "Standard Dish";
        int longestDishBaseMinutes = 15;

        for (DishItemDetail item : dishItems) {
            // Extra portions beyond 1 scale at +10% duration
            double effectiveDishTime = item.baseMinutes() + (item.quantity() - 1) * (item.baseMinutes() * 0.10);
            if (effectiveDishTime > maxEffectiveTime) {
                maxEffectiveTime = effectiveDishTime;
                longestDishName = item.dishName();
                longestDishBaseMinutes = item.baseMinutes();
            }
        }

        // Parallel kitchen staging overhead: +1.5 mins per additional distinct dish in order
        int additionalDistinctDishes = Math.max(0, dishItems.size() - 1);
        double totalBaseTime = maxEffectiveTime + (additionalDistinctDishes * 1.5);

        int baseCookingMinutes = Math.max(1, (int) Math.round(totalBaseTime));
        return new DishCookingCalculation(baseCookingMinutes, longestDishName, longestDishBaseMinutes);
    }

    private int calculateQueueWaitingMinutes(List<KitchenOrder> activeOrders, int activeCount, boolean isVip) {
        if (activeCount < DEFAULT_PARALLEL_KITCHEN_SLOTS) {
            return 0; // immediate station available
        }

        Instant now = Instant.now();

        if (isVip) {
            // VIP priority jumps ahead of queued orders; only waits for the earliest currently cooking order to finish
            long minRemainingSeconds = Long.MAX_VALUE;
            if (activeOrders != null) {
                for (KitchenOrder ko : activeOrders) {
                    if (ko.getEstimatedReadyTime() != null) {
                        long rem = Duration.between(now, ko.getEstimatedReadyTime()).toSeconds();
                        if (rem > 0 && rem < minRemainingSeconds) {
                            minRemainingSeconds = rem;
                        }
                    }
                }
            }
            if (minRemainingSeconds == Long.MAX_VALUE) {
                return 3; // minimal default wait for slot transition
            }
            return Math.max(1, (int) Math.ceil(minRemainingSeconds / 60.0));
        }

        // Normal order: 3 parallel stations distribute load
        int queuedBeyondCapacity = activeCount - DEFAULT_PARALLEL_KITCHEN_SLOTS;
        // Average prep cycle across stations is approx 15 minutes
        int avgCycleMinutes = 15;
        // Wait time for 1st slot to open (~5 mins) + throughput of remaining queue across 3 slots
        int waitMinutes = 5 + (queuedBeyondCapacity * avgCycleMinutes) / DEFAULT_PARALLEL_KITCHEN_SLOTS;
        return Math.max(0, waitMinutes);
    }

    private int calculateHistoricalBiasMinutes() {
        try {
            List<KitchenOrder> completed = kitchenOrderRepository.findCompletedWithTimestamps(COMPLETED_STATUSES);
            if (completed == null || completed.isEmpty()) {
                return 0;
            }

            // Take up to 20 most recent completed orders
            List<KitchenOrder> recentCompleted = completed.stream()
                    .filter(ko -> ko.getActualStartTime() != null && ko.getActualReadyTime() != null
                            && ko.getEstimatedPrepTimeMinutes() != null && ko.getEstimatedPrepTimeMinutes() > 0)
                    .toList();

            if (recentCompleted.isEmpty()) {
                return 0;
            }

            int count = Math.min(20, recentCompleted.size());
            List<KitchenOrder> sample = recentCompleted.subList(recentCompleted.size() - count, recentCompleted.size());

            double totalBiasMinutes = 0.0;
            int validCount = 0;

            for (KitchenOrder ko : sample) {
                long actualMins = Duration.between(ko.getActualStartTime(), ko.getActualReadyTime()).toMinutes();
                int estimatedMins = ko.getEstimatedPrepTimeMinutes();
                double diff = actualMins - estimatedMins;
                totalBiasMinutes += diff;
                validCount++;
            }

            if (validCount == 0) {
                return 0;
            }

            double avgBias = totalBiasMinutes / validCount;
            // Clamp bias to [-5, +15] to prevent anomalous swings
            int clamped = (int) Math.round(Math.max(-5.0, Math.min(15.0, avgBias)));
            return clamped;
        } catch (Exception e) {
            log.warn("Failed to calculate historical bias, defaulting to 0: {}", e.getMessage());
            return 0;
        }
    }

    private void logPredictionToKitchenOrder(Long orderId, int estimatedMinutes, Instant estimatedReadyTime, boolean isVip) {
        Optional<KitchenOrder> koOpt = kitchenOrderRepository.findByOrderId(orderId);
        KitchenOrder ko;
        if (koOpt.isPresent()) {
            ko = koOpt.get();
        } else {
            Order order = orderRepository.findById(orderId).orElse(null);
            ko = KitchenOrder.builder()
                    .order(order)
                    .status(OrderKitchenStatus.PENDING)
                    .priority(isVip ? 3 : 1)
                    .kitchenStation("GRILL")
                    .build();
        }

        ko.setEstimatedPrepTimeMinutes(estimatedMinutes);
        ko.setEstimatedReadyTime(estimatedReadyTime);
        if (isVip && (ko.getPriority() == null || ko.getPriority() < 3)) {
            ko.setPriority(3);
        }

        kitchenOrderRepository.save(ko);
        log.info("[FR-045 Log] KitchenOrder #{} updated for Order #{}: estimatedPrepTime={}m, estimatedReadyTime={}",
                ko.getId(), orderId, estimatedMinutes, estimatedReadyTime);
    }

    private record DishItemDetail(String dishName, int quantity, int baseMinutes) {}
    private record DishCookingCalculation(int baseCookingMinutes, String longestDishName, int longestDishMinutes) {}
}
