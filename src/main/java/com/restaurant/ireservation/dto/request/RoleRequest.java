package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleRequest {

    @NotBlank(message = "Must named for role")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Role name may contain only letters, numbers, _ and -")
    String name;

    @Size(max = 255, message = "Role description must not exceed 255 characters")
    String description;

    @NotNull(message = "Permission IDs are required; use an empty list to remove all permissions")
    @Size(max = 1000, message = "A role cannot have more than 1000 permissions")
    List<@NotNull @Positive Long> permissionIds;
}
