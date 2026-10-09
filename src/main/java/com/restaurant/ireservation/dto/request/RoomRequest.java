package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class RoomRequest {

    Long id;

    @NotBlank(message = "Room name cannot be blank")
    @Size(min = 2, max = 100, message = "Room name must be between 2 and 100 characters")
    String roomName;

    @NotNull(message = "Room type is required")
    RoomType roomType;

    @Min(value = 10, message = "Capacity must be at least 10")
    int capacity;

    @Min(value = 0, message = "Price must be non-negative")
    double price;

    RoomStatus roomStatus;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description;
}
