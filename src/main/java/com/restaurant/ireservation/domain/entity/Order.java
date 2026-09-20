package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {
    String orderNo; // ORD-26092026-042

    @ManyToOne
    @JoinColumn(name = "reservation_id")
    Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "customer_information_id")
    CustomerInformation customerInformation;

    @Enumerated(EnumType.STRING)
    OrderStatus orderStatus;

    boolean isVipRoom;

    double totalAmount;




}
