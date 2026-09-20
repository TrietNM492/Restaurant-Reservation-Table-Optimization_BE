package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MenuItem extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "category_id")
    MenuCategory category;

    @Column(nullable = false)
    String itemName;

    @Column(length = 500)
    String description;

    @Min(0)
    double price;

    String itemPicture;

    Duration estimatedPrepTime;

    @Enumerated(EnumType.STRING)
    ItemStatus statusCode;

    @ManyToOne
    @JoinColumn(name = "origin_id")
    FoodOrigin origin;






}
