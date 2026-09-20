package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Reservation extends BaseEntity {


    String reservationNo;   // RES-26092026-042

    @Enumerated(EnumType.STRING)
    PartySize partySize;

    Instant startTime;
    Instant endTime;

    @Enumerated(EnumType.STRING)
    ReservationType reservationType;

    @Enumerated(EnumType.STRING)
    ReservationStatus reservationStatus;

    @Column(length = 500)
    String note;




}
