package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module 11: Food Preparation Time Breakdown Details
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreparationTimeBreakdownDTO {

    // Base cooking time based on dish preparation duration and parallelism (minutes)
    int baseCookingMinutes;

    // Waiting time in queue before kitchen starts cooking (minutes)
    int queueWaitingMinutes;

    // Historical dynamic bias/offset calibrated from recent completed orders (minutes)
    int historicalBiasMinutes;

    // Current active orders occupying the kitchen
    int activeKitchenOrdersCount;

    // Number of parallel cooking stations/kitchen capacity (default 3)
    int parallelKitchenSlots;

    // Whether this order receives VIP priority queue handling
    boolean isVipPriority;

    // Dish with the longest preparation time in the order
    String longestDishName;

    // Base minutes of the longest dish
    int longestDishMinutes;

    // Time when the first dish is ready to be served (e.g. appetizer or quick item)
    Integer firstDishReadyMinutes;

    // Name of the first dish that will be served
    String firstDishName;
}
