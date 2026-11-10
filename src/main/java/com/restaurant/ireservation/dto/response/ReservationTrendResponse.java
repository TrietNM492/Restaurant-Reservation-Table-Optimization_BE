package com.restaurant.ireservation.dto.response;

import java.time.LocalDate;

public record ReservationTrendResponse(
        LocalDate date,
        long reservationCount

) {
}
