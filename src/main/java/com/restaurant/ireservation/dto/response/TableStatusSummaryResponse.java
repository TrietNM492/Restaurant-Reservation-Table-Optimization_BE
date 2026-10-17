package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

/**
 * Module: Table Status Tracking [2]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableStatusSummaryResponse {

    long totalTables;
    long availableTables;
    long reservedTables;
    long inUseTables;
    long cleaningTables;
    long lockedTables;
    Map<TableStatus, Long> statusCounts;
    List<AreaTableStatusSummary> areaSummaries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AreaTableStatusSummary {
        Long areaId;
        String areaCode;
        String areaName;
        long totalTables;
        long availableTables;
        long inUseTables;
        long reservedTables;
        long cleaningTables;
        long lockedTables;
    }
}
