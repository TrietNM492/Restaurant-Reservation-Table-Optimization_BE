package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class KitchenDispatcherService {

    final OrderRepository orderRepository;
    final OrderItemRepository orderItemRepository;
    final KitchenOrderRepository kitchenOrderRepository;
    final PaymentRepository paymentRepository;
    final PreparationTimeService preparationTimeService;

    /**
     * Scheduled job running every 2 minutes to scan eligible orders and dispatch to kitchen
     */
    @Scheduled(cron = "0 */2 * * * *")
    @Transactional
    public void scheduledDispatch() {
        log.info("[KitchenDispatcher] Running scheduled dispatch check...");
        int dispatchedCount = dispatchEligibleOrdersToKitchen();
        log.info("[KitchenDispatcher] Scheduled dispatch completed. Dispatched {} orders.", dispatchedCount);
    }

    /**
     * Scans orders in AWAITING_PAYMENT or PENDING that have not yet been sent to the kitchen.
     * Evaluates dynamic prep time estimation and checks if:
     * Instant.now() >= reservation.startTime - estimatedPrepMinutes (or if walk-in / arrival time passed).
     */
    @Transactional
    public int dispatchEligibleOrdersToKitchen() {
        List<Order> candidates = orderRepository.findByOrderStatusIn(
                List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING)
        );

        int dispatchedCount = 0;
        Instant now = Instant.now();

        for (Order order : candidates) {
            List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
            if (items.isEmpty()) {
                continue;
            }

            boolean hasActiveTicket = kitchenOrderRepository.existsByOrderIdAndStatusIn(
                    order.getId(),
                    List.of(
                            OrderKitchenStatus.PENDING,
                            OrderKitchenStatus.COOKING,
                            OrderKitchenStatus.PREPARING,
                            OrderKitchenStatus.QUEUED,
                            OrderKitchenStatus.READY,
                            OrderKitchenStatus.DONE,
                            OrderKitchenStatus.SERVED,
                            OrderKitchenStatus.SERVCED
                    )
            );
            if (hasActiveTicket) {
                continue;
            }

            Reservation res = order.getReservation();
            if (res != null) {
                if (res.getReservationStatus() == ReservationStatus.CANCELLED) {
                    continue;
                }

                Instant startTime = res.getStartTime();
                if (startTime != null) {
                    if (now.isBefore(startTime)) {
                        // Check if cumulative deposit is at least 50% of the current total amount
                        if (paymentRepository != null) {
                            Double alreadyPaidSum = paymentRepository.sumPaidAmountByOrderIdAndStatuses(
                                    order.getId(),
                                    List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID)
                            );
                            double alreadyPaid = (alreadyPaidSum != null) ? alreadyPaidSum : 0.0;

                            int discountRate = resolveDiscountRate(order);
                            double orderTargetTotal = order.getTotalAmount() * (100 - discountRate) / 100.0;
                            double minDepositRequired = orderTargetTotal * 0.50;

                            if (alreadyPaid < minDepositRequired - 1.0) {
                                log.info("[KitchenDispatcher] Order #{} has unpaid deposit for newly added dishes (paid: {}, required min 50%: {}). Holding off dispatch.",
                                        order.getOrderNo(), alreadyPaid, minDepositRequired);
                                continue;
                            }
                        }

                        int prepMinutes = preparationTimeService.estimatePrepMinutes(items, order.isVipRoom());
                        Instant dispatchTime = startTime.minus(Duration.ofMinutes(prepMinutes));
                        if (now.isBefore(dispatchTime)) {
                            log.debug("[KitchenDispatcher] Order #{} not yet ready for dispatch. Dispatch at: {}, Current: {}",
                                    order.getOrderNo(), dispatchTime, now);
                            continue;
                        }
                    }
                }
            }

            dispatchOrder(order, items);
            dispatchedCount++;
        }

        return dispatchedCount;
    }

    /**
     * Directly dispatches an order to the kitchen.
     */
    @Transactional
    public KitchenOrder dispatchOrder(Order order, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            items = orderItemRepository.findAllByOrderId(order.getId());
        }

        int prepMinutes = preparationTimeService.estimatePrepMinutes(items, order.isVipRoom());
        Instant now = Instant.now();
        Instant targetReadyTime;

        if (order.getReservation() != null && order.getReservation().getStartTime() != null) {
            targetReadyTime = order.getReservation().getStartTime();
            if (targetReadyTime.isBefore(now)) {
                targetReadyTime = now.plus(Duration.ofMinutes(prepMinutes));
            }
        } else {
            targetReadyTime = now.plus(Duration.ofMinutes(prepMinutes));
        }

        String note = buildOrderItemsSummary(items);
        String station = order.isVipRoom() ? "VIP_CHEF" : "MAIN_KITCHEN";
        int priority = order.isVipRoom() ? 3 : 1;

        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .order(order)
                .status(OrderKitchenStatus.PENDING)
                .priority(priority)
                .estimatedPrepTimeMinutes(prepMinutes)
                .estimatedReadyTime(targetReadyTime)
                .orderTime(LocalDate.now())
                .kitchenStation(station)
                .note(note)
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(kitchenOrder);
        log.info("[KitchenDispatcher] Dispatched Order #{} to Kitchen as KitchenOrder #{}. Station: {}, Priority: {}, EstPrepTime: {}m, ReadyAt: {}",
                order.getOrderNo(), saved.getId(), station, priority, prepMinutes, targetReadyTime);

        return saved;
    }

    /**
     * Handles customer adding dishes after order was already sent to the kitchen:
     * - If existing ticket is still cooking / not yet served -> append note and extend prep time.
     * - If existing ticket was already SERVED -> create new KitchenOrder #2 at the end of the queue.
     */
    @Transactional
    public void handleAdditionalItemAdded(Order order, OrderItem newItem) {
        Reservation res = order.getReservation();
        Instant now = Instant.now();

        // If before reservation arrival time, newly added items require 50% deposit payment before kitchen dispatch
        if (res != null && res.getStartTime() != null && now.isBefore(res.getStartTime())) {
            log.info("[KitchenDispatcher] Customer added extra dish '{}' before arrival time for Order #{}. Awaiting 50% deposit before kitchen dispatch.",
                    newItem.getMenuItem() != null ? newItem.getMenuItem().getItemName() : newItem.getId(), order.getOrderNo());
            return;
        }

        Optional<KitchenOrder> latestTicketOpt = kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(order.getId());
        if (latestTicketOpt.isEmpty()) {
            return;
        }

        KitchenOrder latestTicket = latestTicketOpt.get();
        String itemDesc = (newItem.getMenuItem() != null ? newItem.getMenuItem().getItemName() : "Dish")
                + " x" + newItem.getQuantity();

        if (latestTicket.getStatus() == OrderKitchenStatus.SERVED || latestTicket.getStatus() == OrderKitchenStatus.SERVCED) {
            int extraPrep = preparationTimeService.resolveMenuItemPrepMinutes(newItem.getMenuItem());

            KitchenOrder round2 = KitchenOrder.builder()
                    .order(order)
                    .status(OrderKitchenStatus.PENDING)
                    .priority(order.isVipRoom() ? 3 : 1)
                    .estimatedPrepTimeMinutes(extraPrep)
                    .estimatedReadyTime(Instant.now().plus(Duration.ofMinutes(extraPrep)))
                    .orderTime(LocalDate.now())
                    .kitchenStation(latestTicket.getKitchenStation())
                    .note("[Gọi thêm - Đợt 2]: " + itemDesc)
                    .build();

            kitchenOrderRepository.save(round2);
            log.info("[KitchenDispatcher] Created Round 2 KitchenOrder #{} for Order #{}: {}",
                    round2.getId(), order.getOrderNo(), itemDesc);
        } else {
            String existingNote = latestTicket.getNote() != null ? latestTicket.getNote() : "";
            String updatedNote = existingNote.isBlank()
                    ? "[Gọi thêm]: " + itemDesc
                    : existingNote + " | [Gọi thêm]: " + itemDesc;
            latestTicket.setNote(updatedNote);

            if (latestTicket.getEstimatedReadyTime() != null) {
                latestTicket.setEstimatedReadyTime(latestTicket.getEstimatedReadyTime().plus(Duration.ofMinutes(8)));
            }
            if (latestTicket.getEstimatedPrepTimeMinutes() != null) {
                latestTicket.setEstimatedPrepTimeMinutes(latestTicket.getEstimatedPrepTimeMinutes() + 8);
            }
            kitchenOrderRepository.save(latestTicket);
            log.info("[KitchenDispatcher] Appended extra dish to existing KitchenOrder #{}: {}",
                    latestTicket.getId(), itemDesc);
        }
    }

    private String buildOrderItemsSummary(List<OrderItem> items) {
        return items.stream()
                .map(item -> {
                    String name = item.getMenuItem() != null ? item.getMenuItem().getItemName() : "Item #" + item.getId();
                    return item.getQuantity() + "x " + name;
                })
                .collect(Collectors.joining(", "));
    }

    private int resolveDiscountRate(Order order) {
        List<Payment> existingPayments = paymentRepository.findByOrderId(order.getId());
        if (existingPayments != null) {
            for (Payment p : existingPayments) {
                if (p.getPaymentStatus() == PaymentStatus.PAID || p.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
                    return p.getDiscountRate();
                }
            }
        }
        if (order.getCustomerInformation() != null && order.getCustomerInformation().getCustomerRank() != null) {
            var rank = order.getCustomerInformation().getCustomerRank();
            if (rank == com.restaurant.ireservation.domain.enumerations.RankCustomer.CAOTHU) return 15;
            if (rank == com.restaurant.ireservation.domain.enumerations.RankCustomer.DIAMOND) return 10;
            if (rank == com.restaurant.ireservation.domain.enumerations.RankCustomer.GOLDEN) return 5;
        }
        return 0;
    }
}
