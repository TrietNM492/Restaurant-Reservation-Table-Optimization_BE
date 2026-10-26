package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.MultiTableAllocationRequest;
import com.restaurant.ireservation.dto.response.MultiTableAllocationResponse;
import com.restaurant.ireservation.service.MultiTableAllocationService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/table-allocation/combinations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_STAFF', 'ROLE_MANAGER', 'ROLE_ADMIN')")
public class MultiTableAllocationController {
    private final MultiTableAllocationService multiTableAllocationService;

    @PostMapping
    @ApiMessage("Allocate table combination successfully")
    public MultiTableAllocationResponse allocate(@Valid @RequestBody MultiTableAllocationRequest request) {
        return multiTableAllocationService.allocate(request);
    }

    @GetMapping
    @ApiMessage("Fetch table combinations successfully")
    public ResultPaginationDTO getCombinations(@RequestParam(required = false) Long reservationId,
            @PageableDefault(sort = "id") Pageable pageable) {
        return multiTableAllocationService.getCombinations(reservationId, pageable);
    }
}
