package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Module 13: Individual Table Usage Statistics
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TableUsageStatDTO {

    Long tableId;

    Integer tableNumber;

    int capacity;

    String areaName;

    long totalReservationsCount;

    double totalOccupiedHours;

    double utilizationRatePercent;
}
