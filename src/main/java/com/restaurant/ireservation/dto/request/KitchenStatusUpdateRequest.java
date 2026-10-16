package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: Kitchen Status Update [2]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenStatusUpdateRequest {

    @NotNull(message = "Status is required")
    OrderKitchenStatus status;

    String note;
}
