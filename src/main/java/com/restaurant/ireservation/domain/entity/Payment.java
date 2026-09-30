package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class Payment extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "order_id")
    Order order;

    double totalFinalPrice;

    int discountRate;

    @Enumerated(EnumType.STRING)
    PaymentStatus paymentStatus;

}
