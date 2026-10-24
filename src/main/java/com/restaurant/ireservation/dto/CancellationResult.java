package com.restaurant.ireservation.dto;

import java.time.Instant;

public sealed interface CancellationResult {

    record ConcurrentlyModified(Long reservationId) implements CancellationResult {}

    record CancelledWithoutNotification(
        Long reservationId,
        String reservationNo,
        String missingUsername
    ) implements CancellationResult {}

    record CancelledWithNotification(
        Long reservationId,
        String reservationNo,
        String customerEmail,
        String customerName,
        Instant startTime,
        Long notificationId
    ) implements CancellationResult {}
}
