package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.*;
import com.restaurant.ireservation.dto.request.AddOrderItemRequestDTO;
import com.restaurant.ireservation.dto.request.CreateOrderRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateOrderItemRequestDTO;
import com.restaurant.ireservation.dto.response.OrderItemResponseDTO;
import com.restaurant.ireservation.dto.response.OrderResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VipRoomOrderPaymentIntegrationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationTableRepository reservationTableRepository;
    @Mock
    private ReservationRoomRepository reservationRoomRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private KitchenOrderRepository kitchenOrderRepository;
    @Mock
    private KitchenDispatcherService kitchenDispatcherService;

    private OrderService orderService;
    private OrderItemService orderItemService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                restaurantTableRepository,
                reservationRepository,
                reservationTableRepository,
                reservationRoomRepository,
                menuItemRepository,
                customerRepository,
                userRepository,
                kitchenOrderRepository,
                stringRedisTemplate
        );

        orderItemService = new OrderItemService(
                orderRepository,
                orderItemRepository,
                menuItemRepository,
                reservationRoomRepository,
                kitchenOrderRepository,
                kitchenDispatcherService
        );
    }

    @Test
    void testCreateOrder_WithVipRoom_CalculatesRoomPrice() {
        Reservation reservation = Reservation.builder()
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(Instant.now().plusSeconds(7200))
                .build();
        reservation.setId(200L);

        Room room = Room.builder()
                .roomName("Presidential VIP Suite")
                .roomType(RoomType.VIP_ROOM)
                .price(500000.0)
                .build();
        ReservationRoom resRoom = ReservationRoom.builder()
                .reservation(reservation)
                .room(room)
                .build();

        when(reservationRepository.findById(200L)).thenReturn(Optional.of(reservation));
        when(orderRepository.existsByReservationIdAndOrderStatusNotIn(eq(200L), any())).thenReturn(false);
        when(reservationRoomRepository.findByReservationId(200L)).thenReturn(List.of(resRoom));

        MenuItem steak = MenuItem.builder()
                .itemName("Wagyu Steak")
                .price(200000.0)
                .statusCode(ItemStatus.AVAILABLE)
                .build();
        steak.setId(10L);

        when(menuItemRepository.findById(10L)).thenReturn(Optional.of(steak));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequestDTO.InitialOrderItemDTO itemDTO = new CreateOrderRequestDTO.InitialOrderItemDTO();
        itemDTO.setMenuItemId(10L);
        itemDTO.setQuantity(2); // 2 * 200,000 = 400,000

        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setReservationId(200L);
        request.setIsVipRoom(true);
        request.setItems(List.of(itemDTO));

        OrderResponseDTO response = orderService.createOrder(request, "customer1");

        assertThat(response.isVipRoom()).isTrue();
        // Room 500,000 + 400,000 items = 900,000 VND
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(900000.0));
    }

    @Test
    void testAddOrderItem_PreservesVipRoomCharge() {
        Reservation reservation = Reservation.builder()
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(Instant.now().plusSeconds(7200))
                .build();
        reservation.setId(200L);

        Room room = Room.builder()
                .roomName("Presidential VIP Suite")
                .price(500000.0)
                .build();
        ReservationRoom resRoom = ReservationRoom.builder()
                .reservation(reservation)
                .room(room)
                .build();

        Order order = Order.builder()
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(900000.0)
                .build();
        order.setId(100L);

        MenuItem juice = MenuItem.builder()
                .itemName("Fresh Orange Juice")
                .price(50000.0)
                .statusCode(ItemStatus.AVAILABLE)
                .build();
        juice.setId(20L);

        OrderItem existingSteak = OrderItem.builder()
                .order(order)
                .quantity(2)
                .unitPrice("200000")
                .subtotal(400000.0)
                .build();

        OrderItem newJuiceItem = OrderItem.builder()
                .order(order)
                .menuItem(juice)
                .quantity(1)
                .unitPrice("50000")
                .subtotal(50000.0)
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(menuItemRepository.findById(20L)).thenReturn(Optional.of(juice));
        when(orderItemRepository.findByOrderIdAndMenuItemId(100L, 20L)).thenReturn(Optional.empty());
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(newJuiceItem);
        when(reservationRoomRepository.findByReservationId(200L)).thenReturn(List.of(resRoom));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(existingSteak, newJuiceItem));

        AddOrderItemRequestDTO request = new AddOrderItemRequestDTO();
        request.setOrderId(100L);
        request.setMenuItemId(20L);
        request.setQuantity(1);

        OrderItemResponseDTO result = orderItemService.addOrderItem(request);

        assertThat(result).isNotNull();
        // Total should be room (500,000) + steak (400,000) + juice (50,000) = 950,000
        assertThat(order.getTotalAmount()).isEqualTo(950000.0);
        verify(orderRepository).save(order);
    }

    @Test
    void testUpdateOrderItem_AllowedWhenAwaitingPayment() {
        Reservation reservation = Reservation.builder().build();
        reservation.setId(200L);

        Room room = Room.builder().price(500000.0).build();
        ReservationRoom resRoom = ReservationRoom.builder().reservation(reservation).room(room).build();

        Order order = Order.builder()
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(900000.0)
                .build();
        order.setId(100L);

        MenuItem steak = MenuItem.builder()
                .itemName("Wagyu Steak")
                .price(200000.0)
                .build();

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(2)
                .unitPrice("200000")
                .subtotal(400000.0)
                .build();
        orderItem.setId(1L);

        when(orderItemRepository.findWithDetailsById(1L)).thenReturn(Optional.of(orderItem));
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(100L), any())).thenReturn(false);
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(orderItem);
        when(reservationRoomRepository.findByReservationId(200L)).thenReturn(List.of(resRoom));

        // After updating quantity to 3:
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(
                OrderItem.builder().subtotal(600000.0).build()
        ));

        UpdateOrderItemRequestDTO updateDTO = new UpdateOrderItemRequestDTO();
        updateDTO.setQuantity(3);

        OrderItemResponseDTO response = orderItemService.updateOrderItem(1L, updateDTO);

        assertThat(response).isNotNull();
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getSubtotal()).isEqualTo(600000.0);
        // Room 500,000 + 600,000 items = 1,100,000 VND
        assertThat(order.getTotalAmount()).isEqualTo(1100000.0);
    }

    @Test
    void testUpdateOrderItem_DeniedWhenKitchenIsCooking() {
        Order order = Order.builder()
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .build();
        order.setId(100L);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .quantity(2)
                .build();
        orderItem.setId(1L);

        when(orderItemRepository.findWithDetailsById(1L)).thenReturn(Optional.of(orderItem));
        // Kitchen is already cooking
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(100L), any())).thenReturn(true);

        UpdateOrderItemRequestDTO updateDTO = new UpdateOrderItemRequestDTO();
        updateDTO.setQuantity(3);

        assertThatThrownBy(() -> orderItemService.updateOrderItem(1L, updateDTO))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Kitchen is already cooking");
    }

    @Test
    void testDeleteOrderItem_AllowedWhenAwaitingPayment() {
        Reservation reservation = Reservation.builder().build();
        reservation.setId(200L);

        Room room = Room.builder().price(500000.0).build();
        ReservationRoom resRoom = ReservationRoom.builder().reservation(reservation).room(room).build();

        Order order = Order.builder()
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(900000.0)
                .build();
        order.setId(100L);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .quantity(2)
                .subtotal(400000.0)
                .build();
        orderItem.setId(1L);

        when(orderItemRepository.findWithDetailsById(1L)).thenReturn(Optional.of(orderItem));
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(100L), any())).thenReturn(false);
        when(reservationRoomRepository.findByReservationId(200L)).thenReturn(List.of(resRoom));
        // After deleting, no items remaining
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(Collections.emptyList());

        orderItemService.deleteOrderItem(1L);

        verify(orderItemRepository).delete(orderItem);
        // Only VIP room fee remains
        assertThat(order.getTotalAmount()).isEqualTo(500000.0);
    }

    @Test
    void testToOrderResponseDTO_IncludesKitchenStatusAndEstimatedReadyTime() {
        Order order = Order.builder()
                .orderNo("ORD-TRACK-001")
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(false)
                .totalAmount(350000.0)
                .build();
        order.setId(105L);

        Instant readyTime = Instant.now().plus(Duration.ofMinutes(18));
        KitchenOrder ticket = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.COOKING)
                .estimatedReadyTime(readyTime)
                .build();

        when(kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(105L))
                .thenReturn(Optional.of(ticket));

        OrderResponseDTO response = orderService.toOrderResponseDTO(order, Collections.emptyList(), null);

        assertThat(response).isNotNull();
        assertThat(response.getKitchenStatus()).isEqualTo(OrderKitchenStatus.COOKING);
        assertThat(response.getEstimatedReadyTime()).isEqualTo(readyTime);
    }
}
