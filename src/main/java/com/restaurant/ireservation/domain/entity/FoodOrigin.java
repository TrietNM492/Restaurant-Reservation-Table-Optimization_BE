package com.restaurant.ireservation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "food_origins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class FoodOrigin extends BaseEntity {

    @Column(nullable = false)
    String madeIn;

    String evident; // cai nay luu link video, or description

    @Column(length = 500)
    String description;

    boolean isPeakSeason;

    int discountRate;
}
