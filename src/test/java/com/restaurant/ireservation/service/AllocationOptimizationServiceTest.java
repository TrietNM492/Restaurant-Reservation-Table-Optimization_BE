package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.AllocationOptimizationRequest;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationOptimizationServiceTest {

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @InjectMocks
    AllocationOptimizationService service;

    RestaurantTable small;   // id 1, 4 seats
    RestaurantTable big;     // id 2, 12 seats
    Instant start;           // 3 hours from now (outside the default 60 min lock window)

    @BeforeEach
    void setUp() {
        TableArea indoor = TableArea.builder().code("IN").name(AreaName.INDOOR).build();
        indoor.setId(1L);
        small = table(1, 4, indoor);
        big = table(2, 12, indoor);
        start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
    }

    RestaurantTable table(long id, int capacity, TableArea area) {
        RestaurantTable t = RestaurantTable.builder()
                .tableNumber((int) id).capacity(capacity).area(area).statusCode(TableStatus.AVAILABLE).build();
        t.setId(id);
        return t;
    }

    Reservation reservation(long id, PartySize size, ReservationStatus status, Instant from) {
        Reservation r = Reservation.builder()
                .reservationNo("RES-" + id)
                .partySize(size)
                .reservationStatus(status)
                .startTime(from)
                .endTime(from.plus(Duration.ofMinutes(90)))
                .build();
        r.setId(id);
        return r;
    }

    ReservationTable link(Reservation r, RestaurantTable t) {
        return ReservationTable.builder().reservation(r).table(t).build();
    }

    void givenBookings(ReservationTable... rows) {
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(small, big));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any()))
                .thenReturn(List.of(rows));
    }

    /** SMALL party (needs 4 seats) sitting alone on the 12-seat table: 8 wasted seats. */
    ReservationTable oversizedBooking(ReservationStatus status, Instant from) {
        return link(reservation(10, PartySize.SMALL, status, from), big);
    }

    // ------------------------------------------------------------------ preview

    @Test
    @DisplayName("preview: proposes moving a small party from the 12-seat table to the 4-seat table")
    void preview_proposesMove() {
        givenBookings(oversizedBooking(ReservationStatus.CONFIRMED, start));

        AllocationOptimizationResponse res = service.preview(new AllocationOptimizationRequest());

        assertFalse(res.isApplied());
        assertEquals(1, res.getReservationsInScope());
        assertEquals(1, res.getMovableReservations());
        assertEquals(1, res.getMovesCount());
        var move = res.getMoves().get(0);
        assertEquals("RES-10", move.getReservationNo());
        assertEquals(2L, move.getFromTables().get(0).getId());
        assertEquals(1L, move.getToTables().get(0).getId());
        assertEquals(8, move.getWastedSeatsBefore());
        assertEquals(0, move.getWastedSeatsAfter());
        assertEquals(8, res.getBefore().getWastedSeats());
        assertEquals(0, res.getAfter().getWastedSeats());
        assertTrue(res.getAfter().getSeatUtilizationPercent() > res.getBefore().getSeatUtilizationPercent());
        assertNotNull(move.getReason());
        verify(reservationTableRepository, never()).deleteByReservationId(any());
        verify(reservationTableRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("preview: reservation starting inside the lock window is pinned")
    void preview_lockWindowPinsReservation() {
        givenBookings(oversizedBooking(ReservationStatus.CONFIRMED, Instant.now().plus(10, ChronoUnit.MINUTES)));

        AllocationOptimizationResponse res = service.preview(new AllocationOptimizationRequest());

        assertEquals(0, res.getMovableReservations());
        assertEquals(0, res.getMovesCount());
        assertNotNull(res.getMessage());
    }

    @Test
    @DisplayName("preview: IN_PROGRESS reservation is never moved")
    void preview_inProgressPinned() {
        givenBookings(oversizedBooking(ReservationStatus.IN_PROGRESS, start));

        AllocationOptimizationResponse res = service.preview(new AllocationOptimizationRequest());

        assertEquals(0, res.getMovesCount());
    }

    @Test
    @DisplayName("preview: includeConfirmed=false pins CONFIRMED reservations")
    void preview_excludeConfirmed() {
        givenBookings(oversizedBooking(ReservationStatus.CONFIRMED, start));

        AllocationOptimizationResponse res = service.preview(
                AllocationOptimizationRequest.builder().includeConfirmed(false).build());

        assertEquals(0, res.getMovableReservations());
        assertEquals(0, res.getMovesCount());
    }

    @Test
    @DisplayName("preview: PENDING reservation can be moved even when includeConfirmed=false")
    void preview_pendingStillMovable() {
        givenBookings(oversizedBooking(ReservationStatus.PENDING, start));

        AllocationOptimizationResponse res = service.preview(
                AllocationOptimizationRequest.builder().includeConfirmed(false).build());

        assertEquals(1, res.getMovesCount());
    }

    @Test
    @DisplayName("preview: no reservations in the window gives an explanatory message")
    void preview_noReservations() {
        givenBookings();

        AllocationOptimizationResponse res = service.preview(new AllocationOptimizationRequest());

        assertEquals(0, res.getReservationsInScope());
        assertTrue(res.getMoves().isEmpty());
        assertNotNull(res.getMessage());
    }

    @Test
    @DisplayName("preview: window end before start throws RequestInvalidException")
    void preview_endBeforeStart() {
        AllocationOptimizationRequest req = AllocationOptimizationRequest.builder()
                .windowStart(start).windowEnd(start.minusSeconds(60)).build();

        assertThrows(RequestInvalidException.class, () -> service.preview(req));
    }

    @Test
    @DisplayName("preview: window longer than 72 hours throws RequestInvalidException")
    void preview_windowTooLong() {
        AllocationOptimizationRequest req = AllocationOptimizationRequest.builder()
                .windowStart(start).windowEnd(start.plus(73, ChronoUnit.HOURS)).build();

        assertThrows(RequestInvalidException.class, () -> service.preview(req));
    }

    @Test
    @DisplayName("preview: window start in the past throws RequestInvalidException")
    void preview_pastStart() {
        AllocationOptimizationRequest req = AllocationOptimizationRequest.builder()
                .windowStart(Instant.now().minus(2, ChronoUnit.HOURS)).build();

        assertThrows(RequestInvalidException.class, () -> service.preview(req));
    }

    // ------------------------------------------------------------------ apply

    @Test
    @DisplayName("apply: replaces the table links of the moved reservation")
    void apply_persistsMove() {
        givenBookings(oversizedBooking(ReservationStatus.CONFIRMED, start));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(10L), any(), any(), any())).thenReturn(List.of());

        AllocationOptimizationResponse res = service.apply(new AllocationOptimizationRequest());

        assertTrue(res.isApplied());
        assertEquals(1, res.getMovesCount());
        verify(reservationTableRepository).deleteByReservationId(10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservationTable>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationTableRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(1L, captor.getValue().get(0).getTable().getId());
        assertEquals(10L, captor.getValue().get(0).getReservation().getId());
    }

    @Test
    @DisplayName("apply: aborts with ReservationConflictException when the target table was taken meanwhile")
    void apply_conflictAborts() {
        givenBookings(oversizedBooking(ReservationStatus.CONFIRMED, start));
        Reservation other = reservation(99, PartySize.SMALL, ReservationStatus.CONFIRMED, start);
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(10L), any(), any(), any())).thenReturn(List.of(link(other, small)));

        assertThrows(ReservationConflictException.class, () -> service.apply(new AllocationOptimizationRequest()));
        verify(reservationTableRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("apply: nothing to improve means nothing is written")
    void apply_noMoves() {
        givenBookings(link(reservation(10, PartySize.SMALL, ReservationStatus.CONFIRMED, start), small));

        AllocationOptimizationResponse res = service.apply(new AllocationOptimizationRequest());

        assertFalse(res.isApplied());
        assertEquals(0, res.getMovesCount());
        verify(reservationTableRepository, never()).deleteByReservationId(any());
        verify(reservationTableRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------------ helpers

    @Test
    @DisplayName("requiredSeats uses the upper bound of each PartySize bucket")
    void requiredSeats_upperBounds() {
        assertEquals(1, AllocationOptimizationService.requiredSeats(PartySize.SINGLE));
        assertEquals(4, AllocationOptimizationService.requiredSeats(PartySize.SMALL));
        assertEquals(8, AllocationOptimizationService.requiredSeats(PartySize.MEDIUM));
        assertEquals(12, AllocationOptimizationService.requiredSeats(PartySize.LARGE));
        assertEquals(16, AllocationOptimizationService.requiredSeats(PartySize.XLARGE));
        assertEquals(0, AllocationOptimizationService.requiredSeats(null));
    }
}
