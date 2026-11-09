package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
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
    final ReservationRoomRepository reservationRoomRepository;
    final MenuItemRepository menuItemRepository;
    final CustomerRepository customerRepository;
    final UserRepository userRepository;
    final KitchenOrderRepository kitchenOrderRepository;
    final KitchenDispatcherService kitchenDispatcherService;
    final PaymentRepository paymentRepository;
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

        // If walk-in dine-in at table without an existing reservation, link via an
        // in-progress walk-in reservation
        if (reservation == null && table != null) {
            Reservation walkInRes = Reservation.builder()
                    .reservationNo("WALK-" + (System.currentTimeMillis() % 1000000))
                    .reservationType(ReservationType.WALK_IN)
                    .reservationStatus(ReservationStatus.IN_PROGRESS)
                    .partySize(PartySize.SMALL)
                    .startTime(Instant.now())
                    .endTime(Instant.now().plus(Duration.ofHours(2)))
                    .note("Walk-in dining at table " + table.getTableNumber())
                    .build();
            walkInRes.setCreatedBy(username);
            reservation = reservationRepository.save(walkInRes);

            ReservationTable resTable = ReservationTable.builder()
                    .reservation(reservation)
                    .table(table)
                    .build();
            reservationTableRepository.save(resTable);
        }

        // 2b. Transition table status to IN_USE for active dining (walk-in or customer already seated)
        boolean isAdvanceReservation = reservation != null
                && reservation.getStartTime() != null
                && Instant.now().isBefore(reservation.getStartTime())
                && reservation.getReservationType() != ReservationType.WALK_IN
                && reservation.getReservationStatus() != ReservationStatus.IN_PROGRESS;

        if (!isAdvanceReservation && table != null && table.getStatusCode() != TableStatus.IN_USE) {
            table.setStatusCode(TableStatus.IN_USE);
            restaurantTableRepository.save(table);
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

        // 5. Determine VIP Room & calculate room fee
        boolean isVip = Boolean.TRUE.equals(request.getIsVipRoom());
        BigDecimal roomCharge = BigDecimal.ZERO;
        if (reservation != null && reservationRoomRepository != null) {
            List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(reservation.getId());
            if (!resRooms.isEmpty()) {
                isVip = true;
                for (ReservationRoom rr : resRooms) {
                    if (rr.getRoom() != null && rr.getRoom().getPrice() > 0) {
                        roomCharge = roomCharge.add(BigDecimal.valueOf(rr.getRoom().getPrice()));
                    }
                }
            }
        }

        // 6. Build Order entity
        Order order = Order.builder()
                .orderNo(orderNo)
                .reservation(reservation)
                .customerInformation(customerInfo)
                .orderStatus(OrderStatus.PENDING)
                .isVipRoom(isVip)
                .totalAmount(roomCharge.doubleValue())
                .build();
        order.setCreatedBy(username);
        Order savedOrder = orderRepository.save(order);

        // 7. Process initial items if present
        BigDecimal totalAmount = roomCharge;
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

        // Dispatch to kitchen immediately ONLY for walk-in / seated dining (advance pre-orders await 50% deposit and JIT dispatch)
        if (!isAdvanceReservation && !savedItems.isEmpty() && kitchenDispatcherService != null) {
            try {
                kitchenDispatcherService.dispatchOrder(savedOrder, savedItems, request.getCustomerNote());
            } catch (Exception e) {
                log.warn("Failed to dispatch order to kitchen immediately: {}", e.getMessage());
            }
        }

        return toOrderResponseDTO(savedOrder, savedItems, table);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getCustomerOrderStatus(Long orderId, String username) {
        return getCustomerOrderStatus(orderId, null, username);
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getCustomerOrderStatus(Long orderId, Long reservationId, String username) {
        if (username == null || username.isBlank()) {
            throw new ResourceNotFoundException("User is not authenticated!");
        }

        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        CustomerInformation customerInfo = customerRepository.findOneByUserId(user.getId())
                .orElse(null);

        Order order;
        if (orderId != null) {
            order = orderRepository.findWithDetailsById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

            boolean isStaffOrAdmin = user.getRole() != null
                    && ("ROLE_ADMIN".equalsIgnoreCase(user.getRole().getName()) ||
                            "ROLE_STAFF".equalsIgnoreCase(user.getRole().getName()) ||
                            "ROLE_MANAGER".equalsIgnoreCase(user.getRole().getName()));

            if (!isStaffOrAdmin) {
                boolean isOwner = (customerInfo != null && order.getCustomerInformation() != null &&
                        order.getCustomerInformation().getId().equals(customerInfo.getId()))
                        || (order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(username));
                if (!isOwner) {
                    throw new ResourceNotFoundException("Order not found or does not belong to the current customer!");
                }
            }
        } else if (reservationId != null) {
            Optional<Order> resOrderOpt = orderRepository.findFirstByReservationIdAndOrderStatusNotInOrderByCreatedDateDesc(
                    reservationId, CLOSED_STATUSES);
            if (resOrderOpt.isEmpty()) {
                return null;
            }
            order = resOrderOpt.get();
        } else {
            if (customerInfo != null) {
                List<Order> activeOrders = orderRepository.findActiveOrdersByCustomerInfoId(customerInfo.getId(),
                        CLOSED_STATUSES);
                if (activeOrders.isEmpty()) {
                    throw new ResourceNotFoundException("No active order found for the current customer!");
                }
                order = activeOrders.get(0);
            } else {
                List<Order> activeOrders = orderRepository.findByCreatedByAndOrderStatusNotInOrderByCreatedDateDesc(
                        username, CLOSED_STATUSES);
                if (activeOrders.isEmpty()) {
                    throw new ResourceNotFoundException("No active order found for user: " + username);
                }
                order = activeOrders.get(0);
            }
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

        OrderKitchenStatus kitchenStatus = null;
        Instant estimatedReadyTime = null;
        if (kitchenOrderRepository != null && order.getId() != null) {
            Optional<KitchenOrder> latestTicketOpt = kitchenOrderRepository
                    .findFirstByOrderIdOrderByCreatedDateDesc(order.getId());
            if (latestTicketOpt.isPresent()) {
                KitchenOrder ko = latestTicketOpt.get();
                kitchenStatus = ko.getStatus();
                estimatedReadyTime = ko.getEstimatedReadyTime();
            }
        }

        int discountRate = resolveDiscountRate(order);
        BigDecimal orderTotal = BigDecimal.valueOf(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal targetTotalAmount = orderTotal.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = orderTotal.subtract(targetTotalAmount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal alreadyPaidBd = BigDecimal.ZERO;
        BigDecimal remainingBd = targetTotalAmount;
        if (paymentRepository != null && order.getId() != null) {
            Double paidSum = paymentRepository.sumPaidAmountByOrderIdAndStatuses(
                    order.getId(),
                    List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID));
            if (paidSum != null) {
                alreadyPaidBd = BigDecimal.valueOf(paidSum).setScale(2, RoundingMode.HALF_UP);
                remainingBd = targetTotalAmount.subtract(alreadyPaidBd);
                if (remainingBd.compareTo(BigDecimal.ZERO) < 0) {
                    remainingBd = BigDecimal.ZERO;
                }
            }
        }

        Long roomId = null;
        String roomName = null;
        BigDecimal roomPrice = null;
        if (order.isVipRoom() && order.getReservation() != null && reservationRoomRepository != null) {
            List<ReservationRoom> resRooms = reservationRoomRepository
                    .findByReservationId(order.getReservation().getId());
            if (!resRooms.isEmpty() && resRooms.get(0).getRoom() != null) {
                Room r = resRooms.get(0).getRoom();
                roomId = r.getId();
                roomName = r.getRoomName();
                if (r.getPrice() > 0) {
                    roomPrice = BigDecimal.valueOf(r.getPrice()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        Reservation res = order.getReservation();
        Instant resStartTime = (res != null) ? res.getStartTime() : null;
        String resType = (res != null && res.getReservationType() != null) ? res.getReservationType().name() : null;
        boolean isAdvanceRes = res != null
                && res.getStartTime() != null
                && Instant.now().isBefore(res.getStartTime())
                && res.getReservationType() != ReservationType.WALK_IN
                && res.getReservationStatus() != ReservationStatus.IN_PROGRESS
                && res.getReservationStatus() != ReservationStatus.COMPLETED;

        return OrderResponseDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .tableId(table != null ? table.getId() : null)
                .tableNumber(table != null ? table.getTableNumber() : null)
                .roomId(roomId)
                .roomName(roomName)
                .roomPrice(roomPrice)
                .reservationId(res != null ? res.getId() : null)
                .reservationNo(res != null ? res.getReservationNo() : null)
                .reservationStartTime(resStartTime)
                .reservationType(resType)
                .isAdvanceReservation(isAdvanceRes)
                .customerInformationId(
                        order.getCustomerInformation() != null ? order.getCustomerInformation().getId() : null)
                .orderStatus(order.getOrderStatus())
                .kitchenStatus(kitchenStatus)
                .estimatedReadyTime(estimatedReadyTime)
                .isVipRoom(order.isVipRoom())
                .totalAmount(orderTotal)
                .discountRate(discountRate)
                .discountAmount(discountAmount)
                .targetTotalAmount(targetTotalAmount)
                .alreadyPaidAmount(alreadyPaidBd)
                .remainingAmount(remainingBd)
                .items(itemDTOs)
                .createdBy(order.getCreatedBy())
                .createdDate(order.getCreatedDate())
                .lastModifiedDate(order.getLastModifiedDate())
                .build();
    }

    private int resolveDiscountRate(Order order) {
        if (order == null)
            return 0;
        if (paymentRepository != null && order.getId() != null) {
            List<Payment> existingPayments = paymentRepository.findByOrderId(order.getId());
            if (existingPayments != null) {
                for (Payment p : existingPayments) {
                    if (p.getPaymentStatus() == PaymentStatus.PAID
                            || p.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
                        return p.getDiscountRate();
                    }
                }
            }
        }
        if (order.getCustomerInformation() != null && order.getCustomerInformation().getCustomerRank() != null) {
            RankCustomer rank = order.getCustomerInformation().getCustomerRank();
            if (rank == RankCustomer.CAOTHU)
                return 15;
            if (rank == RankCustomer.DIAMOND)
                return 10;
            if (rank == RankCustomer.GOLDEN)
                return 5;
        }
        return 0;
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
