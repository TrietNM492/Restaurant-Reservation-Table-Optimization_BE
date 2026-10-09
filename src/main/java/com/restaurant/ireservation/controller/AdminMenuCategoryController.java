package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.MenuCategoryRequest;
import com.restaurant.ireservation.dto.response.MenuCategoryResponse;
import com.restaurant.ireservation.service.MenuCategoryService;
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
@RequestMapping("/api/v1/admin/menu-category")
@PreAuthorize("hasRole('ADMIN')")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AdminMenuCategoryController {

    final MenuCategoryService menuCategoryService;

    @GetMapping
    public ResponseEntity<ResultPaginationDTO> fetchAll(
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String key = q != null ? q.trim() : "";
        return ResponseEntity.ok(menuCategoryService.fetchCategories(key, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuCategoryResponse> fetchById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(menuCategoryService.fetchById(id));
    }

    @PostMapping
    public ResponseEntity<MenuCategoryResponse> create(@Valid @RequestBody MenuCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuCategoryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuCategoryResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody MenuCategoryRequest request
    ) {
        return ResponseEntity.ok(menuCategoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        menuCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
