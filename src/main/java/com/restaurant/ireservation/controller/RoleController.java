package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.RolePermissionsRequest;
import com.restaurant.ireservation.dto.request.RoleRequest;
import com.restaurant.ireservation.dto.response.RoleResponse;
import com.restaurant.ireservation.service.RoleService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/roles")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Validated
public class RoleController {

    final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResultPaginationDTO> fetchAllRoles(
            @RequestParam(name = "q", required = false) String q,
            Pageable pageable
    ) {
        return ResponseEntity.ok(roleService.fetchAllRole(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleResponse> fetchRoleById(@Positive @PathVariable Long id) {
        return ResponseEntity.ok(roleService.fetchRoleById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleResponse> updateRole(
            @Positive @PathVariable Long id,
            @Valid @RequestBody RoleRequest request
    ) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleResponse> updateRolePermissions(
            @Positive @PathVariable Long id,
            @Valid @RequestBody RolePermissionsRequest request
    ) {
        return ResponseEntity.ok(roleService.updateRolePermissions(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRole(@Positive @PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
