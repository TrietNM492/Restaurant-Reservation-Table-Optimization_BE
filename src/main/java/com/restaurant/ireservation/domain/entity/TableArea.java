package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@Entity
@Table(name = "table_areas")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TableArea extends BaseEntity {

    @Column(unique = true, length = 50)
    @Size(max = 50)
    String code;

    @Column(length = 500)
    String description;

    @Enumerated(EnumType.STRING)
    AreaName name;

}
