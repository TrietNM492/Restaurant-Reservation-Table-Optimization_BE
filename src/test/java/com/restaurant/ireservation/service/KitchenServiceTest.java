package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.response.KitchenQueueResponseDTO;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    @Mock
    private KitchenOrderRepository kitchenOrderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ReservationTableRepository reservationTableRepository;
    @Mock
    private ReservationRoomRepository reservationRoomRepository;

    private KitchenService kitchenService;

    @BeforeEach
    void setUp() {
        kitchenService = new KitchenService(
                kitchenOrderRepository,
                orderItemRepository,
                reservationTableRepository,
                reservationRoomRepository
        );
    }

    @Test
    void testMapToQueueResponseDTO_Round2Ticket_FiltersOutRound1Dishes() {
        Order order = Order.builder()
                .orderNo("ORD-08112026-001")
                .build();
        order.setId(101L);

        MenuItem chicken = MenuItem.builder()
                .itemName("Grilled Chicken")
                .price(120000.0)
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();
        chicken.setId(1L);

        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .price(180000.0)
                .estimatedPrepTime(Duration.ofMinutes(36))
                .build();
        steak.setId(2L);

        // In DB, accumulated quantity for steak is 2 (1 from round 1 + 1 from round 2)
        OrderItem itemChicken = OrderItem.builder()
                .order(order)
                .menuItem(chicken)
                .quantity(3)
                .unitPrice("120000")
                .subtotal(360000.0)
                .build();
        itemChicken.setId(201L);

        OrderItem itemSteak = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(2)
                .unitPrice("180000")
                .subtotal(360000.0)
                .build();
        itemSteak.setId(202L);

        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(itemChicken, itemSteak));

        // Kitchen Order Ticket #2 (Round 2 for only 1 Steak)
        KitchenOrder ticketRound2 = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.PENDING)
                .note("[Gọi thêm - Đợt 2]: Beef Steak x1")
                .estimatedPrepTimeMinutes(36)
                .build();
        ticketRound2.setId(502L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any()))
                .thenReturn(List.of(ticketRound2));

        List<KitchenQueueResponseDTO> result = kitchenService.getKitchenOrders(null, null, null);

        assertThat(result).hasSize(1);
        KitchenQueueResponseDTO dto = result.get(0);
        assertThat(dto.getKitchenOrderId()).isEqualTo(502L);
        // System tag [Gọi thêm] is not shown to kitchen as note when no customer note
        assertThat(dto.getNote()).isNull();

        // MUST only contain Beef Steak x1, NOT Grilled Chicken x3 or Beef Steak x2!
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getItemName()).isEqualTo("Beef Steak");
        assertThat(dto.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(dto.getItems().get(0).getSubtotal()).isEqualTo(180000.0);
    }

    @Test
    void testMapToQueueResponseDTO_Round2Ticket_PreservesRealCustomerNoteOnly() {
        Order order = Order.builder()
                .orderNo("ORD-08112026-001")
                .build();
        order.setId(101L);

        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .price(180000.0)
                .estimatedPrepTime(Duration.ofMinutes(36))
                .build();
        steak.setId(2L);

        OrderItem itemSteak = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(1)
                .unitPrice("180000")
                .subtotal(180000.0)
                .build();

        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(itemSteak));

        KitchenOrder ticket = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.PENDING)
                .note("[Gọi thêm - Đợt 2]: Beef Steak x1 | Không tiêu, chín kỹ")
                .estimatedPrepTimeMinutes(36)
                .build();
        ticket.setId(505L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any()))
                .thenReturn(List.of(ticket));

        List<KitchenQueueResponseDTO> result = kitchenService.getKitchenOrders(null, null, null);

        assertThat(result).hasSize(1);
        KitchenQueueResponseDTO dto = result.get(0);
        // Only real customer instruction is displayed!
        assertThat(dto.getNote()).isEqualTo("Không tiêu, chín kỹ");
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getItemName()).isEqualTo("Beef Steak");
    }

    @Test
    void testMapToQueueResponseDTO_Round1Ticket_PreservesRound1Dishes() {
        Order order = Order.builder()
                .orderNo("ORD-08112026-001")
                .build();
        order.setId(101L);

        MenuItem chicken = MenuItem.builder()
                .itemName("Grilled Chicken")
                .price(120000.0)
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();
        chicken.setId(1L);

        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .price(180000.0)
                .estimatedPrepTime(Duration.ofMinutes(36))
                .build();
        steak.setId(2L);

        // Accumulated in DB is 2 Steaks, 3 Chickens
        OrderItem itemChicken = OrderItem.builder()
                .order(order)
                .menuItem(chicken)
                .quantity(3)
                .unitPrice("120000")
                .subtotal(360000.0)
                .build();

        OrderItem itemSteak = OrderItem.builder()
                .order(order)
                .menuItem(steak)
                .quantity(2)
                .unitPrice("180000")
                .subtotal(360000.0)
                .build();

        when(orderItemRepository.findAllByOrderId(101L)).thenReturn(List.of(itemChicken, itemSteak));

        // Ticket 1 (Round 1) created with "3x Grilled Chicken, 1x Beef Steak"
        KitchenOrder ticketRound1 = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.SERVED)
                .note("3x Grilled Chicken, 1x Beef Steak")
                .estimatedPrepTimeMinutes(36)
                .build();
        ticketRound1.setId(501L);

        when(kitchenOrderRepository.findKitchenQueue(any(), any(), any()))
                .thenReturn(List.of(ticketRound1));

        List<KitchenQueueResponseDTO> result = kitchenService.getKitchenOrders(OrderKitchenStatus.SERVED, null, null);

        assertThat(result).hasSize(1);
        KitchenQueueResponseDTO dto = result.get(0);
        assertThat(dto.getItems()).hasSize(2);

        var chickenDTO = dto.getItems().stream().filter(i -> i.getItemName().equals("Grilled Chicken")).findFirst().orElseThrow();
        assertThat(chickenDTO.getQuantity()).isEqualTo(3);

        var steakDTO = dto.getItems().stream().filter(i -> i.getItemName().equals("Beef Steak")).findFirst().orElseThrow();
        assertThat(steakDTO.getQuantity()).isEqualTo(1); // exactly 1 from Round 1 note, not 2!
    }

    @Test
    void testMapToQueueResponseDTO_FallbackWhenNoItemsInNote() {
        Order order = Order.builder().orderNo("ORD-001").build();
        order.setId(102L);

        MenuItem salad = MenuItem.builder().itemName("Caesar Salad").price(45000.0).build();
        OrderItem item = OrderItem.builder().order(order).menuItem(salad).quantity(2).unitPrice("45000").subtotal(90000.0).build();

        when(orderItemRepository.findAllByOrderId(102L)).thenReturn(List.of(item));

        KitchenOrder ticket = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.PENDING)
                .note("Table 5 VIP Note Only")
                .build();
        ticket.setId(503L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(List.of(ticket));

        List<KitchenQueueResponseDTO> result = kitchenService.getKitchenOrders(null, null, null);
        assertThat(result.get(0).getItems()).hasSize(1);
        assertThat(result.get(0).getItems().get(0).getItemName()).isEqualTo("Caesar Salad");
        assertThat(result.get(0).getItems().get(0).getQuantity()).isEqualTo(2);
    }
}
