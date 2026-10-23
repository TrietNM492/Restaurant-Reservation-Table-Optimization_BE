package com.restaurant.ireservation.dto.request;

import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Module 11: Food Preparation Time Estimation (FR-044 & FR-045)
 * Supports both pre-order prediction (using items list) and post-order prediction (using orderId).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreparationTimePredictionRequestDTO {

    /**
     * Optional: If specified, fetches items and VIP status from existing Order in DB.
     */
    Long orderId;

    /**
     * Optional: Items list for pre-order calculation (e.g. when customer is browsing menu).
     */
    @Valid
    List<OrderItemPredictionRequestDTO> items;

    /**
     * Optional: Force VIP status priority override (true = VIP, false = normal).
     */
    Boolean isVip;
}
