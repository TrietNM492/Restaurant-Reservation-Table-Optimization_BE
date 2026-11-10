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
    @DisplayName("Option A: Station Isolation - 3 Steaks (20m) on 2 Hot slots (40m) + 1 Salad (10m) on 1 Cold slot takes 40m")
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

        // Option A: Hot station has 2 slots -> 2 Steaks take 20m, 3rd Steak takes another 20m -> 40m.
        // Cold station has 1 slot -> Salad takes 10m.
        // Order makespan = max(40m, 10m) = 40 mins.
        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(3).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertEquals(40, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(40, res.getEstimatedMinutes());
        assertEquals("Beef Steak", res.getBreakdown().getLongestDishName());
        assertEquals(10, res.getBreakdown().getFirstDishReadyMinutes());
    }

    @Test
    @DisplayName("Pillar 3 & 4: Multi-item active order (Steak + Chicken + 50 Ice Creams, 40m left) blocks 2nd order: Queue Delay = 40m, Ice Cream ready at 45m")
    void predict_activeOrderWithFiftyIceCreams_blocksSecondOrderDessert() {
        Order activeOrder = Order.builder().orderNo("ORD-08112026-001").build();
        activeOrder.setId(201L);

        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(36)).build();
        steak.setId(1L);
        MenuItem chicken = MenuItem.builder().itemName("Grilled Chicken").estimatedPrepTime(Duration.ofMinutes(25)).build();
        chicken.setId(2L);
        MenuItem iceCream = MenuItem.builder().itemName("Ice Cream").estimatedPrepTime(Duration.ofMinutes(5)).build();
        iceCream.setId(3L);

        // Active Order 1 items: 1 Steak, 1 Chicken, 50 Ice Creams
        List<OrderItem> activeItems = List.of(
                OrderItem.builder().order(activeOrder).menuItem(steak).quantity(1).build(),
                OrderItem.builder().order(activeOrder).menuItem(chicken).quantity(1).build(),
                OrderItem.builder().order(activeOrder).menuItem(iceCream).quantity(50).build()
        );
        when(orderItemRepository.findAllByOrderId(201L)).thenReturn(activeItems);

        Instant now = Instant.now();
        Instant activeReadyTime = now.plus(Duration.ofMinutes(40)); // 40 minutes remaining
        KitchenOrder activeKo = KitchenOrder.builder()
                .order(activeOrder)
                .status(OrderKitchenStatus.QUEUED)
                .estimatedReadyTime(activeReadyTime)
                .estimatedPrepTimeMinutes(40)
                .build();
        activeKo.setId(501L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(List.of(activeKo));
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        // Account 2 arrives: 1 Grilled Chicken (25m) + 1 Ice Cream (5m)
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(chicken));
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(iceCream));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(3L).quantity(1).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // 1. Queue Delay must be 40m (NOT 0m!)
        assertEquals(40, res.getBreakdown().getQueueWaitingMinutes());
        // 2. Base Cooking in isolation is 25m (Chicken)
        assertEquals(25, res.getBreakdown().getBaseCookingMinutes());
        // 3. Total estimated is 25 + 40 = 65m
        assertEquals(65, res.getEstimatedMinutes());
        // 4. First dish ready is Ice Cream at 40 (queue wait) + 5 (prep) = 45m (NOT 5m!)
        assertEquals(45, res.getBreakdown().getFirstDishReadyMinutes());
        assertEquals("Ice Cream", res.getBreakdown().getFirstDishName());
    }

    @Test
    @DisplayName("Pillar 1: 4 dishes of 30m each on 3 slots must take 60m (resolves Contradiction 1)")
    void predict_fourDishes30m_threeSlots_takes60m() {
        MenuItem dish1 = MenuItem.builder().itemName("Dish 1").estimatedPrepTime(Duration.ofMinutes(30)).build();
        dish1.setId(1L);
        MenuItem dish2 = MenuItem.builder().itemName("Dish 2").estimatedPrepTime(Duration.ofMinutes(30)).build();
        dish2.setId(2L);
        MenuItem dish3 = MenuItem.builder().itemName("Dish 3").estimatedPrepTime(Duration.ofMinutes(30)).build();
        dish3.setId(3L);
        MenuItem dish4 = MenuItem.builder().itemName("Dish 4").estimatedPrepTime(Duration.ofMinutes(30)).build();
        dish4.setId(4L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(dish1));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(dish2));
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(dish3));
        when(menuItemRepository.findById(4L)).thenReturn(Optional.of(dish4));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(3L).quantity(1).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(4L).quantity(1).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // 3 dishes run on 3 slots for 30m, 4th dish waits for 1st slot to free up -> 30 + 30 = 60m
        assertEquals(60, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(60, res.getEstimatedMinutes());
        assertEquals(30, res.getBreakdown().getFirstDishReadyMinutes());
    }

    @Test
    @DisplayName("Option A: 3 Steaks of 36m on 2 Hot slots: first 2 take 36m, 3rd takes 72m total")
    void predict_threeSteaks36m_emptyKitchen_takes72m_optionA() {
        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(36)).build();
        steak.setId(1L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(3).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // 2 steaks cook in parallel on HOT_STATION (2 slots) -> 36m. 3rd steak takes another 36m -> 72m!
        assertEquals(72, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(72, res.getEstimatedMinutes());
        assertEquals(36, res.getBreakdown().getFirstDishReadyMinutes());
    }

    @Test
    @DisplayName("Pillars 1 & 4: 2 Steaks (36m) + 1 Ice Cream (5m) takes 36m total with Ice Cream ready in 5m (resolves Contradiction 3)")
    void predict_twoSteaksAndIceCream_stationDecoupled() {
        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(36)).build();
        steak.setId(1L);
        MenuItem iceCream = MenuItem.builder().itemName("Ice Cream").estimatedPrepTime(Duration.ofMinutes(5)).build();
        iceCream.setId(2L);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(iceCream));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(
                        OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(2).build(),
                        OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build()
                ))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // Steak 1 (36m, slot 0), Steak 2 (36m, slot 1), Ice Cream (5m, slot 2)
        // Makespan = 36m (NOT inflated to 66m!), First dish ready = 5m (Ice Cream)
        assertEquals(36, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(36, res.getEstimatedMinutes());
        assertEquals(5, res.getBreakdown().getFirstDishReadyMinutes());
        assertEquals("Ice Cream", res.getBreakdown().getFirstDishName());
        assertEquals("Beef Steak", res.getBreakdown().getLongestDishName());
    }

    @Test
    @DisplayName("Pillar 3: Dessert-only order is not blocked by active kitchen queue in hot station")
    void predict_dessertOnlyOrder_notBlockedByHotKitchenQueue() {
        MenuItem iceCream = MenuItem.builder().itemName("Ice Cream").estimatedPrepTime(Duration.ofMinutes(5)).build();
        iceCream.setId(2L);

        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(iceCream));

        // 4 active orders currently cooking steaks in HOT_STATION
        List<KitchenOrder> activeOrders = List.of(
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).kitchenStation(PreparationTimeService.STATION_HOT).build(),
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).kitchenStation(PreparationTimeService.STATION_HOT).build(),
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).kitchenStation(PreparationTimeService.STATION_HOT).build(),
                KitchenOrder.builder().status(OrderKitchenStatus.COOKING).kitchenStation(PreparationTimeService.STATION_HOT).build()
        );
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(activeOrders);
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(2L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // BAR_DESSERT station has 0 load -> Queue wait is 0m!
        assertEquals(0, res.getBreakdown().getQueueWaitingMinutes());
        assertEquals(5, res.getBreakdown().getBaseCookingMinutes());
        assertEquals(5, res.getEstimatedMinutes());
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

    @Test
    @DisplayName("Pillar 3 Station Isolation: Order 1 has 1 Steak (36m) + 1 Chicken (25m) + 1 Ice Cream (5m). Order 2 with only 1 Ice Cream waits 5m (NOT 51m)")
    void predict_dessertOrderBehindMixedOrder_waitsOnlyForDessertStationWorkload() {
        Order activeOrder = Order.builder().orderNo("ORD-08112026-003").build();
        activeOrder.setId(301L);

        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(36)).build();
        steak.setId(1L);
        MenuItem chicken = MenuItem.builder().itemName("Grilled Chicken").estimatedPrepTime(Duration.ofMinutes(25)).build();
        chicken.setId(2L);
        MenuItem iceCream = MenuItem.builder().itemName("Ice Cream").estimatedPrepTime(Duration.ofMinutes(5)).build();
        iceCream.setId(3L);

        // Active Order 1 has 1 Steak (36m), 1 Chicken (25m), 1 Ice Cream (5m)
        List<OrderItem> activeItems = List.of(
                OrderItem.builder().order(activeOrder).menuItem(steak).quantity(1).build(),
                OrderItem.builder().order(activeOrder).menuItem(chicken).quantity(1).build(),
                OrderItem.builder().order(activeOrder).menuItem(iceCream).quantity(1).build()
        );
        when(orderItemRepository.findAllByOrderId(301L)).thenReturn(activeItems);

        Instant now = Instant.now();
        Instant activeReadyTime = now.plus(Duration.ofMinutes(51)); // 51 mins total for order 1
        KitchenOrder activeKo = KitchenOrder.builder()
                .order(activeOrder)
                .status(OrderKitchenStatus.QUEUED)
                .estimatedReadyTime(activeReadyTime)
                .estimatedPrepTimeMinutes(51)
                .build();
        activeKo.setId(601L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(List.of(activeKo));
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());

        // Order 2 arrives: only 1 Ice Cream (5m)
        when(menuItemRepository.findById(3L)).thenReturn(Optional.of(iceCream));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(3L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // 1. Queue Delay must only be 5m (the ice cream workload of order 1), NOT 51m!
        assertEquals(5, res.getBreakdown().getQueueWaitingMinutes());
        // 2. Base cooking for ice cream is 5m
        assertEquals(5, res.getBreakdown().getBaseCookingMinutes());
        // 3. First dish ready in 5m (queue) + 5m (prep) = 10m (NOT 56m!)
        assertEquals(10, res.getBreakdown().getFirstDishReadyMinutes());
        assertEquals("Ice Cream", res.getBreakdown().getFirstDishName());
        // 4. Total estimated without peak bias is 5 + 5 = 10m
        assertEquals(10, res.getEstimatedMinutes());
    }

    @Test
    @DisplayName("S1, S2, S3: Ingredient ambiguity resolution - cooking method & main protein win over sauce/drink words")
    void resolveStationFromName_ingredientAmbiguity() {
        // Hot mains with drink/sauce ingredients should route to HOT_STATION, not BAR_DESSERT
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Braised Beef in Red Wine"));
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Beer-Battered Fish"));
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Coffee-Rubbed Steak"));
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Tea-Smoked Duck"));
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Crab Cake"));
        assertEquals(PreparationTimeService.STATION_HOT, service.resolveStationFromName("Fried Chicken Wings"));

        // Cold delicacies with meat names should route to COLD_STATION
        assertEquals(PreparationTimeService.STATION_COLD, service.resolveStationFromName("Beef Carpaccio"));
        assertEquals(PreparationTimeService.STATION_COLD, service.resolveStationFromName("Chicken Salad"));
        assertEquals(PreparationTimeService.STATION_COLD, service.resolveStationFromName("Salmon Tartare"));
        assertEquals(PreparationTimeService.STATION_COLD, service.resolveStationFromName("Fresh Spring Rolls"));

        // Desserts with roll should route to BAR_DESSERT
        assertEquals(PreparationTimeService.STATION_BAR, service.resolveStationFromName("Cinnamon Roll"));
        assertEquals(PreparationTimeService.STATION_BAR, service.resolveStationFromName("Iced Vanilla Latte"));
        assertEquals(PreparationTimeService.STATION_BAR, service.resolveStationFromName("Matcha Boba Tea"));
    }

    @Test
    @DisplayName("R2: Active kitchen order with unknown/null station falls back to HOT station workload")
    void predict_fallbackUnknownStation_routesToHotStation() {
        Order activeOrder = Order.builder().orderNo("ORD-UNKNOWN").build();
        activeOrder.setId(701L);

        Instant now = Instant.now();
        KitchenOrder activeKo = KitchenOrder.builder()
                .order(activeOrder)
                .status(OrderKitchenStatus.QUEUED)
                .estimatedPrepTimeMinutes(15)
                .estimatedReadyTime(now.plus(Duration.ofMinutes(15)))
                .kitchenStation("UNKNOWN_SPECIAL_CORNER")
                .build();
        activeKo.setId(702L);

        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(List.of(activeKo));
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());
        when(orderItemRepository.findAllByOrderId(701L)).thenReturn(Collections.emptyList());

        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(20)).build();
        steak.setId(1L);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(steak));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder()
                .items(List.of(OrderItemPredictionRequestDTO.builder().menuItemId(1L).quantity(1).build()))
                .build();

        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        // Unknown station load routes to HOT_STATION -> steak waits 15m
        assertEquals(15, res.getBreakdown().getQueueWaitingMinutes());
        assertEquals(35, res.getEstimatedMinutes());
    }

    @Test
    @DisplayName("L4: Should skip logging prediction if existing KitchenOrder is CANCELLED")
    void predict_logPrediction_skipsCancelledKitchenOrder() {
        Order order = Order.builder().orderNo("ORD-CANCELLED-KO").build();
        order.setId(800L);

        MenuItem steak = MenuItem.builder().itemName("Beef Steak").estimatedPrepTime(Duration.ofMinutes(20)).build();
        steak.setId(1L);

        OrderItem item = OrderItem.builder().order(order).menuItem(steak).quantity(1).build();

        KitchenOrder cancelledKo = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.CANCELLED)
                .estimatedPrepTimeMinutes(999)
                .build();
        cancelledKo.setId(801L);

        when(orderRepository.findById(800L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(800L)).thenReturn(List.of(item));
        when(kitchenOrderRepository.findActiveKitchenQueue(any(), any(), any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findCompletedWithTimestamps(any())).thenReturn(Collections.emptyList());
        when(kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(800L)).thenReturn(Optional.of(cancelledKo));

        PreparationTimePredictionRequestDTO req = PreparationTimePredictionRequestDTO.builder().orderId(800L).build();
        PreparationTimePredictionResponseDTO res = service.predictPreparationTime(req);

        assertNotNull(res);
        // Verify save was NEVER called because KitchenOrder is CANCELLED
        verify(kitchenOrderRepository, never()).save(any(KitchenOrder.class));
    }
}
