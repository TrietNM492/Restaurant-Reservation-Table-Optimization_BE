package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeUserStatus {
    @NotNull(message = "Staff id is required")
    @Positive(message = "Id must be positive")
    Long id;

    String reason;
    boolean notifyUser;
}
