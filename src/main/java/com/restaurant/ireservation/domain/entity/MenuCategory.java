package com.restaurant.ireservation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "menu_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MenuCategory extends BaseEntity {

    @Column(unique = true)
    String code;

    String name;

    @Column(length = 500)
    String description;

    String menuQr;

    String menuPicture;

}
