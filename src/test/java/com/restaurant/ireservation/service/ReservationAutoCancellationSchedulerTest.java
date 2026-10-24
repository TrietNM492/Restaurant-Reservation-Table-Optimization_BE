package com.restaurant.ireservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationAutoCancellationSchedulerTest {

    @Mock
    private ReservationAutoCancellationService service;

    @InjectMocks
    private ReservationAutoCancellationScheduler scheduler;

    private final Instant now = Instant.parse("2026-10-24T16:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        scheduler.setClock(fixedClock);
    }

    @Test
    @DisplayName("Scheduler TC-01: executeAutoCancellation delegates to service with current time")
    void executeAutoCancellation_delegatesToServiceWithCurrentTime() {
        scheduler.executeAutoCancellation();

        verify(service).processAutoCancellations(now);
    }
}
