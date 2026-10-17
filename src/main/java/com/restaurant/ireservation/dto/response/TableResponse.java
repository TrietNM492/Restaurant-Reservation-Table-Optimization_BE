package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.TableStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Module: Table Management [3]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableResponse {

    Long id;
    Integer tableNumber;
    int capacity;
    TableStatus statusCode;
    String description;
    TableAreaResponse area;
    String createdBy;
    Instant createdDate;
    String lastModifiedBy;
    Instant lastModifiedDate;
}
