package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.dto.request.AddOrderItemRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateOrderItemRequestDTO;
import com.restaurant.ireservation.dto.response.OrderItemResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.ireservation.domain.entity.ReservationRoom;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemService {

    final OrderRepository orderRepository;
    final OrderItemRepository orderItemRepository;
    final MenuItemRepository menuItemRepository;
    final ReservationRoomRepository reservationRoomRepository;
    final KitchenOrderRepository kitchenOrderRepository;
    final KitchenDispatcherService kitchenDispatcherService;

    @Transactional
    public OrderItemResponseDTO addOrderItem(AddOrderItemRequestDTO request) {
        // 1. Check order exists
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        // 2. Only allow adding items if order is not completed
        if (order.getOrderStatus() == OrderStatus.COMPLETED || order.getOrderStatus() == OrderStatus.OK) {
            throw new RequestInvalidException(
                    "Cannot add item to an order that is already in " + order.getOrderStatus() + " status!");
        }

        // 3. Check menu item exists and is available
        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Menu item not found with id: " + request.getMenuItemId()));

        if (menuItem.getStatusCode() != ItemStatus.AVAILABLE) {
            throw new RequestInvalidException("Menu item '" + menuItem.getItemName() + "' is currently not available!");
        }

        // 4. Check if item already exists in order (accumulate quantity if present)
        Optional<OrderItem> existingItemOpt = orderItemRepository.findByOrderIdAndMenuItemId(order.getId(),
                menuItem.getId());
        OrderItem targetItem;

        if (existingItemOpt.isPresent()) {
            targetItem = existingItemOpt.get();
            int newQuantity = targetItem.getQuantity() + request.getQuantity();
            targetItem.setQuantity(newQuantity);

            BigDecimal unitPriceBd = parseBigDecimal(targetItem.getUnitPrice());
            if (unitPriceBd.compareTo(BigDecimal.ZERO) == 0) {
                unitPriceBd = BigDecimal.valueOf(menuItem.getPrice());
                targetItem.setUnitPrice(unitPriceBd.toPlainString());
            }

            BigDecimal subtotalBd = unitPriceBd.multiply(BigDecimal.valueOf(newQuantity));
            targetItem.setSubtotal(subtotalBd.doubleValue());
        } else {
            BigDecimal unitPriceBd = BigDecimal.valueOf(menuItem.getPrice());
            BigDecimal subtotalBd = unitPriceBd.multiply(BigDecimal.valueOf(request.getQuantity()));

            targetItem = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)
                    .quantity(request.getQuantity())
                    .unitPrice(unitPriceBd.toPlainString())
                    .subtotal(subtotalBd.doubleValue())
                    .build();
        }

        OrderItem savedItem = orderItemRepository.save(targetItem);

        // 5. Recalculate order total amount (including VIP room price if applicable)
        BigDecimal newOrderTotal = recalculateOrderTotal(order);
        order.setTotalAmount(newOrderTotal.doubleValue());
        orderRepository.save(order);

        // 6. Notify kitchen dispatcher if order was already sent to the kitchen
        if (kitchenDispatcherService != null) {
            kitchenDispatcherService.handleAdditionalItemAdded(order, savedItem);
        }

        return toResponseDTO(savedItem);
    }

    @Transactional
    public OrderItemResponseDTO updateOrderItem(Long id, UpdateOrderItemRequestDTO request) {
        // 1. Check order item exists
        OrderItem orderItem = orderItemRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        Order order = orderItem.getOrder();

        // 2. Allow editing when order is PENDING or AWAITING_PAYMENT (before kitchen cooking)
        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new RequestInvalidException("Cannot update item. Order is currently in " + order.getOrderStatus()
                    + " status. Only orders prior to dining/cooking can be modified.");
        }

        // Check if kitchen has already started cooking or beyond
        if (kitchenOrderRepository != null) {
            boolean isCookingOrBeyond = kitchenOrderRepository.existsByOrderIdAndStatusIn(
                    order.getId(),
                    List.of(
                            OrderKitchenStatus.COOKING,
                            OrderKitchenStatus.DONE,
                            OrderKitchenStatus.PREPARING,
                            OrderKitchenStatus.READY,
                            OrderKitchenStatus.SERVED,
                            OrderKitchenStatus.SERVCED
                    )
            );
            if (isCookingOrBeyond) {
                throw new RequestInvalidException("Cannot update item. Kitchen is already cooking or has served dishes for this order.");
            }
        }

        // 3. Nếu số lượng = 0 thì hủy/xóa món ăn khỏi đơn
        if (request.getQuantity() == 0) {
            orderItemRepository.delete(orderItem);
            orderItemRepository.flush();

            BigDecimal newOrderTotal = recalculateOrderTotal(order);
            order.setTotalAmount(newOrderTotal.doubleValue());
            orderRepository.save(order);

            return null;
        }

        // Cập nhật số lượng mới (> 0)
        orderItem.setQuantity(request.getQuantity());

        BigDecimal unitPriceBd = parseBigDecimal(orderItem.getUnitPrice());
        if (unitPriceBd.compareTo(BigDecimal.ZERO) == 0 && orderItem.getMenuItem() != null) {
            unitPriceBd = BigDecimal.valueOf(orderItem.getMenuItem().getPrice());
            orderItem.setUnitPrice(unitPriceBd.toPlainString());
        }

        BigDecimal subtotalBd = unitPriceBd.multiply(BigDecimal.valueOf(request.getQuantity()));
        orderItem.setSubtotal(subtotalBd.doubleValue());

        OrderItem savedItem = orderItemRepository.save(orderItem);

        // 4. Tự động tính lại tổng tiền của đơn sau khi sửa
        BigDecimal newOrderTotal = recalculateOrderTotal(order);
        order.setTotalAmount(newOrderTotal.doubleValue());
        orderRepository.save(order);

        return toResponseDTO(savedItem);
    }

    @Transactional
    public void deleteOrderItem(Long id) {
        OrderItem orderItem = orderItemRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        Order order = orderItem.getOrder();

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new RequestInvalidException("Cannot delete item. Order is currently in " + order.getOrderStatus()
                    + " status. Only orders prior to dining/cooking can be modified.");
        }

        if (kitchenOrderRepository != null) {
            boolean isCookingOrBeyond = kitchenOrderRepository.existsByOrderIdAndStatusIn(
                    order.getId(),
                    List.of(
                            OrderKitchenStatus.COOKING,
                            OrderKitchenStatus.DONE,
                            OrderKitchenStatus.PREPARING,
                            OrderKitchenStatus.READY,
                            OrderKitchenStatus.SERVED,
                            OrderKitchenStatus.SERVCED
                    )
            );
            if (isCookingOrBeyond) {
                throw new RequestInvalidException("Cannot delete item. Kitchen is already cooking or has served dishes for this order.");
            }
        }

        orderItemRepository.delete(orderItem);
        orderItemRepository.flush();

        BigDecimal newOrderTotal = recalculateOrderTotal(order);
        order.setTotalAmount(newOrderTotal.doubleValue());
        orderRepository.save(order);
    }

    private BigDecimal recalculateOrderTotal(Order order) {
        List<OrderItem> allItems = orderItemRepository.findAllByOrderId(order.getId());
        BigDecimal itemsTotal = allItems.stream()
                .map(item -> BigDecimal.valueOf(item.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal roomCharge = BigDecimal.ZERO;
        if (order.getReservation() != null && reservationRoomRepository != null) {
            List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(order.getReservation().getId());
            for (ReservationRoom rr : resRooms) {
                if (rr.getRoom() != null && rr.getRoom().getPrice() > 0) {
                    roomCharge = roomCharge.add(BigDecimal.valueOf(rr.getRoom().getPrice()));
                }
            }
        }
        return itemsTotal.add(roomCharge);
    }

    private OrderItemResponseDTO toResponseDTO(OrderItem item) {
        BigDecimal unitPriceBd = parseBigDecimal(item.getUnitPrice());
        BigDecimal subtotalBd = BigDecimal.valueOf(item.getSubtotal());

        return OrderItemResponseDTO.builder()
                .id(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null)
                .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                .menuItemName(item.getMenuItem() != null ? item.getMenuItem().getItemName() : null)
                .quantity(item.getQuantity())
                .unitPrice(unitPriceBd)
                .subtotal(subtotalBd)
                .createdDate(item.getCreatedDate())
                .build();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
