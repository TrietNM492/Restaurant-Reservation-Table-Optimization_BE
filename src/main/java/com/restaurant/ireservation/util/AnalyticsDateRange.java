package com.restaurant.ireservation.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record AnalyticsDateRange(
        Instant from,
        Instant to
) {
    private static final ZoneId ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");


    public static AnalyticsDateRange fromDates(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "Both from and to dates are required"
            );
        }

        if (!fromDate.isBefore(toDate)) {
            throw new IllegalArgumentException(
                    "The from date must be before the to date"
            );
        }

        return new AnalyticsDateRange(
                fromDate.atStartOfDay(ZONE).toInstant(),
                toDate.atStartOfDay(ZONE).toInstant()
        );
    }

}
