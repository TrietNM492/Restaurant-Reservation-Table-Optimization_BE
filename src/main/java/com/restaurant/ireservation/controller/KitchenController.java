package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.request.KitchenStatusUpdateRequest;
import com.restaurant.ireservation.dto.response.KitchenDashboardDTO;
import com.restaurant.ireservation.dto.response.KitchenQueueResponseDTO;
import com.restaurant.ireservation.service.KitchenService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module: Kitchen Queue Tracking [3] & Kitchen Status Update [2] & Kitchen Load Monitoring [2]
 */
@RestController
@RequestMapping("/api/v1/kitchen")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KitchenController {

    KitchenService kitchenService;

    // ─── Kitchen Queue Tracking [3] ───────────────────────────────────────────

    @GetMapping("/orders")
    @ApiMessage("Fetch kitchen orders successfully")
    public ResponseEntity<List<KitchenQueueResponseDTO>> getKitchenOrders(
            @RequestParam(name = "status", required = false) OrderKitchenStatus status,
            @RequestParam(name = "isVip", required = false) Boolean isVip,
            @RequestParam(name = "search", required = false) String search
    ) {
        return ResponseEntity.ok(kitchenService.getKitchenOrders(status, isVip, search));
    }

    @GetMapping("/orders/{id}")
    @ApiMessage("Fetch kitchen order detail successfully")
    public ResponseEntity<KitchenQueueResponseDTO> getKitchenOrderById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(kitchenService.getKitchenOrderById(id));
    }

    // ─── Kitchen Status Update [2] ────────────────────────────────────────────

    @PutMapping("/orders/{id}/status")
    @ApiMessage("Update kitchen order status successfully")
    public ResponseEntity<KitchenQueueResponseDTO> updateKitchenOrderStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody KitchenStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(kitchenService.updateKitchenOrderStatus(id, request));
    }

    // ─── Kitchen Load Monitoring [2] — FR-030 ────────────────────────────────

    @GetMapping("/dashboard")
    @ApiMessage("Fetch kitchen dashboard successfully")
    public ResponseEntity<KitchenDashboardDTO> getKitchenDashboard() {
        return ResponseEntity.ok(kitchenService.getKitchenDashboard());
    }
}
