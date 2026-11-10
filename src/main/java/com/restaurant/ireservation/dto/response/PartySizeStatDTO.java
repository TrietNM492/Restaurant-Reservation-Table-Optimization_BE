package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module 13: Party Size Distribution Stat
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PartySizeStatDTO {

    String partySize;

    String label;

    long reservationCount;

    double percentage;
}
