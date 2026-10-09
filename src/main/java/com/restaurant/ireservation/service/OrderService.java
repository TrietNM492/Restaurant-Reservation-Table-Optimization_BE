package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.dto.request.CreateOrderRequestDTO;
import com.restaurant.ireservation.dto.response.OrderItemResponseDTO;
import com.restaurant.ireservation.dto.response.OrderResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class OrderService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");
    static final List<OrderStatus> CLOSED_STATUSES = List.of(OrderStatus.COMPLETED, OrderStatus.OK);

    final OrderRepository orderRepository;
    final OrderItemRepository orderItemRepository;
    final RestaurantTableRepository restaurantTableRepository;
    final ReservationRepository reservationRepository;
    final ReservationTableRepository reservationTableRepository;
    final MenuItemRepository menuItemRepository;
    final CustomerRepository customerRepository;
    final UserRepository userRepository;
    final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, String username) {
        if (request.getTableId() == null && request.getReservationId() == null) {
            throw new RequestInvalidException("Please provide tableId or reservationId to create an order!");
        }

        Reservation reservation = null;
        RestaurantTable table = null;

        // 1. Check table if tableId is provided
        if (request.getTableId() != null) {
            table = restaurantTableRepository.findById(request.getTableId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Table not found with id: " + request.getTableId()));

            boolean hasActiveOrder = orderRepository.existsActiveOrderByTableId(table.getId(), CLOSED_STATUSES);
            if (hasActiveOrder) {
                throw new RequestInvalidException(
                        "Table " + table.getTableNumber() + " already has an active order, cannot create a new one!");
            }
        }

        // 2. Check reservation if reservationId is provided
        if (request.getReservationId() != null) {
            reservation = reservationRepository.findById(request.getReservationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Reservation not found with id: " + request.getReservationId()));

            boolean hasActiveOrder = orderRepository.existsByReservationIdAndOrderStatusNotIn(reservation.getId(),
                    CLOSED_STATUSES);
            if (hasActiveOrder) {
                throw new RequestInvalidException("This reservation already has an active order!");
            }

            // Look up associated table if table was not directly provided
            if (table == null) {
                List<ReservationTable> resTables = reservationTableRepository.findByReservationId(reservation.getId());
                if (!resTables.isEmpty() && resTables.get(0).getTable() != null) {
                    table = resTables.get(0).getTable();
                    if (orderRepository.existsActiveOrderByTableId(table.getId(), CLOSED_STATUSES)) {
                        throw new RequestInvalidException("Table " + table.getTableNumber()
                                + " of this reservation already has an active order!");
                    }
                }
            }
        }

        // 3. Customer information (if provided)
        CustomerInformation customerInfo = null;
        if (request.getCustomerInformationId() != null) {
            customerInfo = customerRepository.findById(request.getCustomerInformationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer information not found with id: " + request.getCustomerInformationId()));
        } else if (username != null) {
            var userOpt = userRepository.findUserByUsername(username);
            if (userOpt.isPresent()) {
                customerInfo = customerRepository.findOneByUserId(userOpt.get().getId()).orElse(null);
            }
        }

        // 4. Generate orderNo (ORD-ddMMyyyy-XXX)
        String orderNo = generateOrderNo();

        // 5. Build Order entity
        Order order = Order.builder()
                .orderNo(orderNo)
                .reservation(reservation)
                .customerInformation(customerInfo)
                .orderStatus(OrderStatus.PENDING)
                .isVipRoom(Boolean.TRUE.equals(request.getIsVipRoom()))
                .totalAmount(0.0)
                .build();
        order.setCreatedBy(username);
        Order savedOrder = orderRepository.save(order);

        // 6. Process initial items if present
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> savedItems = new ArrayList<>();

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            Map<Long, Integer> quantityMap = new LinkedHashMap<>();

            for (CreateOrderRequestDTO.InitialOrderItemDTO itemDTO : request.getItems()) {
                if (itemDTO.getQuantity() == null || itemDTO.getQuantity() <= 0) {
                    throw new RequestInvalidException("Item quantity must be greater than or equal to 1!");
                }
                quantityMap.merge(itemDTO.getMenuItemId(), itemDTO.getQuantity(), Integer::sum);
            }

            for (Map.Entry<Long, Integer> entry : quantityMap.entrySet()) {
                Long menuItemId = entry.getKey();
                Integer quantity = entry.getValue();

                MenuItem menuItem = menuItemRepository.findById(menuItemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + menuItemId));

                if (menuItem.getStatusCode() != ItemStatus.AVAILABLE) {
                    throw new RequestInvalidException(
                            "Menu item '" + menuItem.getItemName() + "' is currently not available!");
                }

                BigDecimal unitPrice = BigDecimal.valueOf(menuItem.getPrice());
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

                OrderItem orderItem = OrderItem.builder()
                        .order(savedOrder)
                        .menuItem(menuItem)
                        .quantity(quantity)
                        .unitPrice(unitPrice.toPlainString())
                        .subtotal(subtotal.doubleValue())
                        .build();

                savedItems.add(orderItemRepository.save(orderItem));
                totalAmount = totalAmount.add(subtotal);
            }

            savedOrder.setTotalAmount(totalAmount.doubleValue());
            savedOrder = orderRepository.save(savedOrder);
        }

        return toOrderResponseDTO(savedOrder, savedItems, table);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getCustomerOrderStatus(Long orderId, String username) {
        if (username == null || username.isBlank()) {
            throw new ResourceNotFoundException("User is not authenticated!");
        }

        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        CustomerInformation customerInfo = customerRepository.findOneByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + username));

        Order order;
        if (orderId != null) {
            order = orderRepository.findWithDetailsById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

            if (order.getCustomerInformation() == null ||
                    !order.getCustomerInformation().getId().equals(customerInfo.getId())) {
                throw new ResourceNotFoundException("Order not found or does not belong to the current customer!");
            }
        } else {
            List<Order> activeOrders = orderRepository.findActiveOrdersByCustomerInfoId(customerInfo.getId(),
                    CLOSED_STATUSES);
            if (activeOrders.isEmpty()) {
                throw new ResourceNotFoundException("No active order found for the current customer!");
            }
            order = activeOrders.get(0);
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        RestaurantTable table = getTableFromOrder(order);

        return toOrderResponseDTO(order, items, table);
    }

    private String generateOrderNo() {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        long seq = 1L;
        try {
            if (stringRedisTemplate != null) {
                String redisKey = "order:seq:" + dateStr;
                Long counter = stringRedisTemplate.opsForValue().increment(redisKey);
                if (counter != null && counter == 1L) {
                    stringRedisTemplate.expire(redisKey, Duration.ofDays(2));
                }
                if (counter != null) {
                    seq = counter;
                }
            }
        } catch (Exception e) {
            log.warn("Redis is not available for orderNo sequence, using fallback timestamp: {}", e.getMessage());
            seq = (System.currentTimeMillis() % 1000) + 1;
        }
        return String.format("ORD-%s-%03d", dateStr, seq);
    }

    private RestaurantTable getTableFromOrder(Order order) {
        if (order.getReservation() != null) {
            List<ReservationTable> resTables = reservationTableRepository
                    .findByReservationId(order.getReservation().getId());
            if (!resTables.isEmpty() && resTables.get(0).getTable() != null) {
                return resTables.get(0).getTable();
            }
        }
        return null;
    }

    public OrderResponseDTO toOrderResponseDTO(Order order, List<OrderItem> items, RestaurantTable table) {
        if (table == null) {
            table = getTableFromOrder(order);
        }

        List<OrderItemResponseDTO> itemDTOs = (items != null) ? items.stream()
                .map(this::toOrderItemResponseDTO)
                .toList() : Collections.emptyList();

        return OrderResponseDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .tableId(table != null ? table.getId() : null)
                .tableNumber(table != null ? table.getTableNumber() : null)
                .reservationId(order.getReservation() != null ? order.getReservation().getId() : null)
                .reservationNo(order.getReservation() != null ? order.getReservation().getReservationNo() : null)
                .customerInformationId(
                        order.getCustomerInformation() != null ? order.getCustomerInformation().getId() : null)
                .orderStatus(order.getOrderStatus())
                .isVipRoom(order.isVipRoom())
                .totalAmount(BigDecimal.valueOf(order.getTotalAmount()))
                .items(itemDTOs)
                .createdBy(order.getCreatedBy())
                .createdDate(order.getCreatedDate())
                .lastModifiedDate(order.getLastModifiedDate())
                .build();
    }

    public OrderItemResponseDTO toOrderItemResponseDTO(OrderItem item) {
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
