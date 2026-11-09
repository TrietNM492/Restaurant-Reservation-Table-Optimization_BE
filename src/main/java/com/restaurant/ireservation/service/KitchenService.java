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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            itemDTOs = resolveTicketItems(ko, items);
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
                .note(extractCustomerDisplayNote(ko.getNote()))
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

    private List<KitchenOrderItemDTO> resolveTicketItems(KitchenOrder ko, List<OrderItem> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return Collections.emptyList();
        }

        String note = ko.getNote();
        if (note == null || note.isBlank()) {
            return allItems.stream()
                    .map(it -> buildItemDTO(it, it.getQuantity()))
                    .collect(Collectors.toList());
        }

        List<KitchenOrderItemDTO> matchedDTOs = new ArrayList<>();
        List<OrderItem> sortedItems = allItems.stream()
                .filter(it -> it.getMenuItem() != null && it.getMenuItem().getItemName() != null)
                .sorted((a, b) -> Integer.compare(
                        b.getMenuItem().getItemName().length(),
                        a.getMenuItem().getItemName().length()))
                .toList();

        for (OrderItem it : sortedItems) {
            String name = it.getMenuItem().getItemName();
            int qty = extractItemQuantityFromNote(note, name);
            if (qty > 0) {
                matchedDTOs.add(buildItemDTO(it, qty));
            }
        }

        // If no items were identified from note, fall back to displaying all order items
        if (matchedDTOs.isEmpty()) {
            return allItems.stream()
                    .map(it -> buildItemDTO(it, it.getQuantity()))
                    .collect(Collectors.toList());
        }

        return matchedDTOs;
    }

    private int extractItemQuantityFromNote(String note, String itemName) {
        if (note == null || itemName == null || itemName.isBlank()) {
            return 0;
        }

        int totalQty = 0;
        String quotedName = Pattern.quote(itemName.trim());

        // Pattern 1: "ItemName x1" or "ItemName x 1" or "ItemName (x1)"
        Pattern p1 = Pattern.compile(quotedName + "\\s*\\(?x\\s*(\\d+)\\)?", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(note);
        while (m1.find()) {
            try {
                totalQty += Integer.parseInt(m1.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 2: "1x ItemName" or "1 x ItemName"
        Pattern p2 = Pattern.compile("(\\d+)\\s*x\\s*" + quotedName, Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(note);
        while (m2.find()) {
            try {
                totalQty += Integer.parseInt(m2.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // Pattern 3: If mentioned without explicit quantity, but contains the name (e.g. "[Gọi thêm]: Beef Steak")
        if (totalQty == 0 && Pattern.compile("(?i)\\b" + quotedName + "\\b").matcher(note).find()) {
            totalQty = 1;
        }

        return totalQty;
    }

    private KitchenOrderItemDTO buildItemDTO(OrderItem it, int effectiveQuantity) {
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

        BigDecimal unitPriceBd = parseBigDecimal(it.getUnitPrice());
        if (unitPriceBd.compareTo(BigDecimal.ZERO) == 0 && mi != null) {
            unitPriceBd = BigDecimal.valueOf(mi.getPrice());
        }
        double subtotal = unitPriceBd.multiply(BigDecimal.valueOf(effectiveQuantity)).doubleValue();

        return KitchenOrderItemDTO.builder()
                .orderItemId(it.getId())
                .menuItemId(menuItemId)
                .itemName(itemName)
                .quantity(effectiveQuantity)
                .estimatedPrepTimeMinutes(prepMinutes)
                .categoryName(catName)
                .unitPrice(unitPriceBd.toPlainString())
                .subtotal(subtotal)
                .build();
    }

    private BigDecimal parseBigDecimal(String str) {
        if (str == null || str.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(str.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String extractCustomerDisplayNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }

        // If the note has " | ", separate the system summary from actual customer notes
        if (note.contains(" | ")) {
            String[] parts = note.split("\\s*\\|\\s*");
            List<String> realNotes = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                String extracted = extractRealNoteFromPart(trimmed);
                if (extracted != null && !extracted.isBlank()) {
                    realNotes.add(extracted);
                }
            }
            if (!realNotes.isEmpty()) {
                return String.join(" | ", realNotes);
            }
            return null;
        }

        return extractRealNoteFromPart(note.trim());
    }

    private String extractRealNoteFromPart(String part) {
        if (part == null || part.isBlank()) {
            return null;
        }
        String trimmed = part.trim();
        // If it starts with [Gọi thêm - Đợt ...], it's a system tag
        if (trimmed.startsWith("[Gọi thêm") || trimmed.startsWith("[Gọi Thêm")) {
            int openParen = trimmed.indexOf('(');
            int closeParen = trimmed.lastIndexOf(')');
            if (openParen > -1 && closeParen > openParen) {
                String inside = trimmed.substring(openParen + 1, closeParen).trim();
                if (!inside.isEmpty()) {
                    return inside;
                }
            }
            return null; // Pure system summary
        }

        // If it's a summary of dishes like "3x Grilled Chicken, 1x Beef Steak"
        if (trimmed.matches("^(?:\\d+\\s*x\\s*[^,]+(?:,\\s*)?)+$")) {
            return null;
        }

        return trimmed;
    }
}
