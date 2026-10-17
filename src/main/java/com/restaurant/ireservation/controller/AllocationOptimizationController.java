package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.AllocationOptimizationRequest;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse;
import com.restaurant.ireservation.service.AllocationOptimizationService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tables/allocation-optimization")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AllocationOptimizationController {

    AllocationOptimizationService allocationOptimizationService;

    /** Shows what would change; nothing is modified. */
    @PostMapping("/preview")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    @ApiMessage("Preview allocation optimization successfully")
    public AllocationOptimizationResponse preview(
            @Valid @RequestBody(required = false) AllocationOptimizationRequest request) {
        return allocationOptimizationService.preview(orDefault(request));
    }

    /** Recomputes the plan and persists the table changes. */
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGER', 'ROLE_ADMIN')")
    @ApiMessage("Apply allocation optimization successfully")
    public AllocationOptimizationResponse apply(
            @Valid @RequestBody(required = false) AllocationOptimizationRequest request) {
        return allocationOptimizationService.apply(orDefault(request));
    }

    private AllocationOptimizationRequest orDefault(AllocationOptimizationRequest request) {
        return request != null ? request : new AllocationOptimizationRequest();
    }
}
