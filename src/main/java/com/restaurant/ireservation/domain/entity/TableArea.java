package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "table_areas")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TableArea extends BaseEntity {

    @Column(unique = true)
    @Size(min = 50)
    String code;

    @Column(length = 500)
    String description;

    @Enumerated(EnumType.STRING)
    AreaName name;

}
