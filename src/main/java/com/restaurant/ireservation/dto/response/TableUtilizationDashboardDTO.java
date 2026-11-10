package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

/**
 * Module 13: Table Utilization Analytics Dashboard (FR-048)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableUtilizationDashboardDTO {

    LocalDate startDate;

    LocalDate endDate;

    int daysAnalyzed;

    int totalTables;

    long totalReservations;

    // Overall average utilization rate across all tables (%)
    double overallUtilizationRate;

    // Estimated average party size headcount
    double averagePartySize;

    // Most popular table description (e.g. "Table #4 (Indoor - 18 bookings)")
    String mostPopularTable;

    // Peak hour interval (e.g. "19:00 - 20:00 (25 bookings)")
    String peakHour;

    // Per-table breakdown
    List<TableUsageStatDTO> tableUsageStats;

    // Peak hours breakdown
    List<HourlyPeakDTO> hourlyPeakDistribution;

    // Party size breakdown
    List<PartySizeStatDTO> partySizeDistribution;
}
