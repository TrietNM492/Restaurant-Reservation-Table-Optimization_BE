package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module 13: Hourly Peak Distribution Stat
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HourlyPeakDTO {

    int hour;

    String timeSlot;

    long reservationCount;
}
