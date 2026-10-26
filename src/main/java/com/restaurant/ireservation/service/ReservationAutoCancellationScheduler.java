package com.restaurant.ireservation.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ReservationAutoCancellationScheduler {

    final ReservationAutoCancellationService reservationAutoCancellationService;

    @Getter
    @Setter
    Clock clock = Clock.systemUTC();

    @Scheduled(cron = "${app.reservation.auto-cancellation.cron:0 * * * * *}")
    public void executeAutoCancellation() {
        log.debug("Executing scheduled reservation auto-cancellation task");
        reservationAutoCancellationService.processAutoCancellations(Instant.now(clock));
    }
}
