package com.restaurant.ireservation.dto.response;

import java.time.Instant;

public record OccupancyDashboardResponse(
        long totalTables,
        long occupiedtables,  // IN_USE
        long availabletables, // AVAILABLE
        Double occupacyRate,
        Instant asOf // thoi diem tao so lieu
) {

    public static OccupancyDashboardResponse toResponse(long totalTables, long occupiedtables, long availabletables, Double occupacyRate, Instant asOf) {
        return new OccupancyDashboardResponse(totalTables, occupiedtables, availabletables, occupacyRate, asOf);
    }
}
