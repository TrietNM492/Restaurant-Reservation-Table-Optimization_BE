package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.VipRoomAnalyticsDTO;
import com.restaurant.ireservation.service.VipRoomAnalyticsService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module: Dashboard Analytics
 * Cung cấp các API thống kê tổng hợp cho dashboard quản trị.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardController {

    VipRoomAnalyticsService vipRoomAnalyticsService;

    // ─── VIP Room Analytics ───────────────────────────────────────────────────

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
}
