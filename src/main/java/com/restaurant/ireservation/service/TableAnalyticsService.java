package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.response.HourlyPeakDTO;
import com.restaurant.ireservation.dto.response.PartySizeStatDTO;
import com.restaurant.ireservation.dto.response.TableUsageStatDTO;
import com.restaurant.ireservation.dto.response.TableUtilizationDashboardDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 13: Table Utilization Analytics (FR-048)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TableAnalyticsService {

    // Excluded statuses from occupancy calculation
    static final List<ReservationStatus> EXCLUDED_STATUSES = List.of(
            ReservationStatus.CANCELLED
    );

    static final ZoneId RESTAURANT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final double DAILY_OPERATIONAL_HOURS = 12.0; // Restaurant operates 10:00 - 22:00 (12 hrs/day)

    RestaurantTableRepository restaurantTableRepository;
    ReservationTableRepository reservationTableRepository;
    ReservationRepository reservationRepository;

    /**
     * FR-048: Retrieve Table Utilization Analytics Dashboard
     * Defaults to the last 30 days if no date range is provided.
     */
    @Transactional(readOnly = true)
    public TableUtilizationDashboardDTO getTableUtilizationDashboard(LocalDate startDate, LocalDate endDate) {
        // Default to last 30 days if dates are omitted
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(30);
        }

        if (startDate.isAfter(endDate)) {
            throw new RequestInvalidException("startDate cannot be after endDate");
        }

        Instant startInstant = startDate.atStartOfDay(RESTAURANT_ZONE).toInstant();
        Instant endInstant = endDate.atTime(LocalTime.MAX).atZone(RESTAURANT_ZONE).toInstant();
        int daysAnalyzed = Math.max(1, (int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
        double totalAvailableHoursPerTable = daysAnalyzed * DAILY_OPERATIONAL_HOURS;

        // 1. Fetch tables
        List<RestaurantTable> tables = restaurantTableRepository.findAllByOrderByTableNumberAsc();
        int totalTables = tables.size();

        // 2. Fetch reservation table linkages
        List<ReservationTable> bookingLinks = reservationTableRepository.findReservationsInDateRange(
                startInstant, endInstant, EXCLUDED_STATUSES);

        // Group bookings by table ID
        Map<Long, List<ReservationTable>> bookingsByTableId = bookingLinks.stream()
                .filter(rt -> rt.getTable() != null)
                .collect(Collectors.groupingBy(rt -> rt.getTable().getId()));

        // 3. Calculate per-table usage statistics
        List<TableUsageStatDTO> tableStats = new ArrayList<>();
        double sumUtilization = 0.0;
        String mostPopularTableDesc = "None";
        long maxBookings = -1;

        for (RestaurantTable t : tables) {
            List<ReservationTable> tblBookings = bookingsByTableId.getOrDefault(t.getId(), Collections.emptyList());
            long count = tblBookings.size();

            double totalOccupiedHours = 0.0;
            for (ReservationTable rt : tblBookings) {
                Reservation r = rt.getReservation();
                if (r != null && r.getStartTime() != null) {
                    Instant rEnd = r.getEndTime() != null ? r.getEndTime() : r.getStartTime().plus(Duration.ofMinutes(90));
                    long minutes = Math.max(0, Duration.between(r.getStartTime(), rEnd).toMinutes());
                    totalOccupiedHours += (minutes / 60.0);
                }
            }

            double roundedOccupiedHours = Math.round(totalOccupiedHours * 10.0) / 10.0;
            double utilizationPercent = totalAvailableHoursPerTable > 0
                    ? Math.min(100.0, Math.round((totalOccupiedHours / totalAvailableHoursPerTable) * 1000.0) / 10.0)
                    : 0.0;

            sumUtilization += utilizationPercent;

            String areaName = (t.getArea() != null && t.getArea().getName() != null)
                    ? t.getArea().getName().name()
                    : "General";

            if (count > maxBookings && count > 0) {
                maxBookings = count;
                mostPopularTableDesc = String.format("Table #%d (%s - %d bookings)", t.getTableNumber(), areaName, count);
            }

            tableStats.add(TableUsageStatDTO.builder()
                    .tableId(t.getId())
                    .tableNumber(t.getTableNumber())
                    .capacity(t.getCapacity())
                    .areaName(areaName)
                    .totalReservationsCount(count)
                    .totalOccupiedHours(roundedOccupiedHours)
                    .utilizationRatePercent(utilizationPercent)
                    .build());
        }

        double overallUtilizationRate = totalTables > 0
                ? Math.round((sumUtilization / totalTables) * 10.0) / 10.0
                : 0.0;

        // 4. Fetch general reservations for peak hour & party size distributions
        List<Reservation> generalReservations = reservationRepository.findReservationsInDateRange(
                startInstant, endInstant, EXCLUDED_STATUSES);
        long totalReservations = generalReservations.size();

        // 5. Hourly Peak Distribution (Focusing on dining hours 10:00 to 22:00)
        Map<Integer, Long> countByHour = new HashMap<>();
        for (Reservation r : generalReservations) {
            if (r.getStartTime() != null) {
                int hour = ZonedDateTime.ofInstant(r.getStartTime(), RESTAURANT_ZONE).getHour();
                countByHour.put(hour, countByHour.getOrDefault(hour, 0L) + 1);
            }
        }

        List<HourlyPeakDTO> hourlyPeaks = new ArrayList<>();
        String peakHourDesc = "None";
        long maxHourCount = -1;

        for (int h = 10; h <= 22; h++) {
            long count = countByHour.getOrDefault(h, 0L);
            String slot = String.format("%02d:00 - %02d:00", h, h + 1);
            if (count > maxHourCount && count > 0) {
                maxHourCount = count;
                peakHourDesc = String.format("%s (%d bookings)", slot, count);
            }
            hourlyPeaks.add(HourlyPeakDTO.builder()
                    .hour(h)
                    .timeSlot(slot)
                    .reservationCount(count)
                    .build());
        }

        // 6. Party Size Distribution
        Map<PartySize, Long> countByParty = generalReservations.stream()
                .filter(r -> r.getPartySize() != null)
                .collect(Collectors.groupingBy(Reservation::getPartySize, Collectors.counting()));

        List<PartySizeStatDTO> partyStats = new ArrayList<>();
        double totalWeightedHeadcount = 0.0;
        long totalPartyReservations = 0;

        for (PartySize ps : PartySize.values()) {
            long count = countByParty.getOrDefault(ps, 0L);
            double pct = totalReservations > 0
                    ? Math.round((count * 1000.0) / totalReservations) / 10.0
                    : 0.0;

            int headcountWeight = mapPartySizeToHeadcount(ps);
            totalWeightedHeadcount += (count * headcountWeight);
            totalPartyReservations += count;

            partyStats.add(PartySizeStatDTO.builder()
                    .partySize(ps.name())
                    .label(mapPartySizeToLabel(ps))
                    .reservationCount(count)
                    .percentage(pct)
                    .build());
        }

        double avgPartySize = totalPartyReservations > 0
                ? Math.round((totalWeightedHeadcount / totalPartyReservations) * 10.0) / 10.0
                : 0.0;

        log.info("[Table Analytics] Range: {} to {}, totalTables={}, totalReservations={}, overallUtil={}%",
                startDate, endDate, totalTables, totalReservations, overallUtilizationRate);

        return TableUtilizationDashboardDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .daysAnalyzed(daysAnalyzed)
                .totalTables(totalTables)
                .totalReservations(totalReservations)
                .overallUtilizationRate(overallUtilizationRate)
                .averagePartySize(avgPartySize)
                .mostPopularTable(mostPopularTableDesc)
                .peakHour(peakHourDesc)
                .tableUsageStats(tableStats)
                .hourlyPeakDistribution(hourlyPeaks)
                .partySizeDistribution(partyStats)
                .build();
    }

    private int mapPartySizeToHeadcount(PartySize ps) {
        if (ps == null) return 2;
        return switch (ps) {
            case SINGLE -> 1;
            case SMALL -> 3;
            case MEDIUM -> 6;
            case LARGE -> 10;
            case XLARGE -> 16;
        };
    }

    private String mapPartySizeToLabel(PartySize ps) {
        if (ps == null) return "Standard (2-4 guests)";
        return switch (ps) {
            case SINGLE -> "Single (1 guest)";
            case SMALL -> "Small (2-4 guests)";
            case MEDIUM -> "Medium (5-8 guests)";
            case LARGE -> "Large (9-12 guests)";
            case XLARGE -> "X-Large (13+ guests)";
        };
    }
}
