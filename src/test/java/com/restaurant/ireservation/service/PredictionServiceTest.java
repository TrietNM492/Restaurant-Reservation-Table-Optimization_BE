package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.domain.enumerations.WaitStatus;
import com.restaurant.ireservation.dto.request.WaitingTimeRequestDTO;
import com.restaurant.ireservation.dto.response.WaitingTimeResponseDTO;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @InjectMocks
    PredictionService predictionService;

    // Fixed test clock at 2026-10-23T18:00:00Z
    static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");
    static final String USERNAME = "customer@example.com";

    @BeforeEach
    void setUp() {
        predictionService.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Reservation buildReservation(Long id, ReservationStatus status, ReservationType type,
                                         Instant start, Instant end, String createdBy) {
        Reservation r = new Reservation();
        r.setId(id);
        r.setReservationStatus(status);
        r.setReservationType(type);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setCreatedBy(createdBy);
        return r;
    }

    private RestaurantTable buildTable(Long id, TableStatus status) {
        RestaurantTable t = new RestaurantTable();
        t.setId(id);
        t.setStatusCode(status);
        return t;
    }

    private ReservationTable buildReservationTable(Reservation r, RestaurantTable t) {
        ReservationTable rt = new ReservationTable();
        rt.setReservation(r);
        rt.setTable(t);
        return rt;
    }

    @Test
    @DisplayName("TC-01: On-time future reservation returns SCHEDULED with zero wait and readyTime = startTime")
    void futureReservation_onTime_returnsScheduled() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));   // 21:00
        Reservation target = buildReservation(1L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(1L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(1L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(1L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(1L), USERNAME);

        assertEquals(WaitStatus.SCHEDULED, response.getWaitStatus());
        assertEquals(0, response.getEstimatedWaitMinutes());
        assertEquals(start, response.getEstimatedReadyTime());
        assertTrue(response.getMessage().contains("scheduled on time"));
    }

    @Test
    @DisplayName("TC-02: On-time started/late reservation returns READY with zero wait and non-stale readyTime = now")
    void startedReservation_onTime_returnsReadyWithNow() {
        Instant start = NOW.minus(Duration.ofMinutes(10)); // 17:50, customer arrives at 18:00 (now)
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(2L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        when(reservationRepository.findById(2L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(2L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(2L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(2L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(2L), USERNAME);

        assertEquals(WaitStatus.READY, response.getWaitStatus());
        assertEquals(0, response.getEstimatedWaitMinutes());
        assertEquals(NOW, response.getEstimatedReadyTime());
        assertFalse(response.getEstimatedReadyTime().isBefore(NOW), "Ready time must never be in the past");
    }

    @Test
    @DisplayName("TC-03: Target reservation already IN_PROGRESS on clean table returns READY with readyTime = now")
    void targetInProgress_cleanTable_returnsReady() {
        Instant start = NOW.minus(Duration.ofMinutes(20));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(3L, ReservationStatus.IN_PROGRESS, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        when(reservationRepository.findById(3L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(3L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(3L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(3L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(3L), USERNAME);

        assertEquals(WaitStatus.READY, response.getWaitStatus());
        assertEquals(0, response.getEstimatedWaitMinutes());
        assertEquals(NOW, response.getEstimatedReadyTime());
        assertTrue(response.getMessage().contains("No remaining wait to begin seating"));
    }

    @Test
    @DisplayName("TC-04: Preceding COMPLETED reservation ending at 18:50 with arrival at 19:00 yields WAITING 5m")
    void precedingCompletedBooking_delaysArrival_returnsWaiting() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(4L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        Reservation prev = buildReservation(99L, ReservationStatus.COMPLETED, ReservationType.TABLE,
                start.minus(Duration.ofMinutes(80)), start.minus(Duration.ofMinutes(10)), "other"); // ends 18:50
        ReservationTable rtPrev = buildReservationTable(prev, table);

        when(reservationRepository.findById(4L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(4L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(4L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(4L), anyCollection(), any(), any()))
                .thenReturn(List.of(rtPrev));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(4L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(5, response.getEstimatedWaitMinutes());
        assertEquals(start.plus(Duration.ofMinutes(5)), response.getEstimatedReadyTime()); // 18:50 + 15m = 19:05
    }

    @Test
    @DisplayName("TC-05: Policy 2: IN_PROGRESS booking with now < endTime <= T_arrival on IN_USE table yields WAITING and disclaimer")
    void policy2_inProgressActive_returnsWaitingWithDisclaimer() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(5L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.IN_USE);

        // Active occupant ends at 18:50 (now = 18:00 < 18:50 <= 19:00)
        Reservation activeOccupant = buildReservation(88L, ReservationStatus.IN_PROGRESS, ReservationType.TABLE,
                NOW.minus(Duration.ofMinutes(40)), NOW.plus(Duration.ofMinutes(50)), "other");
        ReservationTable rtOcc = buildReservationTable(activeOccupant, table);

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(5L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(5L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findByTableIdAndReservationStatusExcludingReservation(10L, 5L, ReservationStatus.IN_PROGRESS))
                .thenReturn(List.of(rtOcc));
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(5L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(5L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(5, response.getEstimatedWaitMinutes());
        assertEquals(Instant.parse("2026-10-23T19:05:00Z"), response.getEstimatedReadyTime());
        assertTrue(response.getMessage().contains("schedule-based estimate, not confirmation that the table will be vacant"));
        assertTrue(response.getMessage().contains("18:50"));
    }

    @Test
    @DisplayName("TC-06: Overstay: IN_PROGRESS booking with endTime <= now on IN_USE table yields INDETERMINATE")
    void inUseOverstay_returnsIndeterminate() {
        // Clock at 18:55
        Instant nowOverstay = Instant.parse("2026-10-23T18:55:00Z");
        predictionService.setClock(Clock.fixed(nowOverstay, ZoneOffset.UTC));

        Instant start = Instant.parse("2026-10-23T19:00:00Z");
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(6L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.IN_USE);

        // Occupant scheduled end was 18:50 (which is <= 18:55 now)
        Reservation activeOccupant = buildReservation(88L, ReservationStatus.IN_PROGRESS, ReservationType.TABLE,
                Instant.parse("2026-10-23T17:30:00Z"), Instant.parse("2026-10-23T18:50:00Z"), "other");
        ReservationTable rtOcc = buildReservationTable(activeOccupant, table);

        when(reservationRepository.findById(6L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(6L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(6L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findByTableIdAndReservationStatusExcludingReservation(10L, 6L, ReservationStatus.IN_PROGRESS))
                .thenReturn(List.of(rtOcc));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(6L), USERNAME);

        assertEquals(WaitStatus.INDETERMINATE, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
        assertNull(response.getEstimatedReadyTime());
        assertTrue(response.getMessage().contains("exceeded their scheduled departure time"));
    }

    @Test
    @DisplayName("TC-07: Table physically CLEANING yields WAITING 15m from now")
    void tableCleaning_returnsWaiting15m() {
        Instant start = NOW; // 18:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(7L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.CLEANING);

        when(reservationRepository.findById(7L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(7L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(7L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(7L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(7L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(15, response.getEstimatedWaitMinutes());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), response.getEstimatedReadyTime());
        assertTrue(response.getMessage().contains("currently being cleaned"));
    }

    @Test
    @DisplayName("TC-08: Target IN_PROGRESS reservation on CLEANING table yields WAITING 15m")
    void targetInProgress_tableCleaning_returnsWaiting15m() {
        Instant start = NOW.minus(Duration.ofMinutes(10));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(8L, ReservationStatus.IN_PROGRESS, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.CLEANING);

        when(reservationRepository.findById(8L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(8L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(8L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(8L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(8L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(15, response.getEstimatedWaitMinutes());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-09: Table status LOCKED returns UNAVAILABLE")
    void tableLocked_returnsUnavailable() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(9L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.LOCKED);

        when(reservationRepository.findById(9L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(9L)).thenReturn(List.of(buildReservationTable(target, table)));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(9L), USERNAME);

        assertEquals(WaitStatus.UNAVAILABLE, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
        assertNull(response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-10: Overlapping active reservation on target interval returns CONFLICT")
    void activeConflict_returnsConflict() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(10L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        Reservation conflicting = buildReservation(999L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, "other");
        ReservationTable rtConflict = buildReservationTable(conflicting, table);

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(10L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(10L), anyCollection(), any(), any()))
                .thenReturn(List.of(rtConflict));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(10L), USERNAME);

        assertEquals(WaitStatus.CONFLICT, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
        assertNull(response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-11: Target IN_PROGRESS reservation with overlapping active booking returns CONFLICT")
    void targetInProgress_withConflict_returnsConflict() {
        Instant start = NOW.minus(Duration.ofMinutes(10));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(11L, ReservationStatus.IN_PROGRESS, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        Reservation conflicting = buildReservation(999L, ReservationStatus.CONFIRMED, ReservationType.TABLE, NOW, end, "other");
        ReservationTable rtConflict = buildReservationTable(conflicting, table);

        when(reservationRepository.findById(11L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(11L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(11L), anyCollection(), any(), any()))
                .thenReturn(List.of(rtConflict));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(11L), USERNAME);

        assertEquals(WaitStatus.CONFLICT, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
        assertNull(response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-12: Multi-table bottleneck: Table 1 ready in 5m, Table 2 ready in 15m yields WAITING 15m")
    void multiTable_bottleneckWaitTime() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(12L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);

        RestaurantTable t1 = buildTable(10L, TableStatus.AVAILABLE);
        RestaurantTable t2 = buildTable(20L, TableStatus.AVAILABLE);

        Reservation prev1 = buildReservation(101L, ReservationStatus.COMPLETED, ReservationType.TABLE,
                start.minus(Duration.ofMinutes(70)), start.minus(Duration.ofMinutes(10)), "u1"); // ends 18:50 -> ready 19:05 (5m delay)
        Reservation prev2 = buildReservation(102L, ReservationStatus.COMPLETED, ReservationType.TABLE,
                start.minus(Duration.ofMinutes(60)), start, "u2"); // ends 19:00 -> ready 19:15 (15m delay)

        when(reservationRepository.findById(12L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(12L)).thenReturn(List.of(
                buildReservationTable(target, t1),
                buildReservationTable(target, t2)
        ));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(12L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(12L), anyCollection(), any(), any()))
                .thenReturn(List.of(buildReservationTable(prev1, t1)));
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(20L), eq(12L), anyCollection(), any(), any()))
                .thenReturn(List.of(buildReservationTable(prev2, t2)));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(12L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(15, response.getEstimatedWaitMinutes());
        assertEquals(start.plus(Duration.ofMinutes(15)), response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-13: Multi-table precedence: Table 1 LOCKED and Table 2 CONFLICT yields UNAVAILABLE")
    void multiTable_lockedOverridesConflict() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(13L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);

        RestaurantTable t1 = buildTable(10L, TableStatus.LOCKED);
        RestaurantTable t2 = buildTable(20L, TableStatus.AVAILABLE);

        Reservation conflictRes = buildReservation(999L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, "other");

        when(reservationRepository.findById(13L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(13L)).thenReturn(List.of(
                buildReservationTable(target, t1),
                buildReservationTable(target, t2)
        ));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(eq(List.of(20L)), eq(13L), anyCollection(), any(), any()))
                .thenReturn(List.of(buildReservationTable(conflictRes, t2)));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(13L), USERNAME);

        assertEquals(WaitStatus.UNAVAILABLE, response.getWaitStatus());
    }

    @Test
    @DisplayName("TC-14: Multi-table precedence: Table 1 CONFLICT and Table 2 WAITING yields CONFLICT")
    void multiTable_conflictOverridesWaiting() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(14L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);

        RestaurantTable t1 = buildTable(10L, TableStatus.AVAILABLE);
        RestaurantTable t2 = buildTable(20L, TableStatus.CLEANING);

        Reservation conflictRes = buildReservation(999L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, "other");

        when(reservationRepository.findById(14L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(14L)).thenReturn(List.of(
                buildReservationTable(target, t1),
                buildReservationTable(target, t2)
        ));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(eq(List.of(10L)), eq(14L), anyCollection(), any(), any()))
                .thenReturn(List.of(buildReservationTable(conflictRes, t1)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(eq(List.of(20L)), eq(14L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(14L), USERNAME);

        assertEquals(WaitStatus.CONFLICT, response.getWaitStatus());
    }

    @Test
    @DisplayName("TC-15: Multi-table precedence: Table 1 INDETERMINATE and Table 2 WAITING yields INDETERMINATE")
    void multiTable_indeterminateOverridesWaiting() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(15L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);

        RestaurantTable t1 = buildTable(10L, TableStatus.IN_USE); // Walk-in (0 occupants) -> INDETERMINATE
        RestaurantTable t2 = buildTable(20L, TableStatus.CLEANING);

        when(reservationRepository.findById(15L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(15L)).thenReturn(List.of(
                buildReservationTable(target, t1),
                buildReservationTable(target, t2)
        ));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(15L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findByTableIdAndReservationStatusExcludingReservation(10L, 15L, ReservationStatus.IN_PROGRESS))
                .thenReturn(Collections.emptyList()); // 0 matching -> INDETERMINATE

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(15L), USERNAME);

        assertEquals(WaitStatus.INDETERMINATE, response.getWaitStatus());
    }

    @Test
    @DisplayName("TC-16: Unassigned reservation returns UNASSIGNED")
    void unassignedReservation_returnsUnassigned() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(16L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);

        when(reservationRepository.findById(16L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(16L)).thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(16L), USERNAME);

        assertEquals(WaitStatus.UNASSIGNED, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("TC-17: VIP_ROOM reservation returns NOT_SUPPORTED")
    void vipRoom_returnsNotSupported() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(17L, ReservationStatus.CONFIRMED, ReservationType.VIP_ROOM, start, end, USERNAME);

        when(reservationRepository.findById(17L)).thenReturn(Optional.of(target));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(17L), USERNAME);

        assertEquals(WaitStatus.NOT_SUPPORTED, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("TC-18: Ownership mismatch throws AccessDeniedException")
    void ownershipMismatch_throwsAccessDenied() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(18L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, "owner@example.com");

        when(reservationRepository.findById(18L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () ->
                predictionService.predictWaitingTime(new WaitingTimeRequestDTO(18L), "intruder@example.com"));
    }

    @Test
    @DisplayName("TC-19: Inactive reservation (CANCELLED or COMPLETED) returns INACTIVE")
    void inactiveReservation_returnsInactive() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(19L, ReservationStatus.CANCELLED, ReservationType.TABLE, start, end, USERNAME);

        when(reservationRepository.findById(19L)).thenReturn(Optional.of(target));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(19L), USERNAME);

        assertEquals(WaitStatus.INACTIVE, response.getWaitStatus());
        assertNull(response.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("TC-20: Turnaround boundary: booking ending at T_arrival - 15m is excluded from turnaround")
    void turnaroundBoundary_at15mPrior_excluded() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(20L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        when(reservationRepository.findById(20L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(20L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(20L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        // findPrecedingBookingsForTurnaround excludes r.endTime <= tArrival - 15m
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(20L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(20L), USERNAME);

        assertEquals(WaitStatus.SCHEDULED, response.getWaitStatus());
        assertEquals(0, response.getEstimatedWaitMinutes());
    }

    @Test
    @DisplayName("TC-21: Turnaround boundary: booking ending exactly at T_arrival is included (15m delay)")
    void turnaroundBoundary_exactlyAtArrival_included() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(21L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        Reservation prev = buildReservation(99L, ReservationStatus.COMPLETED, ReservationType.TABLE,
                start.minus(Duration.ofHours(1)), start, "other"); // ends exactly at 19:00
        ReservationTable rtPrev = buildReservationTable(prev, table);

        when(reservationRepository.findById(21L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(21L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(21L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(21L), anyCollection(), any(), any()))
                .thenReturn(List.of(rtPrev));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(21L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(15, response.getEstimatedWaitMinutes());
        assertEquals(start.plus(Duration.ofMinutes(15)), response.getEstimatedReadyTime());
    }

    @Test
    @DisplayName("TC-22: Ceiling rounding check: 5m 30s delay rounds up to 6 minutes")
    void ceilingRounding_5m30s_roundsTo6m() {
        Instant start = NOW.plus(Duration.ofHours(1)); // 19:00
        Instant end = start.plus(Duration.ofHours(2));
        Reservation target = buildReservation(22L, ReservationStatus.CONFIRMED, ReservationType.TABLE, start, end, USERNAME);
        RestaurantTable table = buildTable(10L, TableStatus.AVAILABLE);

        // Preceding booking ends at 18:50:30 -> ready at 19:05:30 -> delay is 5m 30s (330s) -> ceil is 6m
        Instant prevEnd = Instant.parse("2026-10-23T18:50:30Z");
        Reservation prev = buildReservation(99L, ReservationStatus.COMPLETED, ReservationType.TABLE,
                start.minus(Duration.ofHours(1)), prevEnd, "other");
        ReservationTable rtPrev = buildReservationTable(prev, table);

        when(reservationRepository.findById(22L)).thenReturn(Optional.of(target));
        when(reservationTableRepository.findByReservationId(22L)).thenReturn(List.of(buildReservationTable(target, table)));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), eq(22L), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationTableRepository.findPrecedingBookingsForTurnaround(eq(10L), eq(22L), anyCollection(), any(), any()))
                .thenReturn(List.of(rtPrev));

        WaitingTimeResponseDTO response = predictionService.predictWaitingTime(new WaitingTimeRequestDTO(22L), USERNAME);

        assertEquals(WaitStatus.WAITING, response.getWaitStatus());
        assertEquals(6, response.getEstimatedWaitMinutes());
        assertEquals(Instant.parse("2026-10-23T19:05:30Z"), response.getEstimatedReadyTime());
    }
}
