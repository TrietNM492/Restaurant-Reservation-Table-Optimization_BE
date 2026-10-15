package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableRecommendationResponse {

    int guestCount;
    /** PartySize bucket matching guestCount, ready to be sent to the create-reservation API. */
    PartySize suggestedPartySize;
    Instant startTime;
    /** startTime + duration, ready to be sent to the create-reservation API. */
    Instant endTime;
    int durationMinutes;
    /** Number of tables that are free for the whole requested slot (before capacity filtering). */
    int availableTableCount;
    /** Ordered best-first. Empty when nothing fits (see message). */
    List<RecommendationOption> recommendations;
    String message;

    public enum RecommendationType {
        SINGLE_TABLE, TABLE_COMBINATION
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RecommendationOption {
        int rank;
        RecommendationType type;
        /** Convenience: pass this list directly as tableIds when creating the reservation. */
        List<Long> tableIds;
        List<TableSummary> tables;
        int totalCapacity;
        int spareSeats;
        /** 0 - 100, higher is better. */
        double score;
        ScoreBreakdown scoreBreakdown;
        /** Human-readable explanation for staff / customer. */
        List<String> reasons;
    }

    /** Each component is in the range 0 - 1. {@code area} is null when no preferred area was requested. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ScoreBreakdown {
        double capacityFit;
        double schedulePacking;
        Double areaMatch;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TableSummary {
        Long id;
        Integer tableNumber;
        int capacity;
        Long areaId;
        String areaCode;
        AreaName areaName;
        TableStatus status;
    }
}
