package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.dto.request.PermissionRequest;
import com.restaurant.ireservation.service.PermissionService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/permissions")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class PermissionController {

    final PermissionService permissionService;

    @GetMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResultPaginationDTO> fetchAllPermission(@RequestParam(name = "q", required = false) String q, Pageable pageable) {
        String key = q != null ? q : "";
        return ResponseEntity.ok(permissionService.fetchAllPermission(key, pageable));

    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Permission> createPermission(@Valid @RequestBody PermissionRequest request) {
        var permission = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Permission> updatePermission(@Valid @RequestBody PermissionRequest request) {
        var permission = permissionService.updatePermission(request);
        return ResponseEntity.ok(permission);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deletePermission(@Positive @PathVariable("id") Long id) {
        String msg = permissionService.deletePermission(id);
        return ResponseEntity.ok().body(msg);

    }


}
