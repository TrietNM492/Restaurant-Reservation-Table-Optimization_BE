package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginDTO(
        @NotBlank(message = "Username null")
        String username,
        @NotBlank(message = "Password null")
        String password
) {
}
