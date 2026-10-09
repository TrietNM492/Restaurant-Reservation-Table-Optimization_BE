package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: Table Management [3]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeTableStatusRequest {

    Long id;

    @NotNull(message = "Status code is required")
    TableStatus statusCode;
}
