package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.dto.request.TableAreaRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.service.TableAreaService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@RestController
@RequestMapping("/api/v1/admin/table-areas")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminTableAreaController {

    TableAreaService tableAreaService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch all table areas")
    public ResponseEntity<ResultPaginationDTO> fetchAllTableAreas(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "name", required = false) AreaName name,
            Pageable pageable
    ) {
        return ResponseEntity.ok(tableAreaService.fetchTableAreas(q, name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch table area by id")
    public ResponseEntity<TableAreaResponse> fetchTableAreaById(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(tableAreaService.fetchTableAreaById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Create table area successfully")
    public ResponseEntity<TableAreaResponse> createTableArea(
            @Valid @RequestBody TableAreaRequest request
    ) {
        TableAreaResponse response = tableAreaService.createTableArea(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update table area successfully")
    public ResponseEntity<TableAreaResponse> updateTableAreaWithId(
            @Positive(message = "Id must be positive")
            @PathVariable("id") Long id,
            @Valid @RequestBody TableAreaRequest request
    ) {
        request.setId(id);
        return ResponseEntity.ok(tableAreaService.updateTableArea(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update table area successfully")
    public ResponseEntity<TableAreaResponse> updateTableArea(
            @Valid @RequestBody TableAreaRequest request
    ) {
        return ResponseEntity.ok(tableAreaService.updateTableArea(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Delete table area successfully")
    public ResponseEntity<Void> deleteTableArea(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        tableAreaService.deleteTableArea(id);
        return ResponseEntity.ok().build();
    }
}
