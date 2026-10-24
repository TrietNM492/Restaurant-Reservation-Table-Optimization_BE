package com.restaurant.ireservation.dto.response;

import java.util.List;

public record ReservationAnalyticsResponse(
        ReservationAnalyticsSummaryResponse summary,
        List<ReservationTrendResponse> trends

) {
}
