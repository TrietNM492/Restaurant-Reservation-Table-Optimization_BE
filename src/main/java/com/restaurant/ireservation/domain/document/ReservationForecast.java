package com.restaurant.ireservation.domain.document;

import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "reservation_forecasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ReservationForecast {

    @org.springframework.data.annotation.Id
    String id;

    @Field(name = "forecast_date")
    Instant forecastDate;

    @Field(name = "hour_slot")
    Integer hourSlot;

    @Field(name = "expected_reservations")
    int expectedReservations;

    @Field(name = "expected_occupancy")
    int expectedOccupancy;

    @Field(name = "generate_at")
    Instant generateAt;
}
