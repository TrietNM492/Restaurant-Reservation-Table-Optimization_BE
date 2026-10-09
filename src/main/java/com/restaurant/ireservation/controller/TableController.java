package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.response.AreaFloorPlanResponse;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.dto.response.TableStatusSummaryResponse;
import com.restaurant.ireservation.service.TableService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module: Table Status Tracking [2]
 */
@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableController {

    TableService tableService;

    @GetMapping
    @ApiMessage("Fetch tables status list")
    public ResponseEntity<ResultPaginationDTO> fetchAllTables(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "areaId", required = false) Long areaId,
            @RequestParam(name = "statusCode", required = false) TableStatus statusCode,
            Pageable pageable
    ) {
        return ResponseEntity.ok(tableService.fetchTables(q, areaId, statusCode, pageable));
    }

    @GetMapping("/summary")
    @ApiMessage("Fetch table status summary")
    public ResponseEntity<TableStatusSummaryResponse> fetchTableStatusSummary() {
        return ResponseEntity.ok(tableService.fetchTableStatusSummary());
    }

    @GetMapping("/floor-plan")
    @ApiMessage("Fetch table floor plan layout")
    public ResponseEntity<List<AreaFloorPlanResponse>> fetchFloorPlan() {
        return ResponseEntity.ok(tableService.fetchFloorPlan());
    }

    @GetMapping("/area/{areaId}")
    @ApiMessage("Fetch tables by area")
    public ResponseEntity<List<TableResponse>> fetchTablesByArea(
            @Positive(message = "Area ID must be positive")
            @PathVariable("areaId")
            @NotNull(message = "Area ID is required") Long areaId
    ) {
        return ResponseEntity.ok(tableService.fetchTablesByArea(areaId));
    }

    @GetMapping("/{id}")
    @ApiMessage("Fetch table status details")
    public ResponseEntity<TableResponse> fetchTableById(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(tableService.fetchTableById(id));
    }

    @PatchMapping("/{id}/status")
    @ApiMessage("Update table status successfully")
    public ResponseEntity<TableResponse> changeTableStatusById(
            @Positive(message = "Id must be positive")
            @PathVariable("id") Long id,
            @Valid @RequestBody ChangeTableStatusRequest request
    ) {
        request.setId(id);
        return ResponseEntity.ok(tableService.changeTableStatus(request));
    }

    @PatchMapping("/status")
    @ApiMessage("Update table status successfully")
    public ResponseEntity<TableResponse> changeTableStatus(
            @Valid @RequestBody ChangeTableStatusRequest request
    ) {
        return ResponseEntity.ok(tableService.changeTableStatus(request));
    }
}
