package com.restaurant.ireservation.dto.response;

public record ReservationAnalyticsSummaryResponse(
        long reservationCount,
        long completedCount,
        long cancelledCount,
        long noShowCount,
        double cancellationRate,
        double noShowRate

) {
    public static ReservationAnalyticsSummaryResponse toResponse(long reservationCount,
                                                                 long completedCount,
                                                                 long cancelledCount,
                                                                 long noShowCount,
                                                                 double cancellationRate,
                                                                 double noShowRate) {
        return new ReservationAnalyticsSummaryResponse(reservationCount, completedCount, cancelledCount, noShowCount, cancellationRate, noShowRate);
    }
}
