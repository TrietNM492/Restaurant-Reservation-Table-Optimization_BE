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
    /**
     * Other slots (earlier / later start, shorter stay, merged tables) that do work, best first.
     * Empty unless the request asked for them or nothing fit the requested slot.
     */
    List<AlternativeOption> alternatives;
    String message;

    public enum RecommendationType {
        SINGLE_TABLE, TABLE_COMBINATION
    }

    public enum AlternativeType {
        EARLIER_START, LATER_START, SHORTER_DURATION, TABLE_COMBINATION, MORE_TABLES
    }

    /** A relaxed version of the request that does have a table. Use startTime / endTime / tableIds to book it. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AlternativeOption {
        int rank;
        AlternativeType type;
        Instant startTime;
        Instant endTime;
        int durationMinutes;
        /** Start offset versus the request in minutes (negative = earlier). */
        int shiftMinutes;
        /** Stay change versus the request in minutes (negative = shorter). */
        int durationChangeMinutes;
        /** 0 - 100, how little the guest has to give up. */
        double convenienceScore;
        /** 0 - 100, combines convenience and the quality of the table found. */
        double score;
        /** Human readable, e.g. "Starts 30 minutes later than requested". */
        String explanation;
        /** Best table (or combination) available for this alternative slot. */
        RecommendationOption recommendation;
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
