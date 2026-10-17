package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableRecommendationRequest {

    @NotNull(message = "Guest count is required")
    @Min(value = 1, message = "Guest count must be at least 1")
    @Max(value = 100, message = "Guest count cannot exceed 100")
    Integer guestCount;

    /** Optional. Defaults to "now" (walk-in). */
    Instant startTime;

    /** Optional. Defaults to 90 minutes. */
    @Min(value = 30, message = "Duration must be at least 30 minutes")
    @Max(value = 360, message = "Duration cannot exceed 360 minutes")
    Integer durationMinutes;

    /** Optional soft preference: tables in this area are ranked higher, others are still returned. */
    Long preferredAreaId;

    /** Optional. Defaults to 5. */
    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 10, message = "maxResults cannot exceed 10")
    Integer maxResults;

    /** Optional. Defaults to true: suggest merging tables of the same area when single tables are not enough. */
    Boolean allowCombination;

    /** Optional. When to suggest alternative slots; defaults to AUTO (only when nothing fits). */
    AlternativeMode alternativeMode;

    /** Optional. Maximum number of alternatives returned. Defaults to 5. */
    @Min(value = 1, message = "maxAlternatives must be at least 1")
    @Max(value = 10, message = "maxAlternatives cannot exceed 10")
    Integer maxAlternatives;

    /** Optional. How far (minutes) the start may move earlier / later. Defaults to 120. */
    @Min(value = 15, message = "maxShiftMinutes must be at least 15")
    @Max(value = 180, message = "maxShiftMinutes cannot exceed 180")
    Integer maxShiftMinutes;

    public enum AlternativeMode {
        /** Suggest alternatives only when no table fits the requested slot. */
        AUTO,
        /** Always suggest alternatives, even when the requested slot has tables. */
        ALWAYS,
        /** Never suggest alternatives. */
        NEVER
    }
}
