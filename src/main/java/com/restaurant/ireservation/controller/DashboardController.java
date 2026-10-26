package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.KitchenPerformanceAnalyticsDTO;
import com.restaurant.ireservation.dto.response.VipRoomAnalyticsDTO;
import com.restaurant.ireservation.service.KitchenPerformanceAnalyticsService;
import com.restaurant.ireservation.service.VipRoomAnalyticsService;
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
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardController {

    VipRoomAnalyticsService vipRoomAnalyticsService;
    KitchenPerformanceAnalyticsService kitchenPerformanceAnalyticsService;

    // ─── VIP Room Analytics ───────────────────────────────────────────────────
    TableAnalyticsService tableAnalyticsService;

    /**
     * GET /api/v1/dashboard/vip-rooms
     * Trả về analytics tổng hợp cho tất cả phòng VIP:
     * - VIP Room Utilization
     * - Revenue By VIP Room
     * - Booking Frequency
     */
    @GetMapping("/vip-rooms")
    @ApiMessage("Fetch VIP room analytics successfully")
    public ResponseEntity<VipRoomAnalyticsDTO> getVipRoomAnalytics() {
        return ResponseEntity.ok(vipRoomAnalyticsService.getVipRoomAnalytics());

    }


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

    // ─── Kitchen Performance Analytics ────────────────────────────────────────

    /**
     * GET /api/v1/dashboard/kitchen
     * Trả về analytics hiệu suất bếp:
     * - Average Preparation Time
     * - Kitchen Workload
     * - Most Ordered Dishes
     */
    @GetMapping("/kitchen")
    @ApiMessage("Fetch kitchen performance analytics successfully")
    public ResponseEntity<KitchenPerformanceAnalyticsDTO> getKitchenPerformanceAnalytics() {
        return ResponseEntity.ok(kitchenPerformanceAnalyticsService.getKitchenPerformanceAnalytics());
    }
}

