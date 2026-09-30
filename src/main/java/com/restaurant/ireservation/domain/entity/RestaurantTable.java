package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "restaurant_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class RestaurantTable extends BaseEntity {

    @Positive(message = "table number must be positive?")
    Integer tableNumber;

    @Min(value = 1, message = "moi ban co it nhat 1 cho chu, broooooooo")
    int capacity;

    @ManyToOne
    @JoinColumn(name = "area_id")
    TableArea area;

    @Enumerated(EnumType.STRING)
    TableStatus statusCode;

    String description;

}
