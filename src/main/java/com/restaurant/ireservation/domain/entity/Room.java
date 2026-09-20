package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Room extends BaseEntity {

    @Column(nullable = false)
    String roomName;

    @Enumerated(EnumType.STRING)
    RoomType roomType;

    @Min(value = 10, message = "1 phong nho lam cung chua duoc 10 dua chu??")
    int capacity;

    double price;

    @Enumerated(EnumType.STRING)
    RoomStatus roomStatus;

    @Column(length = 500)
    String description;




}
