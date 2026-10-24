package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.response.ReservationAnalyticsResponse;
import com.restaurant.ireservation.dto.response.ReservationAnalyticsSummaryResponse;
import com.restaurant.ireservation.dto.response.ReservationTrendResponse;
import com.restaurant.ireservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationAnalyticsService {

    private final ReservationRepository reservationRepository;

    public ReservationAnalyticsResponse getAnalytics(
            Instant from, Instant to
    ) {
        validateTimeRange(from, to);
        ReservationAnalyticsSummaryResponse response = getSummary(from, to);

        List<ReservationTrendResponse> trends = getTrends(from, to);

        return new ReservationAnalyticsResponse(response, trends);
    }



    private ReservationAnalyticsSummaryResponse getSummary(
            Instant from, Instant to
    ) {
        long reservationCount = reservationRepository.countByCreatedDateGreaterThanEqualAndCreatedDateLessThan(from, to);

        List<Object[]> statusResults = reservationRepository.countFinalReservationByStatus(
                from, to, ReservationStatus.COMPLETED, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW
        );

        Map<ReservationStatus, Long> counts = new HashMap<>();

        for (Object[] row : statusResults) {
            ReservationStatus status = (ReservationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status, count);
        }

        long completedCount = counts.getOrDefault(ReservationStatus.COMPLETED, 0L);

        long cancelledCount = counts.getOrDefault(
                ReservationStatus.CANCELLED, 0L
        );

        long noShowCount = counts.getOrDefault(
                ReservationStatus.NO_SHOW, 0L
        );

        long finalCount =
                completedCount + cancelledCount + noShowCount;

        double cancellationRate = calculateRate(
                cancelledCount, finalCount
        );

        double noShowRate = calculateRate(
                noShowCount, finalCount
        );
        return new ReservationAnalyticsSummaryResponse(reservationCount, completedCount, cancelledCount,
                noShowCount, cancellationRate, noShowRate);

    }


    private List<ReservationTrendResponse> getTrends(
            Instant from,
            Instant to
    ) {
        List<Object[]> results =
                reservationRepository.countReservationByCreatedDate(
                        from, to
                );

        List<ReservationTrendResponse> trends = new ArrayList<>();

        for (Object[] row : results) {
            LocalDate date = convertToLocalDate(row[0]);
            long count = ((Number) row[1]).longValue();

            trends.add(
                    new ReservationTrendResponse(date, count)
            );
        }

        return trends;
    }

    private LocalDate convertToLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return LocalDate.parse(value.toString());
    }



    private double calculateRate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return Math.round(
                (double) numerator / denominator * 10000.0
        ) / 100.0;
    }



    private void validateTimeRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Start and end time are required"
            );
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "Start time must be before end time"
            );

        }
    }
}
