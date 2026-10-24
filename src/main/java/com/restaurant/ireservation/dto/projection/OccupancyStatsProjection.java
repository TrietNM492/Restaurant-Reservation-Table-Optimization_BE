package com.restaurant.ireservation.dto.projection;

public interface OccupancyStatsProjection {

    Long getTotalTables();

    Long getOccupiedTables();

    Long getAvailableTables();

}
