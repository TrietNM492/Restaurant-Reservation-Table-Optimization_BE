package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.request.TableRequest;
import com.restaurant.ireservation.dto.response.TableResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Module: Table Management [3]
 */
@RestController
@RequestMapping("/api/v1/admin/tables")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminTableController {

    TableService tableService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch all tables")
    public ResponseEntity<ResultPaginationDTO> fetchAllTables(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "areaId", required = false) Long areaId,
            @RequestParam(name = "statusCode", required = false) TableStatus statusCode,
            Pageable pageable
    ) {
        return ResponseEntity.ok(tableService.fetchTables(q, areaId, statusCode, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch table by id")
    public ResponseEntity<TableResponse> fetchTableById(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(tableService.fetchTableById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Create table successfully")
    public ResponseEntity<TableResponse> createTable(
            @Valid @RequestBody TableRequest request
    ) {
        TableResponse response = tableService.createTable(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update table successfully")
    public ResponseEntity<TableResponse> updateTableWithId(
            @Positive(message = "Id must be positive")
            @PathVariable("id") Long id,
            @Valid @RequestBody TableRequest request
    ) {
        request.setId(id);
        return ResponseEntity.ok(tableService.updateTable(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update table successfully")
    public ResponseEntity<TableResponse> updateTable(
            @Valid @RequestBody TableRequest request
    ) {
        return ResponseEntity.ok(tableService.updateTable(request));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Change table status successfully")
    public ResponseEntity<TableResponse> changeTableStatus(
            @Valid @RequestBody ChangeTableStatusRequest request
    ) {
        return ResponseEntity.ok(tableService.changeTableStatus(request));
    }

    @PatchMapping("/{id}/toggle-lock")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Toggle table lock successfully")
    public ResponseEntity<TableResponse> toggleLockTable(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(tableService.toggleLockTable(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Delete table successfully")
    public ResponseEntity<Void> deleteTable(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        tableService.deleteTable(id);
        return ResponseEntity.ok().build();
    }
}
