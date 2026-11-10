package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.projection.OccupancyStatsProjection;
import com.restaurant.ireservation.dto.response.OccupancyDashboardResponse;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OccupacyDashboardService {

    private final RestaurantTableRepository restaurantTableRepository;

    @Transactional(readOnly = true)
    public OccupancyDashboardResponse getOccupacyDashboard() {
        OccupancyStatsProjection occupancyStatsProjection =
                restaurantTableRepository.getOccupacyStats(
                        TableStatus.IN_USE,
                        TableStatus.AVAILABLE,
                        TableStatus.LOCKED
                );
        long totalTables = occupancyStatsProjection.getTotalTables() == null ? 0L : occupancyStatsProjection.getTotalTables();
        long occupiedTables = occupancyStatsProjection.getOccupiedTables() == null
                ? 0L : occupancyStatsProjection.getOccupiedTables();

        long availableTables = occupancyStatsProjection.getAvailableTables() == null
                ? 0L : occupancyStatsProjection.getAvailableTables();

        Double occupacyRate = totalTables == 0 ? null : Math.round((double) occupiedTables / totalTables * 10000.0) / 100.0;

        return OccupancyDashboardResponse.toResponse(totalTables, occupiedTables, availableTables, occupacyRate, Instant.now());
    }
}
