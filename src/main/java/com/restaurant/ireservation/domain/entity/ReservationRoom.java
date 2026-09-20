package com.restaurant.ireservation.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name ="reservation_rooms")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ReservationRoom extends BaseEntity {


    @ManyToOne
    @JoinColumn(name = "reservation_id")
    Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "room_id")
    Room room;

}
