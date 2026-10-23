package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.request.OrderItemPredictionRequestDTO;
import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreparationTimeServiceTest {

    @Mock
    KitchenOrderRepository kitchenOrderRepository;

    @Mock
    MenuItemRepository menuItemRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @InjectMocks
    PreparationTimeService service;

    @Test
    @DisplayName("Should throw RequestInvalidException when request is null")
    void predict_nullRequest_throws() {
        assertThrows(RequestInvalidException.class, () -> service.predictPreparationTime(null));
    }

    @Test
    @DisplayName("Should throw RequestInvalidException when neither orderId nor items are provided")
    void predict_emptyRequest_throws() {
        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder().build();
        assertThrows(RequestInvalidException.class, () -> service.predictPreparationTime(req));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when orderId does not exist")
    void predict_orderNotFound_throws() {
        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .orderId(999L)
                .build();
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.predictPreparationTime(req));
    }

    @Test
    @DisplayName("Should throw RequestInvalidException when existing order has zero items")
    void predict_orderHasNoItems_throws() {
        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .orderId(10L)
                .build();
        Order order = Order.builder().orderNo("ORD-001").build();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(10L)).thenReturn(Collections.emptyList());

        assertThrows(RequestInvalidException.class, () -> service.predictPreparationTime(req));
    }

    @Test
    @DisplayName("Should calculate preparation time for pre-order items when kitchen is not busy (Queue = 0)")
    void predict_withItems_kitchenIdle_queueZero() {
        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .estimatedPrepTime(Duration.ofMinutes(25))
                .build();
        steak.setId(1L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertNotNull(res);
        assertEquals(25, res.getEstimatedMinutes());
        assertEquals(25, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(0, res.getBreakdown().getQueueWaitingMinutes());
        assertEquals(0, res.getBreakdown().getHistoricalBiasMinutes());
        assertEquals(3, res.getBreakdown().getParallelKitchenSlots());
        assertEquals("Beef Steak", res.getBreakdown().getLongestDishName());
        assertFalse(res.getBreakdown().isVipPriority());
    }

    @Test
    @DisplayName("Should calculate multi-item dishes with portion scaling (+10% for extra qty) and staging delay (+1.5m)")
    void predict_multiItem_withScaling() {
        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .estimatedPrepTime(Duration.ofMinutes(20))
                .build();
        steak.setId(1L);

        MenuItem salad = MenuItem.builder()
                .itemName("Salad")
                .estimatedPrepTime(Duration.ofMinutes(10))
                .build();
        salad.setId(2L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(salad));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        // Steak qty 3: 20 + 2 * (20 * 0.1) = 24.0 mins
        // Salad qty 1: 10 mins
        // Distinct items = 2 -> overhead +1.5 mins
        // Total base = round(24.0 + 1.5) = 26 mins
        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(3).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertEquals(26, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(26, res.getEstimatedMinutes());
    }

    @Test
    @DisplayName("Should calculate queue wait time when kitchen active orders >= 3 slots")
    void predict_kitchenBusy_queueTimeCalculated() {
        MenuItem pizza = MenuItem.builder()
                .itemName("Pizza")
                .estimatedPrepTime(Duration.ofMinutes(15))
                .build();
        pizza.setId(1L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(pizza));

        // Create 5 active orders (exceeds 3 slots by 2)
        List<KitchenOrder> activeOrders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            activeOrders.add(KitchenOrder.builder().status(OrderKitchenStatus.COOKING).build());
        }
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(activeOrders);
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // queuedBeyondCapacity = 5 - 3 = 2
        // waitMinutes = 5 + (2 * 15) / 3 = 15 mins
        // Total = 15 (base) + 15 (queue) = 30 mins
        assertEquals(15, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(15, res.getBreakdown().getQueueWaitingMinutes());
        assertEquals(30, res.getEstimatedMinutes());
    }

    @Test
    @DisplayName("Should prioritize VIP order in queue waiting time")
    void predict_vipOrder_jumpsQueue() {
        MenuItem pizza = MenuItem.builder()
                .itemName("Pizza")
                .estimatedPrepTime(Duration.ofMinutes(15))
                .build();
        pizza.setId(1L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(pizza));

        Instant now = Instant.now();
        List<KitchenOrder> activeOrders = List.of(
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).estimatedReadyTime(now.plusSeconds(180)).build(), // 3 mins remaining
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).estimatedReadyTime(now.plusSeconds(600)).build(),
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).estimatedReadyTime(now.plusSeconds(900)).build(),
                KitchenOrder.builder().status(OrderKitchenStatus.QUEUED).build()
        );
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(activeOrders);
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build()))
                .isVip(true)
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertTrue(res.getBreakdown().isVipPriority());
        // VIP waits for the earliest ready slot = 3 mins (instead of full queued backlog)
        assertEquals(3, res.getBreakdown().getQueueWaitingMinutes());
        assertEquals(18, res.getEstimatedMinutes()); // 15 + 3
    }

    @Test
    @DisplayName("FR-045: Should calculate for existing orderId and log prediction to KitchenOrder")
    void predict_withOrderId_logsToKitchenOrder() {
        Order order = Order.builder()
                .orderNo("ORD-20261024-001")
                .isVipRoom(true)
                .build();
        order.setId(100L);

        MenuItem soup = MenuItem.builder()
                .itemName("Chicken Soup")
                .estimatedPrepTime(Duration.ofMinutes(12))
                .build();
        soup.setId(5L);

        OrderItem item = OrderItem.builder()
                .order(order)
                .menuItem(soup)
                .quantity(1)
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(item));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        KitchenOrder existingKo = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.PENDING)
                .priority(1)
                .build();
        existingKo.setId(50L);
        when(kitchenOrderRepository.findByOrderId(100L)).thenReturn(Optional.of(existingKo));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .orderId(100L)
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertNotNull(res);
        assertEquals(100L, res.getOrderId());
        assertEquals(12, res.getEstimatedMinutes());
        assertTrue(res.getBreakdown().isVipPriority());

        // Verify FR-045 logging: KitchenOrder is updated with estimatedPrepTimeMinutes and saved
        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository, times(1)).save(captor.capture());
        KitchenOrder savedKo = captor.getValue();
        assertEquals(12, savedKo.getEstimatedPrepTimeMinutes());
        assertNotNull(savedKo.getEstimatedReadyTime());
        assertEquals(3, savedKo.getPriority()); // updated to VIP priority 3
    }

    @Test
    @DisplayName("Should apply historical calibration bias offset from recent completed orders")
    void predict_withHistoricalBiasCorrection() {
        MenuItem steak = MenuItem.builder()
                .itemName("Beef Steak")
                .estimatedPrepTime(Duration.ofMinutes(20))
                .build();
        steak.setId(1L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());

        // Mock 2 completed orders where actual time took 4 minutes longer than estimated (24 mins vs 20 mins)
        Instant baseTime = Instant.now().minus(Duration.ofHours(2));
        KitchenOrder c1 = KitchenOrder.builder()
                .status(OrderKitchenStatus.DONE)
                .estimatedPrepTimeMinutes(20)
                .actualStartTime(baseTime)
                .actualReadyTime(baseTime.plus(Duration.ofMinutes(24))) // +4 mins
                .build();
        KitchenOrder c2 = KitchenOrder.builder()
                .status(OrderKitchenStatus.DONE)
                .estimatedPrepTimeMinutes(15)
                .actualStartTime(baseTime)
                .actualReadyTime(baseTime.plus(Duration.ofMinutes(19))) // +4 mins
                .build();

        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(List.of(c1, c2));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // Bias = +4 mins
        // Total = 20 (base) + 0 (queue) + 4 (bias) = 24 mins
        assertEquals(4, res.getBreakdown().getHistoricalBiasMinutes());
        assertEquals(24, res.getEstimatedMinutes());
    }
}
