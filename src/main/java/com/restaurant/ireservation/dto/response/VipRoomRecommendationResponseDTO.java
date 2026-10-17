package com.restaurant.ireservation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VipRoomRecommendationResponseDTO {

    PartySize partySize;
    Instant startTime;
    Instant endTime;
    int totalRooms;
    int availableRooms;
    Long recommendedRoomId;
    List<VipRoomOptionDTO> rooms;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class VipRoomOptionDTO {
        Long id;
        String roomName;
        RoomType roomType;
        int capacity;
        double price;
        RoomStatus roomStatus;
        String description;

        @JsonProperty("isAvailable")
        boolean isAvailable;

        @JsonProperty("isRecommended")
        boolean isRecommended;

        String unavailabilityReason;
    }
}

