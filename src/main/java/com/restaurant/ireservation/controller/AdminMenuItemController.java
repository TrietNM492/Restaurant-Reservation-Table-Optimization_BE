package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.MenuItemRequest;
import com.restaurant.ireservation.dto.request.MenuItemStatusRequest;
import com.restaurant.ireservation.dto.response.MenuItemResponse;
import com.restaurant.ireservation.service.MenuItemService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/menu-item")
@PreAuthorize("hasRole('ADMIN')")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AdminMenuItemController {

    final MenuItemService menuItemService;

    @GetMapping
    public ResponseEntity<ResultPaginationDTO> fetchAll(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String key = q != null ? q.trim() : "";
        return ResponseEntity.ok(menuItemService.fetchItems(key, categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> fetchById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(menuItemService.fetchById(id));
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody MenuItemRequest request
    ) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MenuItemResponse> changeStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody MenuItemStatusRequest request
    ) {
        return ResponseEntity.ok(menuItemService.changeStatus(id, request));
    }

    /** Soft delete: sets status to ARCHIVED. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable("id") Long id) {
        menuItemService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
