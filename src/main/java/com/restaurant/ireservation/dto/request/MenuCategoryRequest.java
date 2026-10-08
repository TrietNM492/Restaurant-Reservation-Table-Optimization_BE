package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuCategoryRequest {

    @NotBlank(message = "Category code is required")
    @Size(max = 50, message = "Category code must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Category code only allows letters, digits, '_' and '-'")
    String code;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description;

    @Size(max = 255, message = "Menu QR must be at most 255 characters")
    String menuQr;

    @Size(max = 255, message = "Menu picture must be at most 255 characters")
    String menuPicture;
}
