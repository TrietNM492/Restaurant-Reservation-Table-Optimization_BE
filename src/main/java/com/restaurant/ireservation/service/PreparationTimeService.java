package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.request.OrderItemPredictionRequestDTO;
import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimeBreakdownDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.KitchenOrderRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module 11: Food Preparation Time Estimation (FR-044) & Logging (FR-045)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PreparationTimeService {

    // Hardcoded parallel kitchen cooking stations/slots
    public static final int DEFAULT_PARALLEL_KITCHEN_SLOTS = 3;
    public static final int HOT_STATION_SLOTS = 2;
    public static final int COLD_STATION_SLOTS = 1;
    public static final int BAR_DESSERT_SLOTS = 1;
    public static final int DEFAULT_FALLBACK_PREP_MINUTES = 15;
    public static final int MAX_PREP_MINUTES_CAP = 120;
    public static final int MAX_QUANTITY_CAP = 100;

    // Physical kitchen station identifiers
    public static final String STATION_HOT = "HOT_STATION";
    public static final String STATION_COLD = "COLD_STATION";
    public static final String STATION_BAR = "BAR_DESSERT";

    // Active statuses representing kitchen load
    static final List<OrderKitchenStatus> ACTIVE_STATUSES = List.of(
            OrderKitchenStatus.QUEUED,
            OrderKitchenStatus.PREPARING,
            OrderKitchenStatus.COOKING,
            OrderKitchenStatus.PENDING,
            OrderKitchenStatus.ON_HOLD);

    static final List<OrderKitchenStatus> COMPLETED_STATUSES = List.of(
            OrderKitchenStatus.READY,
            OrderKitchenStatus.DONE,
            OrderKitchenStatus.SERVED,
            OrderKitchenStatus.SERVCED);

    KitchenOrderRepository kitchenOrderRepository;
    MenuItemRepository menuItemRepository;
    OrderRepository orderRepository;
    OrderItemRepository orderItemRepository;

    /**
     * FR-044: Predict food preparation time
     * FR-045: Log prediction to KitchenOrder if orderId is present
     */
    @Transactional
    public PreparationTimePredictionResponseDTO predictPreparationTime(PreparationTimePredictionRequestDTO request) {
        if (request == null) {
            throw new RequestInvalidException("Request cannot be null");
        }

        Long orderId = request.getOrderId();
        List<OrderItemPredictionRequestDTO> requestItems = request.getItems();

        if (orderId == null && (requestItems == null || requestItems.isEmpty())) {
            throw new RequestInvalidException("Either orderId or a non-empty items list must be provided.");
        }

        Order targetOrder = null;
        if (orderId != null) {
            targetOrder = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        }

        // 1. Resolve items, stations, and VIP status
        List<DishItemDetail> dishItems = new ArrayList<>();
        boolean isVip = Boolean.TRUE.equals(request.getIsVip())
                || (targetOrder != null && targetOrder.isVipRoom());

        if (requestItems != null && !requestItems.isEmpty()) {
            for (OrderItemPredictionRequestDTO itemDto : requestItems) {
                if (itemDto.getMenuItemId() == null) {
                    throw new RequestInvalidException("menuItemId in items list cannot be null");
                }
                MenuItem mi = menuItemRepository.findById(itemDto.getMenuItemId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Menu item not found with ID: " + itemDto.getMenuItemId()));

                int prepTime = resolveMenuItemPrepMinutes(mi);
                int rawQty = (itemDto.getQuantity() != null && itemDto.getQuantity() > 0) ? itemDto.getQuantity() : 1;
                int qty = Math.min(MAX_QUANTITY_CAP, Math.max(1, rawQty));
                String station = resolveStation(mi);
                dishItems.add(new DishItemDetail(mi.getItemName(), qty, prepTime, station));
            }
        } else {
            // orderId was validated above
            List<OrderItem> existingOrderItems = orderItemRepository.findAllByOrderId(orderId);
            if (existingOrderItems.isEmpty()) {
                throw new RequestInvalidException("Order has no items to calculate preparation time.");
            }

            for (OrderItem oi : existingOrderItems) {
                MenuItem mi = oi.getMenuItem();
                if (mi != null) {
                    int prepTime = resolveMenuItemPrepMinutes(mi);
                    int qty = Math.min(MAX_QUANTITY_CAP, Math.max(1, oi.getQuantity()));
                    String station = resolveStation(mi);
                    dishItems.add(new DishItemDetail(mi.getItemName(), qty, prepTime, station));
                }
            }
        }

        if (dishItems.isEmpty()) {
            throw new RequestInvalidException("No valid dish items found to calculate preparation time.");
        }

        // 2. Base Cooking Time (T_dish) in isolation via 3-Slot Finish-Time Pool
        DishCookingCalculation cookingCalc = calculateBaseCookingMinutes(dishItems);
        int baseCookingMinutes = cookingCalc.baseCookingMinutes();
        String longestDishName = cookingCalc.longestDishName();
        int longestDishMinutes = cookingCalc.longestDishMinutes();

        // 3. Queue Waiting Time (T_queue) & Real-time Timeline Simulation with Active
        // Kitchen Orders (A1: excludes self from queue)
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findActiveKitchenQueue(ACTIVE_STATUSES, null, null);
        int activeCount = 0;
        if (activeOrders != null) {
            activeCount = (int) activeOrders.stream()
                    .filter(ko -> orderId == null || ko.getOrder() == null || !orderId.equals(ko.getOrder().getId()))
                    .count();
        }
        TimelineSimulationResult sim = simulateKitchenTimeline(dishItems, activeOrders, isVip, baseCookingMinutes,
                orderId);

        int queueWaitingMinutes = sim.queueWaitingMinutes();
        int firstDishReadyMinutes = sim.firstDishReadyMinutes();
        String firstDishName = sim.firstDishName();

        // 4. Historical Calibration / Bias (T_bias)
        int historicalBiasMinutes = calculateHistoricalBiasMinutes();

        // 5. Total Estimated Time
        int totalEstimatedMinutes = Math.max(1, baseCookingMinutes + queueWaitingMinutes + historicalBiasMinutes);
        Instant now = Instant.now();
        Instant estimatedReadyTime = now.plus(Duration.ofMinutes(totalEstimatedMinutes));

        // 6. FR-045: Log prediction into KitchenOrder if orderId is provided
        if (orderId != null) {
            logPredictionToKitchenOrder(orderId, totalEstimatedMinutes, estimatedReadyTime, isVip, dishItems);
        }

        PreparationTimeBreakdownDTO breakdown = PreparationTimeBreakdownDTO.builder()
                .baseCookingMinutes(baseCookingMinutes)
                .queueWaitingMinutes(queueWaitingMinutes)
                .historicalBiasMinutes(historicalBiasMinutes)
                .activeKitchenOrdersCount(activeCount)
                .parallelKitchenSlots(DEFAULT_PARALLEL_KITCHEN_SLOTS)
                .isVipPriority(isVip)
                .longestDishName(longestDishName)
                .longestDishMinutes(longestDishMinutes)
                .firstDishReadyMinutes(firstDishReadyMinutes)
                .firstDishName(firstDishName)
                .build();

        log.info(
                "[PrepTime Estimation] orderId={}, totalEstimated={}m (base={}m, firstReady={}m [{}], queue={}m, bias={}m, vip={})",
                orderId, totalEstimatedMinutes, baseCookingMinutes, firstDishReadyMinutes, firstDishName,
                queueWaitingMinutes, historicalBiasMinutes, isVip);

        return PreparationTimePredictionResponseDTO.builder()
                .orderId(orderId)
                .estimatedMinutes(totalEstimatedMinutes)
                .estimatedReadyTime(estimatedReadyTime)
                .breakdown(breakdown)
                .calculationTimestamp(now)
                .build();
    }

    /**
     * Reusable helper to estimate prep time in minutes without persisting a
     * KitchenOrder.
     * Incorporates base cooking duration, 3 parallel stations queue wait, VIP jump,
     * and historical bias calibration.
     */
    @Transactional(readOnly = true)
    public int estimatePrepMinutes(List<OrderItem> items, boolean isVip) {
        if (items == null || items.isEmpty()) {
            return DEFAULT_FALLBACK_PREP_MINUTES;
        }

        List<DishItemDetail> dishItems = new ArrayList<>();

        for (OrderItem oi : items) {
            MenuItem mi = oi.getMenuItem();
            if (mi != null) {
                int prepTime = resolveMenuItemPrepMinutes(mi);
                int qty = Math.min(MAX_QUANTITY_CAP, Math.max(1, oi.getQuantity()));
                String station = resolveStation(mi);
                dishItems.add(new DishItemDetail(mi.getItemName(), qty, prepTime, station));
            }
        }

        if (dishItems.isEmpty()) {
            return DEFAULT_FALLBACK_PREP_MINUTES;
        }

        DishCookingCalculation cookingCalc = calculateBaseCookingMinutes(dishItems);
        int baseCookingMinutes = cookingCalc.baseCookingMinutes();

        List<KitchenOrder> activeOrders = kitchenOrderRepository.findActiveKitchenQueue(ACTIVE_STATUSES, null, null);
        TimelineSimulationResult sim = simulateKitchenTimeline(dishItems, activeOrders, isVip, baseCookingMinutes,
                null);
        int historicalBiasMinutes = calculateHistoricalBiasMinutes();

        return Math.max(1, baseCookingMinutes + sim.queueWaitingMinutes() + historicalBiasMinutes);
    }

    public int resolveMenuItemPrepMinutes(MenuItem mi) {
        if (mi != null && mi.getEstimatedPrepTime() != null) {
            long seconds = mi.getEstimatedPrepTime().toSeconds();
            if (seconds > 0) {
                int minutes = (int) Math.ceil(seconds / 60.0);
                if (minutes > MAX_PREP_MINUTES_CAP) {
                    log.warn("[PrepTime Estimation] Prep time for menu item #{} clamped from {}m to max {}m",
                            mi.getId(), minutes, MAX_PREP_MINUTES_CAP);
                    return MAX_PREP_MINUTES_CAP;
                }
                return Math.max(1, minutes);
            }
        }
        log.warn("[PrepTime Estimation] Menu item {} has no valid prep time, using fallback {}m",
                mi != null ? mi.getId() : "null", DEFAULT_FALLBACK_PREP_MINUTES);
        return DEFAULT_FALLBACK_PREP_MINUTES;
    }

    public String resolveStation(MenuItem mi) {
        if (mi == null) {
            return STATION_HOT;
        }
        MenuCategory cat = mi.getCategory();
        if (cat != null) {
            String code = cat.getCode() != null ? cat.getCode().trim().toUpperCase(Locale.ENGLISH) : "";
            String name = cat.getName() != null ? cat.getName().trim().toLowerCase(Locale.ENGLISH) : "";

            // C1: Tokenize category name to match exact whole words (prevents "Barbecue"
            // matching "bar", "Swine" matching "wine")
            Set<String> tokens = Arrays.stream(name.split("[^a-zA-Z0-9]+"))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());

            // 1. Direct Category Codes
            if ("DES".equals(code) || "BEV".equals(code) || "BAR".equals(code)) {
                return STATION_BAR;
            }
            if ("APP".equals(code) || "STARTER".equals(code)) {
                if (mi.getItemName() != null && hasHotDishKeywords(mi.getItemName())) {
                    return STATION_HOT;
                }
                return STATION_COLD;
            }
            if ("MAIN".equals(code) || "HOT".equals(code) || "GRILL".equals(code)) {
                return STATION_HOT;
            }

            // 2. Token-based Category Matching (prioritize Hot cooking over beverage tags)
            if (tokens.contains("main") || tokens.contains("mains") || tokens.contains("grill")
                    || tokens.contains("barbecue") || tokens.contains("bbq") || tokens.contains("entree")
                    || tokens.contains("pasta") || tokens.contains("pizza") || tokens.contains("seafood")
                    || tokens.contains("steak") || tokens.contains("steaks") || tokens.contains("meat")) {
                return STATION_HOT;
            }
            if (tokens.contains("appetizer") || tokens.contains("appetizers") || tokens.contains("starter")
                    || tokens.contains("starters") || tokens.contains("salad") || tokens.contains("salads")
                    || tokens.contains("cold") || tokens.contains("soup") || tokens.contains("soups")) {
                if (mi.getItemName() != null && hasHotDishKeywords(mi.getItemName())) {
                    return STATION_HOT;
                }
                return STATION_COLD;
            }
            if (tokens.contains("dessert") || tokens.contains("desserts") || tokens.contains("sweet")
                    || tokens.contains("sweets") || tokens.contains("drink") || tokens.contains("drinks")
                    || tokens.contains("beverage") || tokens.contains("beverages") || tokens.contains("bar")
                    || tokens.contains("wine") || tokens.contains("cocktail") || tokens.contains("cocktails")
                    || tokens.contains("coffee") || tokens.contains("tea") || tokens.contains("beer")) {
                return STATION_BAR;
            }
        }
        return resolveStationFromName(mi.getItemName());
    }

    private boolean hasHotDishKeywords(String dishName) {
        if (dishName == null || dishName.isBlank())
            return false;
        String normalized = dishName.toLowerCase(Locale.ENGLISH).replace('-', ' ').replace('_', ' ');
        return matchesWord(normalized, "fried", "wings", "grilled", "grill", "bbq", "barbecue",
                "roast", "roasted", "baked", "hot", "crispy", "calamari", "tempura", "crab cake",
                "fish cake", "egg roll", "battered", "braised", "seared");
    }

    public String resolveStationFromName(String dishName) {
        if (dishName == null || dishName.isBlank()) {
            return STATION_HOT;
        }
        String normalized = dishName.toLowerCase(Locale.ENGLISH)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        // 1. Hot exceptions containing words like "cake", "roll", "tea", "wings" (C6 /
        // S2)
        if (matchesWord(normalized, "crab cake", "crab cakes", "fish cake", "fish cakes", "potato cake",
                "egg roll", "egg rolls", "fried roll", "fried rolls", "fried spring roll", "spring roll fried",
                "calamari", "tempura", "hot wings", "chicken wings", "fried chicken", "fried wings")) {
            return STATION_HOT;
        }

        // 2. Sweet pastries containing "roll"
        if (matchesWord(normalized, "cinnamon roll", "cinnamon rolls", "sweet roll", "sweet rolls")) {
            return STATION_BAR;
        }

        // 3. Raw / Cold delicacies (tartare, carpaccio, ceviche, poke, sashimi, nigiri)
        if (matchesWord(normalized, "tartare", "carpaccio", "ceviche", "poke", "sashimi", "nigiri",
                "bruschetta", "cold cut", "cold cuts", "hummus")) {
            return STATION_COLD;
        }

        // 4. Salads take precedence over protein ingredient (e.g. "Chicken Salad",
        // "Salmon Salad" -> COLD)
        if (matchesWord(normalized, "salad", "salads")) {
            return STATION_COLD;
        }

        // 5. Hot mains & hot cooking verbs take precedence over beverage/sauce
        // ingredients
        // (e.g. "Braised Beef in Red Wine", "Beer-Battered Fish", "Coffee-Rubbed
        // Steak", "Tea-Smoked Duck" -> HOT)
        if (matchesWord(normalized, "steak", "steaks", "beef", "chicken", "pork", "lamb", "duck", "salmon",
                "fish", "shrimp", "burger", "burgers", "pizza", "noodle", "noodles", "pasta", "curry", "stew",
                "hotpot", "wings", "ribs", "meat", "braised", "braise", "battered", "grilled", "grill",
                "bbq", "barbecue", "roasted", "roast", "fried", "crispy", "baked", "tea smoked", "pan seared",
                "seared")) {
            return STATION_HOT;
        }

        // 6. Bar & Desserts (Beverages, ice cream, cakes, coffee, tea)
        if (matchesWord(normalized, "ice cream", "sundae", "gelato", "pudding", "tiramisu", "cake", "cakes",
                "cheesecake", "panna cotta", "flan", "waffle", "waffles", "crepe", "crepes", "brownie",
                "brownies", "dessert", "desserts", "smoothie", "smoothies", "shake", "shakes", "milkshake",
                "cocktail", "cocktails", "mocktail", "mocktails", "wine", "beer", "juice", "lemonade",
                "soda", "coffee", "espresso", "latte", "cappuccino", "tea", "boba", "drink", "drinks",
                "beverage", "beverages", "water")) {
            return STATION_BAR;
        }

        // 7. Cold starters, spring rolls, rolls, appetizers
        if (matchesWord(normalized, "spring roll", "spring rolls", "summer roll", "summer rolls",
                "appetizer", "appetizers", "starter", "starters", "roll", "rolls")) {
            return STATION_COLD;
        }

        return STATION_HOT;
    }

    public static boolean matchesWord(String text, String... keywords) {
        if (text == null || text.isBlank())
            return false;
        String padded = " " + text.toLowerCase(Locale.ENGLISH).trim() + " ";
        for (String kw : keywords) {
            String lowerKw = kw.toLowerCase(Locale.ENGLISH).trim();
            if (padded.contains(" " + lowerKw + " ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Option A: Station-Isolated Multi-Slot Finish-Time Pool (LPT Scheduling).
     * Tasks are routed to their dedicated physical station:
     * - HOT_STATION: 2 slots (Stoves, Grills, Fryers)
     * - COLD_STATION: 1 slot (Salad & Cold Starters)
     * - BAR_DESSERT: 1 slot (Beverages & Desserts)
     * Stations work concurrently; total order makespan is max(time_hot, time_cold,
     * time_bar).
     */
    DishCookingCalculation calculateBaseCookingMinutes(List<DishItemDetail> dishItems) {
        if (dishItems == null || dishItems.isEmpty()) {
            return new DishCookingCalculation(DEFAULT_FALLBACK_PREP_MINUTES, "Standard Dish",
                    DEFAULT_FALLBACK_PREP_MINUTES, DEFAULT_FALLBACK_PREP_MINUTES, "Standard Dish");
        }

        List<CookingTask> hotTasks = new ArrayList<>();
        List<CookingTask> coldTasks = new ArrayList<>();
        List<CookingTask> barTasks = new ArrayList<>();

        String longestDishName = dishItems.get(0).dishName();
        int longestDishMinutes = 0;

        for (DishItemDetail item : dishItems) {
            if (item.baseMinutes() > longestDishMinutes) {
                longestDishMinutes = item.baseMinutes();
                longestDishName = item.dishName();
            }
            int effectiveQty = Math.min(MAX_QUANTITY_CAP, Math.max(1, item.quantity()));
            for (int i = 0; i < effectiveQty; i++) {
                CookingTask task = new CookingTask(item.dishName(), item.baseMinutes(), item.station());
                if (STATION_HOT.equals(item.station())) {
                    hotTasks.add(task);
                } else if (STATION_COLD.equals(item.station())) {
                    coldTasks.add(task);
                } else {
                    barTasks.add(task);
                }
            }
        }

        StationScheduleResult hotResult = scheduleStationTasks(hotTasks, HOT_STATION_SLOTS);
        StationScheduleResult coldResult = scheduleStationTasks(coldTasks, COLD_STATION_SLOTS);
        StationScheduleResult barResult = scheduleStationTasks(barTasks, BAR_DESSERT_SLOTS);

        int baseCookingMinutes = Math.max(hotResult.makespan(), Math.max(coldResult.makespan(), barResult.makespan()));
        if (baseCookingMinutes <= 0) {
            baseCookingMinutes = DEFAULT_FALLBACK_PREP_MINUTES;
        }

        // Identify the first dish ready across all physical stations
        int minFinishTime = Integer.MAX_VALUE;
        String firstDishName = longestDishName;

        if (hotResult.firstDishTime() > 0 && hotResult.firstDishTime() < minFinishTime) {
            minFinishTime = hotResult.firstDishTime();
            firstDishName = hotResult.firstDishName();
        }
        if (coldResult.firstDishTime() > 0 && coldResult.firstDishTime() < minFinishTime) {
            minFinishTime = coldResult.firstDishTime();
            firstDishName = coldResult.firstDishName();
        }
        if (barResult.firstDishTime() > 0 && barResult.firstDishTime() < minFinishTime) {
            minFinishTime = barResult.firstDishTime();
            firstDishName = barResult.firstDishName();
        }

        int firstReadyMinutes = (minFinishTime == Integer.MAX_VALUE) ? baseCookingMinutes : Math.max(1, minFinishTime);

        return new DishCookingCalculation(baseCookingMinutes, longestDishName, longestDishMinutes, firstReadyMinutes,
                firstDishName);
    }

    record StationScheduleResult(int makespan, int firstDishTime, String firstDishName) {
    }

    private StationScheduleResult scheduleStationTasks(List<CookingTask> tasks, int slotCount) {
        if (tasks == null || tasks.isEmpty()) {
            return new StationScheduleResult(0, Integer.MAX_VALUE, "");
        }
        // LPT (Longest Processing Time First) on station's specific slots
        tasks.sort((a, b) -> Integer.compare(b.duration(), a.duration()));

        int[] slots = new int[slotCount];
        int minFinishTime = Integer.MAX_VALUE;
        String firstDishName = tasks.get(0).dishName();

        for (CookingTask task : tasks) {
            int bestSlotIdx = 0;
            int minSlotTime = slots[0];
            for (int s = 1; s < slotCount; s++) {
                if (slots[s] < minSlotTime) {
                    minSlotTime = slots[s];
                    bestSlotIdx = s;
                }
            }

            int finishTime = slots[bestSlotIdx] + task.duration();
            slots[bestSlotIdx] = finishTime;

            if (finishTime < minFinishTime) {
                minFinishTime = finishTime;
                firstDishName = task.dishName();
            }
        }

        int makespan = Arrays.stream(slots).max().orElse(0);
        return new StationScheduleResult(makespan, minFinishTime, firstDishName);
    }

    record StationWaitTimes(int waitHot, int waitCold, int waitBar, int genericSlotWait) {
    }

    private StationWaitTimes calculateStationWaitTimes(List<KitchenOrder> activeOrders, boolean isVip,
            Long currentOrderId) {
        if (activeOrders == null || activeOrders.isEmpty()) {
            return new StationWaitTimes(0, 0, 0, 0);
        }

        // A1: Exclude self (the order currently being estimated) from active kitchen
        // queue
        List<KitchenOrder> queueToConsider = activeOrders.stream()
                .filter(ko -> currentOrderId == null || ko.getOrder() == null
                        || !currentOrderId.equals(ko.getOrder().getId()))
                .toList();

        if (queueToConsider.isEmpty()) {
            return new StationWaitTimes(0, 0, 0, 0);
        }

        Instant now = Instant.now();
        // D1: Two-pass accumulation to guarantee list order-invariance
        // Pass 1: Active cooking on line (Max of remaining cooking times across
        // in-flight orders)
        int maxCookingHot = 0;
        int maxCookingCold = 0;
        int maxCookingBar = 0;

        // Pass 2: Queued/pending orders waiting to start (Sum of total workloads)
        int sumQueuedHot = 0;
        int sumQueuedCold = 0;
        int sumQueuedBar = 0;

        int[] genericSlots = new int[DEFAULT_PARALLEL_KITCHEN_SLOTS];

        for (KitchenOrder ko : queueToConsider) {
            // VIP skips queued/pending/on-hold orders that have not started cooking yet
            if (isVip && (ko.getStatus() == OrderKitchenStatus.QUEUED
                    || ko.getStatus() == OrderKitchenStatus.PENDING
                    || ko.getStatus() == OrderKitchenStatus.ON_HOLD)) {
                continue;
            }

            boolean isCooking = (ko.getStatus() == OrderKitchenStatus.COOKING
                    || ko.getStatus() == OrderKitchenStatus.PREPARING);

            List<OrderItem> activeItems = (ko.getOrder() != null && ko.getOrder().getId() != null)
                    ? orderItemRepository.findAllByOrderId(ko.getOrder().getId())
                    : Collections.emptyList();

            if (!activeItems.isEmpty()) {
                List<DishItemDetail> hotDetails = new ArrayList<>();
                List<DishItemDetail> coldDetails = new ArrayList<>();
                List<DishItemDetail> barDetails = new ArrayList<>();

                for (OrderItem oi : activeItems) {
                    if (oi.getMenuItem() != null) {
                        String st = resolveStation(oi.getMenuItem());
                        String name = oi.getMenuItem().getItemName() != null ? oi.getMenuItem().getItemName() : "Dish";
                        int prep = resolveMenuItemPrepMinutes(oi.getMenuItem());
                        int qty = Math.min(MAX_QUANTITY_CAP, Math.max(1, oi.getQuantity()));

                        DishItemDetail detail = new DishItemDetail(name, qty, prep, st);
                        if (STATION_HOT.equals(st)) {
                            hotDetails.add(detail);
                        } else if (STATION_COLD.equals(st)) {
                            coldDetails.add(detail);
                        } else if (STATION_BAR.equals(st)) {
                            barDetails.add(detail);
                        }
                    }
                }

                int workHot = !hotDetails.isEmpty() ? calculateBaseCookingMinutes(hotDetails).baseCookingMinutes() : 0;
                int workCold = !coldDetails.isEmpty() ? calculateBaseCookingMinutes(coldDetails).baseCookingMinutes()
                        : 0;
                int workBar = !barDetails.isEmpty() ? calculateBaseCookingMinutes(barDetails).baseCookingMinutes() : 0;

                int remMinutes;
                if (ko.getEstimatedReadyTime() != null) {
                    long remSec = Duration.between(now, ko.getEstimatedReadyTime()).toSeconds();
                    remMinutes = Math.max(1, (int) Math.ceil(remSec / 60.0));
                } else {
                    remMinutes = (ko.getEstimatedPrepTimeMinutes() != null && ko.getEstimatedPrepTimeMinutes() > 0)
                            ? ko.getEstimatedPrepTimeMinutes()
                            : DEFAULT_FALLBACK_PREP_MINUTES;
                }

                if (isCooking) {
                    long elapsed = (ko.getActualStartTime() != null)
                            ? Math.max(0, Duration.between(ko.getActualStartTime(), now).toMinutes())
                            : 0;
                    // D2: If overdue, ensure at least 2 minutes minimum equipment reservation
                    int remHot = (workHot > 0) ? Math.max(2, workHot - (int) elapsed) : 0;
                    int remCold = (workCold > 0) ? Math.max(2, workCold - (int) elapsed) : 0;
                    int remBar = (workBar > 0) ? Math.max(2, workBar - (int) elapsed) : 0;

                    maxCookingHot = Math.max(maxCookingHot, remHot);
                    maxCookingCold = Math.max(maxCookingCold, remCold);
                    maxCookingBar = Math.max(maxCookingBar, remBar);
                } else {
                    // D3: Queued/pending orders have not started cooking yet; bounded by scheduled
                    // order duration
                    int remHot = Math.min(workHot, remMinutes);
                    int remCold = Math.min(workCold, remMinutes);
                    int remBar = Math.min(workBar, remMinutes);

                    sumQueuedHot += remHot;
                    sumQueuedCold += remCold;
                    sumQueuedBar += remBar;
                }
            } else {
                // Fallback using kitchenStation
                String st = ko.getKitchenStation();
                int remMinutes;
                if (ko.getEstimatedReadyTime() != null) {
                    long remSec = Duration.between(now, ko.getEstimatedReadyTime()).toSeconds();
                    remMinutes = Math.max(1, (int) Math.ceil(remSec / 60.0));
                } else {
                    remMinutes = (ko.getEstimatedPrepTimeMinutes() != null && ko.getEstimatedPrepTimeMinutes() > 0)
                            ? ko.getEstimatedPrepTimeMinutes()
                            : DEFAULT_FALLBACK_PREP_MINUTES;
                }

                if (STATION_BAR.equals(st)) {
                    if (isCooking)
                        maxCookingBar = Math.max(maxCookingBar, remMinutes);
                    else
                        sumQueuedBar += remMinutes;
                } else if (STATION_COLD.equals(st)) {
                    if (isCooking)
                        maxCookingCold = Math.max(maxCookingCold, remMinutes);
                    else
                        sumQueuedCold += remMinutes;
                } else if (STATION_HOT.equals(st) || "GRILL".equalsIgnoreCase(st) || "MAIN_KITCHEN".equalsIgnoreCase(st)
                        || "VIP_CHEF".equalsIgnoreCase(st)) {
                    if (isCooking)
                        maxCookingHot = Math.max(maxCookingHot, remMinutes);
                    else
                        sumQueuedHot += remMinutes;
                } else {
                    // Fallback (R2): null or unrecognized station routed to HOT_STATION (primary
                    // kitchen)
                    if (isCooking)
                        maxCookingHot = Math.max(maxCookingHot, remMinutes);
                    else
                        sumQueuedHot += remMinutes;
                }
            }
        }

        int waitHot = maxCookingHot + sumQueuedHot;
        int waitCold = maxCookingCold + sumQueuedCold;
        int waitBar = maxCookingBar + sumQueuedBar;

        // D9: Use stream min instead of hardcoded genericSlots[0..2]
        int genericSlotWait = Arrays.stream(genericSlots).min().orElse(0);

        if (isVip) {
            waitHot = Math.min(waitHot, 3);
            waitCold = Math.min(waitCold, 3);
            waitBar = Math.min(waitBar, 3);
            genericSlotWait = Math.min(genericSlotWait, 3);
        }

        return new StationWaitTimes(waitHot, waitCold, waitBar, genericSlotWait);
    }

    /**
     * Pillar 3 & 4: Station-Aware Real-time Timeline & Multi-Slot Queue Simulation.
     * Accurately tracks workload across physical stations (HOT_STATION,
     * COLD_STATION, BAR_DESSERT)
     * using the 3 cooking slots. Accounts for multi-item orders in the queue (e.g.
     * 50 ice creams)
     * and correctly calculates when the first dish is ready and when the whole
     * order finishes.
     */
    TimelineSimulationResult simulateKitchenTimeline(List<DishItemDetail> dishItems,
            List<KitchenOrder> activeOrders,
            boolean isVip,
            int baseCookingMinutes,
            Long currentOrderId) {
        if (dishItems == null || dishItems.isEmpty()) {
            return new TimelineSimulationResult(0, DEFAULT_FALLBACK_PREP_MINUTES, "Standard Dish",
                    DEFAULT_FALLBACK_PREP_MINUTES);
        }

        StationWaitTimes waitTimes = calculateStationWaitTimes(activeOrders, isVip, currentOrderId);

        // Schedule individual dish tasks:
        // Each dish starts after its station's queue backlog is cleared
        int minFinishTime = Integer.MAX_VALUE;
        String firstDishName = dishItems.get(0).dishName();

        for (DishItemDetail item : dishItems) {
            int stationWait;
            if (STATION_HOT.equals(item.station())) {
                stationWait = Math.max(waitTimes.waitHot(), waitTimes.genericSlotWait());
            } else if (STATION_BAR.equals(item.station())) {
                stationWait = Math.max(waitTimes.waitBar(), waitTimes.genericSlotWait());
            } else if (STATION_COLD.equals(item.station())) {
                stationWait = Math.max(waitTimes.waitCold(), waitTimes.genericSlotWait());
            } else {
                stationWait = waitTimes.genericSlotWait();
            }

            int itemFinish = stationWait + item.baseMinutes();
            if (itemFinish < minFinishTime) {
                minFinishTime = itemFinish;
                firstDishName = item.dishName();
            }
        }

        // The maximum station queue wait among required stations determines order queue
        // delay
        int maxStationWait = 0;
        for (DishItemDetail item : dishItems) {
            int w;
            if (STATION_HOT.equals(item.station()))
                w = Math.max(waitTimes.waitHot(), waitTimes.genericSlotWait());
            else if (STATION_BAR.equals(item.station()))
                w = Math.max(waitTimes.waitBar(), waitTimes.genericSlotWait());
            else if (STATION_COLD.equals(item.station()))
                w = Math.max(waitTimes.waitCold(), waitTimes.genericSlotWait());
            else
                w = waitTimes.genericSlotWait();
            if (w > maxStationWait) {
                maxStationWait = w;
            }
        }

        int queueWaitingMinutes = maxStationWait;
        int firstDishReady = Math.max(1, minFinishTime);
        int totalWithQueue = Math.max(1, baseCookingMinutes + queueWaitingMinutes);

        return new TimelineSimulationResult(queueWaitingMinutes, firstDishReady, firstDishName, totalWithQueue);
    }

    record TimelineSimulationResult(int queueWaitingMinutes, int firstDishReadyMinutes,
            String firstDishName, int totalEstimatedMinutes) {
    }

    private int calculateHistoricalBiasMinutes() {
        try {
            List<KitchenOrder> completed = kitchenOrderRepository.findCompletedWithTimestamps(COMPLETED_STATUSES);
            if (completed == null || completed.isEmpty()) {
                return 0;
            }

            // Completed is ordered by actualReadyTime DESC from repository query
            // Filter outliers and anomalies (E4)
            List<KitchenOrder> validSample = completed.stream()
                    .filter(ko -> ko.getActualStartTime() != null && ko.getActualReadyTime() != null
                            && ko.getEstimatedPrepTimeMinutes() != null && ko.getEstimatedPrepTimeMinutes() > 0)
                    .filter(ko -> {
                        long actualMins = Duration.between(ko.getActualStartTime(), ko.getActualReadyTime())
                                .toMinutes();
                        // Outlier filter: must be positive duration and under 180 minutes
                        return actualMins > 0 && actualMins <= 180;
                    })
                    .limit(20)
                    .toList();

            // Require valid historical samples before applying bias
            if (validSample.isEmpty()) {
                return 0;
            }

            double totalBiasMinutes = 0.0;
            for (KitchenOrder ko : validSample) {
                long actualMins = Duration.between(ko.getActualStartTime(), ko.getActualReadyTime()).toMinutes();
                int estimatedMins = ko.getEstimatedPrepTimeMinutes();
                double diff = actualMins - estimatedMins;
                totalBiasMinutes += diff;
            }

            double avgBias = totalBiasMinutes / validSample.size();
            // Clamp bias to [-5, +15] to prevent anomalous swings
            return (int) Math.round(Math.max(-5.0, Math.min(15.0, avgBias)));
        } catch (Exception e) {
            log.warn("Failed to calculate historical bias, defaulting to 0: {}", e.getMessage());
            return 0;
        }
    }

    private void logPredictionToKitchenOrder(Long orderId, int estimatedMinutes, Instant estimatedReadyTime,
            boolean isVip, List<DishItemDetail> dishItems) {
        // A7: Safely query kitchen order; try findFirstByOrderIdOrderByCreatedDateDesc
        // first to avoid race conditions
        Optional<KitchenOrder> koOpt = kitchenOrderRepository.findFirstByOrderIdOrderByCreatedDateDesc(orderId);
        if (koOpt.isEmpty()) {
            try {
                koOpt = kitchenOrderRepository.findByOrderId(orderId);
            } catch (Exception ex) {
                // Ignore fallback exception if duplicate records exist
            }
        }
        KitchenOrder ko;
        String station = isVip ? "VIP_CHEF" : resolvePrimaryStation(dishItems);

        if (koOpt.isPresent()) {
            ko = koOpt.get();
            // A3 & L4: Do NOT overwrite estimated prep time if order is already cooking,
            // preparing, completed, or cancelled
            if (ko.getStatus() == OrderKitchenStatus.COOKING
                    || ko.getStatus() == OrderKitchenStatus.PREPARING
                    || ko.getStatus() == OrderKitchenStatus.CANCELLED
                    || COMPLETED_STATUSES.contains(ko.getStatus())) {
                log.info("[FR-045 Log] KitchenOrder #{} status is {}, skip overwriting prep time.",
                        ko.getId(), ko.getStatus());
                return;
            }
        } else {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order
                    .getOrderStatus() == com.restaurant.ireservation.domain.enumerations.OrderStatus.COMPLETED) {
                log.info("[FR-045 Log] Cannot create KitchenOrder for non-existent or completed Order #{}", orderId);
                return;
            }
            ko = KitchenOrder.builder()
                    .order(order)
                    .status(OrderKitchenStatus.PENDING)
                    .priority(isVip ? 3 : 1)
                    .kitchenStation(station)
                    .build();
        }

        ko.setEstimatedPrepTimeMinutes(estimatedMinutes);
        ko.setEstimatedReadyTime(estimatedReadyTime);
        if (ko.getKitchenStation() == null || "GRILL".equalsIgnoreCase(ko.getKitchenStation())) {
            ko.setKitchenStation(station);
        }
        if (isVip && (ko.getPriority() == null || ko.getPriority() < 3)) {
            ko.setPriority(3);
        }

        kitchenOrderRepository.save(ko);
        log.info(
                "[FR-045 Log] KitchenOrder #{} updated for Order #{}: station={}, estimatedPrepTime={}m, estimatedReadyTime={}",
                ko.getId(), orderId, station, estimatedMinutes, estimatedReadyTime);
    }

    private String resolvePrimaryStation(List<DishItemDetail> dishItems) {
        if (dishItems == null || dishItems.isEmpty()) {
            return STATION_HOT;
        }
        boolean hasHot = dishItems.stream().anyMatch(d -> STATION_HOT.equals(d.station()));
        if (hasHot)
            return STATION_HOT;
        boolean hasCold = dishItems.stream().anyMatch(d -> STATION_COLD.equals(d.station()));
        if (hasCold)
            return STATION_COLD;
        return STATION_BAR;
    }

    record DishItemDetail(String dishName, int quantity, int baseMinutes, String station) {
    }

    record DishCookingCalculation(int baseCookingMinutes, String longestDishName, int longestDishMinutes,
            int firstDishReadyMinutes, String firstDishName) {
    }

    record CookingTask(String dishName, int duration, String station) {
    }
}
