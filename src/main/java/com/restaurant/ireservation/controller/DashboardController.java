package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.TableUtilizationDashboardDTO;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import com.restaurant.ireservation.service.TableAnalyticsService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Module 13: Table Utilization Analytics (FR-048)
 * Restricted to ROLE_ADMIN and ROLE_MANAGER.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardController {

    TableAnalyticsService tableAnalyticsService;

    @GetMapping("/tables")
    @ApiMessage("Fetch table utilization dashboard successfully")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.MANAGER + "')")
    public ResponseEntity<TableUtilizationDashboardDTO> getTableUtilizationDashboard(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(tableAnalyticsService.getTableUtilizationDashboard(startDate, endDate));
    }
}
