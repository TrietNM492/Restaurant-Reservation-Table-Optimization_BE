package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.MenuCategoryPublicResponse;
import com.restaurant.ireservation.dto.response.MenuItemPublicResponse;
import com.restaurant.ireservation.service.MenuBrowsingService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public (no login required) menu browsing for clients. */
@RestController
@RequestMapping("/api/v1/menus")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MenuBrowsingController {

    final MenuBrowsingService menuBrowsingService;

    @GetMapping("/categories")
    public ResponseEntity<List<MenuCategoryPublicResponse>> fetchCategories() {
        return ResponseEntity.ok(menuBrowsingService.fetchCategories());
    }

    @GetMapping
    public ResponseEntity<ResultPaginationDTO> browse(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "minPrice", required = false) Double minPrice,
            @RequestParam(name = "maxPrice", required = false) Double maxPrice,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        String key = q != null ? q.trim() : "";
        return ResponseEntity.ok(menuBrowsingService.browse(key, categoryId, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemPublicResponse> fetchById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(menuBrowsingService.fetchById(id));
    }
}
