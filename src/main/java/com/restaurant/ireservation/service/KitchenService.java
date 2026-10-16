package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.request.KitchenStatusUpdateRequest;
import com.restaurant.ireservation.dto.response.KitchenDashboardDTO;
import com.restaurant.ireservation.dto.response.KitchenOrderItemDTO;
import com.restaurant.ireservation.dto.response.KitchenQueueResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module: Kitchen Queue Tracking [3] & Kitchen Status Update [2] & Kitchen Load Monitoring [2]
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class KitchenService {

    KitchenOrderRepository kitchenOrderRepository;
    OrderItemRepository orderItemRepository;
    ReservationTableRepository reservationTableRepository;
    ReservationRoomRepository reservationRoomRepository;

    static final List<OrderKitchenStatus> ACTIVE_STATUSES = List.of(
            OrderKitchenStatus.QUEUED,
            OrderKitchenStatus.PREPARING,
            OrderKitchenStatus.READY,
            OrderKitchenStatus.PENDING,
            OrderKitchenStatus.COOKING,
            OrderKitchenStatus.DONE,
            OrderKitchenStatus.ON_HOLD
    );

    @Transactional(readOnly = true)
    public List<KitchenQueueResponseDTO> getKitchenOrders(OrderKitchenStatus status, Boolean isVip, String search) {
        String querySearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        List<KitchenOrder> orders;

        if (status != null) {
            orders = kitchenOrderRepository.findKitchenQueue(status, isVip, querySearch);
        } else {
            orders = kitchenOrderRepository.findActiveKitchenQueue(ACTIVE_STATUSES, isVip, querySearch);
        }

        return orders.stream()
                .map(this::mapToQueueResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public KitchenQueueResponseDTO getKitchenOrderById(Long id) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found with ID: " + id));
        return mapToQueueResponseDTO(kitchenOrder);
    }

    /**
     * FR-030: Kitchen Load Monitoring
     * Theo dõi: Pending Orders | Average Preparation Time | Kitchen Utilization
     */
    @Transactional(readOnly = true)
    public KitchenDashboardDTO getKitchenDashboard() {
        // --- Pending Orders ---
        List<OrderKitchenStatus> pendingStatuses = List.of(
                OrderKitchenStatus.QUEUED, OrderKitchenStatus.PENDING, OrderKitchenStatus.ON_HOLD);
        long pendingCount = kitchenOrderRepository.countByStatusIn(pendingStatuses);

        // --- Preparing ---
        List<OrderKitchenStatus> preparingStatuses = List.of(
                OrderKitchenStatus.COOKING, OrderKitchenStatus.PREPARING);
        long preparingCount = kitchenOrderRepository.countByStatusIn(preparingStatuses);

        // --- Ready ---
        List<OrderKitchenStatus> readyStatuses = List.of(
                OrderKitchenStatus.READY, OrderKitchenStatus.DONE);
        long readyCount = kitchenOrderRepository.countByStatusIn(readyStatuses);

        long totalActive = pendingCount + preparingCount + readyCount;

        // --- Average Preparation Time (FR-030) ---
        // Tính trong Java bằng Duration.between() để tránh lỗi JPQL EXTRACT dialect
        List<OrderKitchenStatus> completedStatuses = List.of(
                OrderKitchenStatus.READY, OrderKitchenStatus.DONE,
                OrderKitchenStatus.SERVED, OrderKitchenStatus.SERVCED);
        List<KitchenOrder> completedOrders = kitchenOrderRepository.findCompletedWithTimestamps(completedStatuses);
        Double avgMinutes = null;
        if (!completedOrders.isEmpty()) {
            double totalSeconds = completedOrders.stream()
                    .mapToLong(ko -> Duration.between(ko.getActualStartTime(), ko.getActualReadyTime()).toSeconds())
                    .average()
                    .orElse(0.0);
            avgMinutes = totalSeconds / 60.0;
        }

        // --- Kitchen Utilization (FR-030) ---
        // Quy ước: bếp tối đa xử lý đồng thời 20 đơn; tính theo đơn đang chế biến + ready
        final int MAX_KITCHEN_CAPACITY = 20;
        double utilization = Math.min(100.0, (preparingCount + readyCount) * 100.0 / MAX_KITCHEN_CAPACITY);

        // --- Breakdown by status ---
        List<Object[]> grouped = kitchenOrderRepository.countGroupedByStatus();
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (Object[] row : grouped) {
            String statusName = row[0] != null ? row[0].toString() : "UNKNOWN";
            Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            countByStatus.put(statusName, count);
        }

        log.info("[Kitchen Dashboard] pending={}, preparing={}, ready={}, utilization={}%",
                pendingCount, preparingCount, readyCount, String.format("%.1f", utilization));

        return KitchenDashboardDTO.builder()
                .pendingOrdersCount(pendingCount)
                .preparingOrdersCount(preparingCount)
                .readyOrdersCount(readyCount)
                .totalActiveOrdersCount(totalActive)
                .averagePrepTimeMinutes(avgMinutes)
                .kitchenUtilizationPercent(utilization)
                .orderCountByStatus(countByStatus)
                .build();
    }

    /**
     * Module: Kitchen Status Update [2]
     * Cập nhật trạng thái chế biến của một đơn bếp.
     * Tự động ghi nhận timestamp theo từng milestone trạng thái.
     */
    @Transactional
    public KitchenQueueResponseDTO updateKitchenOrderStatus(Long id, KitchenStatusUpdateRequest request) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found with ID: " + id));

        OrderKitchenStatus currentStatus = kitchenOrder.getStatus();
        OrderKitchenStatus newStatus = request.getStatus();

        // Validate terminal states — cannot update once CANCELLED or SERVED
        Set<OrderKitchenStatus> terminalStatuses = Set.of(
                OrderKitchenStatus.CANCELLED, OrderKitchenStatus.CANCELED,
                OrderKitchenStatus.SERVED, OrderKitchenStatus.SERVCED,
                OrderKitchenStatus.REJECTED
        );
        if (terminalStatuses.contains(currentStatus)) {
            throw new RequestInvalidException(
                    "Cannot update status. Kitchen order is already in terminal state: " + currentStatus);
        }

        // Auto-set timestamps based on the new status milestone
        Instant now = Instant.now();
        if ((newStatus == OrderKitchenStatus.COOKING || newStatus == OrderKitchenStatus.PREPARING)
                && kitchenOrder.getActualStartTime() == null) {
            kitchenOrder.setActualStartTime(now);
            log.info("[Kitchen] Order #{} started cooking at {}", id, now);
        }
        if ((newStatus == OrderKitchenStatus.DONE || newStatus == OrderKitchenStatus.READY)
                && kitchenOrder.getActualReadyTime() == null) {
            kitchenOrder.setActualReadyTime(now);
            log.info("[Kitchen] Order #{} marked ready at {}", id, now);
        }
        if ((newStatus == OrderKitchenStatus.SERVED || newStatus == OrderKitchenStatus.SERVCED)
                && kitchenOrder.getActualServedTime() == null) {
            kitchenOrder.setActualServedTime(now);
            log.info("[Kitchen] Order #{} served at {}", id, now);
        }

        kitchenOrder.setStatus(newStatus);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            kitchenOrder.setNote(request.getNote());
        }

        KitchenOrder saved = kitchenOrderRepository.save(kitchenOrder);
        log.info("[Kitchen] Order #{} status updated: {} -> {}", id, currentStatus, newStatus);
        return mapToQueueResponseDTO(saved);
    }

    private KitchenQueueResponseDTO mapToQueueResponseDTO(KitchenOrder ko) {
        Order order = ko.getOrder();
        Long orderId = order != null ? order.getId() : null;
        String orderNo = order != null ? order.getOrderNo() : null;
        boolean isVip = order != null && order.isVipRoom();

        String tableOrRoom = resolveTableOrRoomInfo(order);

        String customerName = "Walk-in Guest";
        if (order != null && order.getCustomerInformation() != null && order.getCustomerInformation().getUser() != null) {
            customerName = order.getCustomerInformation().getUser().getFullname();
        }

        List<KitchenOrderItemDTO> itemDTOs = Collections.emptyList();
        if (orderId != null) {
            List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
            itemDTOs = items.stream().map(it -> {
                MenuItem mi = it.getMenuItem();
                Integer prepMinutes = null;
                String catName = null;
                String itemName = "Dish Item";
                Long menuItemId = null;

                if (mi != null) {
                    menuItemId = mi.getId();
                    itemName = mi.getItemName();
                    if (mi.getEstimatedPrepTime() != null) {
                        prepMinutes = (int) mi.getEstimatedPrepTime().toMinutes();
                    }
                    if (mi.getCategory() != null) {
                        catName = mi.getCategory().getName();
                    }
                }

                return KitchenOrderItemDTO.builder()
                        .orderItemId(it.getId())
                        .menuItemId(menuItemId)
                        .itemName(itemName)
                        .quantity(it.getQuantity())
                        .estimatedPrepTimeMinutes(prepMinutes)
                        .categoryName(catName)
                        .unitPrice(it.getUnitPrice())
                        .subtotal(it.getSubtotal())
                        .build();
            }).collect(Collectors.toList());
        }

        Instant created = ko.getCreatedDate() != null ? ko.getCreatedDate() : Instant.now();
        long waitingMinutes = Math.max(0, Duration.between(created, Instant.now()).toMinutes());

        Integer priority = ko.getPriority() != null ? ko.getPriority() : 1;
        String priorityLabel;
        if (priority >= 3) {
            priorityLabel = "VIP_URGENT";
        } else if (priority == 2) {
            priorityLabel = "HIGH";
        } else {
            priorityLabel = "NORMAL";
        }

        return KitchenQueueResponseDTO.builder()
                .kitchenOrderId(ko.getId())
                .orderId(orderId)
                .orderNo(orderNo)
                .status(ko.getStatus())
                .priority(priority)
                .priorityLabel(priorityLabel)
                .isVip(isVip)
                .tableOrRoomInfo(tableOrRoom)
                .customerName(customerName)
                .orderTime(created)
                .waitingMinutes(waitingMinutes)
                .estimatedPrepTimeMinutes(ko.getEstimatedPrepTimeMinutes())
                .estimatedReadyTime(ko.getEstimatedReadyTime())
                .actualStartTime(ko.getActualStartTime())
                .actualReadyTime(ko.getActualReadyTime())
                .actualServedTime(ko.getActualServedTime())
                .kitchenStation(ko.getKitchenStation())
                .note(ko.getNote())
                .items(itemDTOs)
                .build();
    }

    private String resolveTableOrRoomInfo(Order order) {
        if (order == null || order.getReservation() == null) {
            return "Standard Dine-in";
        }

        Reservation res = order.getReservation();
        if (order.isVipRoom()) {
            List<ReservationRoom> rooms = reservationRoomRepository.findByReservationId(res.getId());
            if (!rooms.isEmpty() && rooms.get(0).getRoom() != null) {
                return "VIP Room: " + rooms.get(0).getRoom().getRoomName();
            }
            return "VIP Room";
        } else {
            List<ReservationTable> tables = reservationTableRepository.findByReservationId(res.getId());
            if (!tables.isEmpty() && tables.get(0).getTable() != null) {
                RestaurantTable table = tables.get(0).getTable();
                String areaName = (table.getArea() != null && table.getArea().getName() != null)
                        ? table.getArea().getName().name()
                        : "General";
                return "Table #" + table.getTableNumber() + " (" + areaName + ")";
            }
            return "Table Reservation";
        }
    }
}
