package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
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
public class ReservationResponseDTO {

    Long id;
    String reservationNo;
    PartySize partySize;
    ReservationType reservationType;
    ReservationStatus reservationStatus;
    Instant startTime;
    Instant endTime;
    String note;
    List<TableSummaryDTO> tables;
    List<RoomSummaryDTO> rooms;
    String createdBy;
    Instant createdDate;
    String customerName;
    String customerPhone;
    String customerEmail;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TableSummaryDTO {
        Long id;
        Integer tableNumber;
        int capacity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RoomSummaryDTO {
        Long id;
        String roomName;
        com.restaurant.ireservation.domain.enumerations.RoomType roomType;
        int capacity;
        double price;
    }
}
