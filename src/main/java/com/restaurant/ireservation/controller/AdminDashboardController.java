package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.OccupancyDashboardResponse;
import com.restaurant.ireservation.dto.response.ReservationAnalyticsResponse;
import com.restaurant.ireservation.service.OccupacyDashboardService;
import com.restaurant.ireservation.service.ReservationAnalyticsService;
import com.restaurant.ireservation.util.AnalyticsDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OccupacyDashboardService occupacyDashboardService;
    private final ReservationAnalyticsService reservationAnalyticsService;

    @GetMapping("/occupacy")
    public ResponseEntity<OccupancyDashboardResponse> getOccupacyDashboard() {
        return ResponseEntity.ok(occupacyDashboardService.getOccupacyDashboard());
    }

    @GetMapping("/reservations")
    public ResponseEntity<ReservationAnalyticsResponse> getAnalytics(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'from' must be before 'to', and both are required"
            );
        }
        AnalyticsDateRange range = AnalyticsDateRange.fromDates(from, to);

        return ResponseEntity.ok(reservationAnalyticsService.getAnalytics(range.from(), range.to()));
    }


}
