package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.AreaName;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableAreaResponse {

    Long id;
    String code;
    AreaName name;
    String description;
    String createdBy;
    Instant createdDate;
    String lastModifiedBy;
    Instant lastModifiedDate;
}
