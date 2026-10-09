package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableAreaRequest {

    Long id;

    @NotBlank(message = "Area code cannot be blank")
    @Size(min = 2, max = 50, message = "Area code must be between 2 and 50 characters")
    String code;

    @NotNull(message = "Area name is required")
    AreaName name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description;
}
