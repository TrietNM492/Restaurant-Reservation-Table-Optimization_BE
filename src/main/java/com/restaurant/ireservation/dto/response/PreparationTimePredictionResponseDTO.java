package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Module 11: Food Preparation Time Prediction Response (FR-044 & FR-045)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreparationTimePredictionResponseDTO {

    // Associated order ID (if provided)
    Long orderId;

    // Total estimated preparation time (in minutes)
    int estimatedMinutes;

    // Estimated timestamp when food will be ready
    Instant estimatedReadyTime;

    // Detailed calculation breakdown
    PreparationTimeBreakdownDTO breakdown;

    // Time at which prediction was calculated
    Instant calculationTimestamp;
}
