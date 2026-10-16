package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.response.KitchenOrderItemDTO;
import com.restaurant.ireservation.dto.response.KitchenQueueResponseDTO;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class KitchenService {

    final KitchenOrderRepository kitchenOrderRepository;
    final OrderItemRepository orderItemRepository;
    final ReservationTableRepository reservationTableRepository;
    final ReservationRoomRepository reservationRoomRepository;

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
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KitchenQueueResponseDTO getKitchenOrderById(Long id) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found with ID: " + id));
        return mapToQueueResponseDTO(kitchenOrder);
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
