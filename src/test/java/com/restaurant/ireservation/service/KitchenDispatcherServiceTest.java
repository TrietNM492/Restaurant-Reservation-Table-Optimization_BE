package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import com.restaurant.ireservation.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KitchenDispatcherServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private KitchenOrderRepository kitchenOrderRepository;
    @Mock
    private PaymentRepository paymentRepository;

    private KitchenDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        dispatcherService = new KitchenDispatcherService(
                orderRepository,
                orderItemRepository,
                kitchenOrderRepository,
                paymentRepository
        );
    }

    @Test
    void testDispatchEligibleOrders_HoldsOffWhenTooEarly() {
        Instant startTime = Instant.now().plus(Duration.ofMinutes(120));
        Reservation reservation = Reservation.builder()
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-TEST-001")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(800000.0)
                .build();
        order.setId(101L);

        MenuItem steak = MenuItem.builder()
                .itemName("Ribeye Steak")
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(2)
                .build();

        when(orderRepository.findByOrderStatusIn(any())).thenReturn(List.of(order));
        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(item));
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(101L), any())).thenReturn(false);
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(101L), any())).thenReturn(400000.0); // 50% paid

        int dispatched = dispatcherService.dispatchEligibleOrdersToKitchen();

        assertThat(dispatched).isEqualTo(0);
        verify(kitchenOrderRepository, never()).save(any(KitchenOrder.class));
    }

    @Test
    void testDispatchEligibleOrders_HoldsOffWhenDepositBelowFiftyPercent() {
        // Reservation is in 20 minutes (within prep window), but customer has NOT paid 50% deposit of current total
        Instant startTime = Instant.now().plus(Duration.ofMinutes(20));
        Reservation reservation = Reservation.builder()
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-UNPAID-001")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(800000.0) // 50% required = 400,000 VND
                .build();
        order.setId(105L);

        MenuItem steak = MenuItem.builder()
                .itemName("Ribeye Steak")
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(2)
                .build();

        when(orderRepository.findByOrderStatusIn(any())).thenReturn(List.of(order));
        when(orderItemRepository.findAllByOrderId(105L)).thenReturn(List.of(item));
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(105L), any())).thenReturn(false);
        // Only paid 150,000 VND (less than 400,000 VND required)
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(105L), any())).thenReturn(150000.0);

        int dispatched = dispatcherService.dispatchEligibleOrdersToKitchen();

        // Must hold off dispatch!
        assertThat(dispatched).isEqualTo(0);
        verify(kitchenOrderRepository, never()).save(any(KitchenOrder.class));
    }

    @Test
    void testDispatchEligibleOrders_DispatchesWhenTimeToCookArrivesAndDepositPaid() {
        Instant startTime = Instant.now().plus(Duration.ofMinutes(20));
        Reservation reservation = Reservation.builder()
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-VIP-002")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .totalAmount(500000.0)
                .build();
        order.setId(102L);

        MenuItem steak = MenuItem.builder()
                .itemName("Wagyu A5")
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(1)
                .build();

        when(orderRepository.findByOrderStatusIn(any())).thenReturn(List.of(order));
        when(orderItemRepository.findAllByOrderId(102L)).thenReturn(List.of(item));
        when(kitchenOrderRepository.existsByOrderIdAndStatusIn(eq(102L), any())).thenReturn(false);
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(102L), any())).thenReturn(250000.0); // 50% deposit paid
        when(kitchenOrderRepository.save(any(KitchenOrder.class))).thenAnswer(i -> {
            KitchenOrder ko = i.getArgument(0);
            ko.setId(999L);
            return ko;
        });

        int dispatched = dispatcherService.dispatchEligibleOrdersToKitchen();

        assertThat(dispatched).isEqualTo(1);
        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());

        KitchenOrder saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderKitchenStatus.PENDING);
        assertThat(saved.getPriority()).isEqualTo(3);
        assertThat(saved.getKitchenStation()).isEqualTo("VIP_CHEF");
        assertThat(saved.getNote()).contains("1x Wagyu A5");
    }

    @Test
    void testEstimatePrepTimeMinutes_CalculatesAccurately() {
        MenuItem dish1 = MenuItem.builder()
                .itemName("Grilled Salmon")
                .estimatedPrepTime(Duration.ofMinutes(20))
                .build();
        MenuItem dish2 = MenuItem.builder()
                .itemName("Caesar Salad")
                .estimatedPrepTime(Duration.ofMinutes(10))
                .build();

        OrderItem item1 = OrderItem.builder().menuItem(dish1).quantity(2).build();
        OrderItem item2 = OrderItem.builder().menuItem(dish2).quantity(1).build();

        when(kitchenOrderRepository.countByStatusIn(any())).thenReturn(4L);

        int prepTimeNonVip = dispatcherService.estimatePrepTimeMinutes(List.of(item1, item2), false);
        int prepTimeVip = dispatcherService.estimatePrepTimeMinutes(List.of(item1, item2), true);

        assertThat(prepTimeVip).isLessThan(prepTimeNonVip);
    }

    @Test
    void testHandleAdditionalItemAdded_HoldsOffWhenBeforeArrivalTime() {
        // Customer adds item before reservation arrival time -> waits for 50% deposit before kitchen dispatch
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Reservation reservation = Reservation.builder()
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-003")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .build();
        order.setId(103L);

        MenuItem dessert = MenuItem.builder()
                .itemName("Tiramisu")
                .build();
        OrderItem extraItem = OrderItem.builder()
                .order(order)
                .menuItem(dessert)
                .quantity(1)
                .build();

        dispatcherService.handleAdditionalItemAdded(order, extraItem);

        // Does NOT modify or create kitchen tickets before arrival
        verify(kitchenOrderRepository, never()).save(any(KitchenOrder.class));
    }

    @Test
    void testHandleAdditionalItemAdded_AppendsToTicketWhenCurrentlyCookingAtDiningTime() {
        // Customer has arrived / dining time has reached
        Instant startTime = Instant.now().minus(Duration.ofMinutes(10));
        Reservation reservation = Reservation.builder()
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-003")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .build();
        order.setId(103L);

        KitchenOrder existingTicket = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.COOKING)
                .note("1x Ribeye Steak")
                .estimatedPrepTimeMinutes(20)
                .estimatedReadyTime(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        existingTicket.setId(501L);

        when(kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(103L))
                .thenReturn(Optional.of(existingTicket));

        MenuItem dessert = MenuItem.builder()
                .itemName("Tiramisu")
                .build();
        OrderItem extraItem = OrderItem.builder()
                .order(order)
                .menuItem(dessert)
                .quantity(1)
                .build();

        dispatcherService.handleAdditionalItemAdded(order, extraItem);

        assertThat(existingTicket.getNote()).contains("1x Ribeye Steak");
        assertThat(existingTicket.getNote()).contains("[Gọi thêm]: Tiramisu x1");
        verify(kitchenOrderRepository).save(existingTicket);
    }

    @Test
    void testHandleAdditionalItemAdded_CreatesRound2TicketWhenFirstRoundAlreadyServedAtDiningTime() {
        Instant startTime = Instant.now().minus(Duration.ofMinutes(30));
        Reservation reservation = Reservation.builder()
                .startTime(startTime)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-004")
                .reservation(reservation)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .isVipRoom(true)
                .build();
        order.setId(104L);

        KitchenOrder ticket1 = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.SERVED)
                .kitchenStation("VIP_CHEF")
                .build();
        ticket1.setId(502L);

        when(kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(104L))
                .thenReturn(Optional.of(ticket1));

        MenuItem wine = MenuItem.builder()
                .itemName("Chateau Wine")
                .estimatedPrepTime(Duration.ofMinutes(5))
                .build();
        OrderItem extraItem = OrderItem.builder()
                .order(order)
                .menuItem(wine)
                .quantity(1)
                .build();

        dispatcherService.handleAdditionalItemAdded(order, extraItem);

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());

        KitchenOrder round2 = captor.getValue();
        assertThat(round2.getStatus()).isEqualTo(OrderKitchenStatus.PENDING);
        assertThat(round2.getPriority()).isEqualTo(3);
        assertThat(round2.getNote()).contains("[Gọi thêm - Đợt 2]: Chateau Wine x1");
        assertThat(round2.getKitchenStation()).isEqualTo("VIP_CHEF");
    }
}
