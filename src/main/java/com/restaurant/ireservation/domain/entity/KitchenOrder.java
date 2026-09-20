package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Duration;
import java.time.LocalDate;

@Entity
@Table(name = "kitchen_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class KitchenOrder extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "order_id")
    Order order;

    @Enumerated(EnumType.STRING)
    OrderKitchenStatus status;

    Duration queueTime;

    LocalDate orderTime;

    LocalDate readyTime;

    LocalDate servedTime;

}
