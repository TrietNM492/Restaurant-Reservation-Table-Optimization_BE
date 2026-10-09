package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PermissionRequest(
        Long id,
        @NotBlank(message = "Permission name can't be MT")
        String name,
        @NotBlank(message = "Method can't be MT")
        String method,
        @NotNull(message = "Role names are required")
        @Size(min = 1, message = "Role must at least 1")
        List<@NotBlank String> rolesName
) {
}
