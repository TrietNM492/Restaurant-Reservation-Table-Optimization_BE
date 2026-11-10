package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.response.TableUtilizationDashboardDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableAnalyticsServiceTest {

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @Mock
    ReservationRepository reservationRepository;

    @InjectMocks
    TableAnalyticsService tableAnalyticsService;

    @Test
    @DisplayName("Should default to 30 days when startDate and endDate are omitted")
    void getDashboard_defaultDates_uses30Days() {
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(Collections.emptyList());
        when(reservationTableRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(Collections.emptyList());

        TableUtilizationDashboardDTO dto = tableAnalyticsService.getTableUtilizationDashboard(null, null);

        assertNotNull(dto);
        assertEquals(LocalDate.now(), dto.getEndDate());
        assertEquals(LocalDate.now().minusDays(30), dto.getStartDate());
        assertEquals(31, dto.getDaysAnalyzed());
        assertEquals(0, dto.getTotalTables());
        assertEquals(0L, dto.getTotalReservations());
    }

    @Test
    @DisplayName("Should throw RequestInvalidException when startDate is after endDate")
    void getDashboard_startDateAfterEndDate_throws() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 10);

        assertThrows(RequestInvalidException.class,
                () -> tableAnalyticsService.getTableUtilizationDashboard(start, end));
    }

    @Test
    @DisplayName("Should calculate utilization rate and table statistics accurately")
    void getDashboard_withBookings_calculatesUtilization() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 10); // 10 days -> 120 operational hours per table

        TableArea indoorArea = TableArea.builder().name(AreaName.INDOOR).build();

        RestaurantTable t1 = RestaurantTable.builder()
                .tableNumber(1)
                .capacity(4)
                .area(indoorArea)
                .build();
        t1.setId(10L);

        RestaurantTable t2 = RestaurantTable.builder()
                .tableNumber(2)
                .capacity(2)
                .area(indoorArea)
                .build();
        t2.setId(20L);

        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(t1, t2));

        // Create 2 bookings for Table 1 (each 2 hours -> total 4 hours)
        Instant bookingTime1 = Instant.parse("2026-10-05T11:00:00Z");
        Reservation r1 = Reservation.builder()
                .reservationNo("RES-001")
                .partySize(PartySize.SMALL)
                .startTime(bookingTime1)
                .endTime(bookingTime1.plus(Duration.ofHours(2)))
                .reservationStatus(ReservationStatus.COMPLETED)
                .build();
        r1.setId(101L);

        Instant bookingTime2 = Instant.parse("2026-10-06T12:00:00Z");
        Reservation r2 = Reservation.builder()
                .reservationNo("RES-002")
                .partySize(PartySize.MEDIUM)
                .startTime(bookingTime2)
                .endTime(bookingTime2.plus(Duration.ofHours(2)))
                .reservationStatus(ReservationStatus.COMPLETED)
                .build();
        r2.setId(102L);

        ReservationTable rt1 = ReservationTable.builder().table(t1).reservation(r1).build();
        ReservationTable rt2 = ReservationTable.builder().table(t1).reservation(r2).build();

        when(reservationTableRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(List.of(rt1, rt2));
        when(reservationRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(List.of(r1, r2));

        TableUtilizationDashboardDTO dto = tableAnalyticsService.getTableUtilizationDashboard(start, end);

        assertNotNull(dto);
        assertEquals(2, dto.getTotalTables());
        assertEquals(2L, dto.getTotalReservations());
        assertEquals(10, dto.getDaysAnalyzed());

        // Table 1: 4 hours / 120 available hours = 3.3% utilization
        var t1Stat = dto.getTableUsageStats().stream()
                .filter(s -> s.getTableId().equals(10L))
                .findFirst().orElseThrow();
        assertEquals(2, t1Stat.getTotalReservationsCount());
        assertEquals(4.0, t1Stat.getTotalOccupiedHours());
        assertEquals(3.3, t1Stat.getUtilizationRatePercent());

        // Table 2: 0 hours = 0.0% utilization
        var t2Stat = dto.getTableUsageStats().stream()
                .filter(s -> s.getTableId().equals(20L))
                .findFirst().orElseThrow();
        assertEquals(0, t2Stat.getTotalReservationsCount());
        assertEquals(0.0, t2Stat.getTotalOccupiedHours());
        assertEquals(0.0, t2Stat.getUtilizationRatePercent());

        // Most popular table is Table #1
        assertTrue(dto.getMostPopularTable().contains("Table #1"));
        assertTrue(dto.getMostPopularTable().contains("2 bookings"));
    }

    @Test
    @DisplayName("Should correctly calculate party size distribution and peak hours")
    void getDashboard_partySizeAndPeakHours() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 1);

        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(Collections.emptyList());
        when(reservationTableRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(Collections.emptyList());

        // 3 reservations at 18:00 (Vietnam time = 11:00 UTC) with PartySize SMALL (weight 3)
        // 1 reservation at 19:00 with PartySize LARGE (weight 10)
        Instant time18Utc = Instant.parse("2026-10-01T11:00:00Z"); // 18:00 Vietnam (+7)
        Instant time19Utc = Instant.parse("2026-10-01T12:00:00Z"); // 19:00 Vietnam (+7)

        Reservation r1 = Reservation.builder().partySize(PartySize.SMALL).startTime(time18Utc).build();
        Reservation r2 = Reservation.builder().partySize(PartySize.SMALL).startTime(time18Utc).build();
        Reservation r3 = Reservation.builder().partySize(PartySize.SMALL).startTime(time18Utc).build();
        Reservation r4 = Reservation.builder().partySize(PartySize.LARGE).startTime(time19Utc).build();

        when(reservationRepository.findReservationsInDateRange(any(), any(), any())).thenReturn(List.of(r1, r2, r3, r4));

        TableUtilizationDashboardDTO dto = tableAnalyticsService.getTableUtilizationDashboard(start, end);

        assertNotNull(dto);
        assertEquals(4L, dto.getTotalReservations());

        // Peak hour should be 18:00 - 19:00 (3 bookings)
        assertTrue(dto.getPeakHour().contains("18:00 - 19:00"));
        assertTrue(dto.getPeakHour().contains("3 bookings"));

        // Average party size = (3 * 3 + 1 * 10) / 4 = 19 / 4 = 4.75 -> rounded to 4.8
        assertEquals(4.8, dto.getAveragePartySize());

        // Verify SMALL party size stat: 3/4 = 75.0%
        var smallStat = dto.getPartySizeDistribution().stream()
                .filter(p -> "SMALL".equals(p.getPartySize()))
                .findFirst().orElseThrow();
        assertEquals(3L, smallStat.getReservationCount());
        assertEquals(75.0, smallStat.getPercentage());
    }
}
