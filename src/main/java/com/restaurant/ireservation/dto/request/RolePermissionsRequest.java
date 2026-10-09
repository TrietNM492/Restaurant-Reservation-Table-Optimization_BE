package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RolePermissionsRequest(
        @NotNull(message = "Permission IDs are required; use an empty list to remove all permissions")
        @Size(max = 1000, message = "A role cannot have more than 1000 permissions")
        List<@NotNull @Positive Long> permissionIds
) {
}
