package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.ServiceType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Report extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "reservation_id")
    Reservation reservation;

    @Column(length = 500)
    String description;

    @Enumerated(EnumType.STRING)
    ServiceType serviceType;
}
