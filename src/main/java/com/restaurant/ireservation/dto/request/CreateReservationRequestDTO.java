package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateReservationRequestDTO {

    @NotNull(message = "Party size is required")
    PartySize partySize;

    @NotNull(message = "Reservation type is required")
    ReservationType reservationType;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    Instant startTime;

    @NotNull(message = "End time is required")
    Instant endTime;

    List<Long> tableIds;

    List<Long> roomIds;

    @Size(max = 500, message = "Note cannot exceed 500 characters")
    String note;

    @AssertTrue(message = "At least one table must be selected")
    public boolean isTableIdsValid() {
        if (reservationType == ReservationType.TABLE) {
            return tableIds != null && !tableIds.isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "At least one room must be selected")
    public boolean isRoomIdsValid() {
        if (reservationType == ReservationType.VIP_ROOM) {
            return roomIds != null && !roomIds.isEmpty();
        }
        return true;
    }
}
