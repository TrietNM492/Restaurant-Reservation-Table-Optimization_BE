package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: VIP Room Management [6]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRoomStatusRequest {

    Long id;

    @NotNull(message = "Room status is required")
    RoomStatus roomStatus;
}
