package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Module: VIP Room Management [6]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomResponse {

    Long id;
    String roomName;
    RoomType roomType;
    int capacity;
    double price;
    RoomStatus roomStatus;
    String description;
    String createdBy;
    Instant createdDate;
    String lastModifiedBy;
    Instant lastModifiedDate;
}
