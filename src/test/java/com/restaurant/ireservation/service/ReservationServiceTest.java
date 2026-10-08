package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import com.restaurant.ireservation.repository.ReservationRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @InjectMocks
    ReservationService reservationService;

    RestaurantTable table1;
    RestaurantTable table2;
    Instant now;
    Instant startTime;
    Instant endTime;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        startTime = now.plus(Duration.ofHours(2));
        endTime = now.plus(Duration.ofHours(4));

        table1 = RestaurantTable.builder()
                .tableNumber(10)
                .capacity(4)
                .statusCode(TableStatus.AVAILABLE)
                .build();
        table1.setId(1L);

        table2 = RestaurantTable.builder()
                .tableNumber(11)
                .capacity(2)
                .statusCode(TableStatus.AVAILABLE)
                .build();
        table2.setId(2L);
    }

    @Test
    @DisplayName("TC-01: Successful single table reservation creation")
    void createReservation_success_singleTable() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note("Quiet table please")
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservations(eq(List.of(1L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        Reservation savedEntity = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Quiet table please")
                .build();
        savedEntity.setId(100L);
        savedEntity.setCreatedBy("customer1");
        savedEntity.setCreatedDate(now);

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedEntity);

        ReservationResponseDTO response = reservationService.createReservation(dto, "customer1");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("RES-05102026-001", response.getReservationNo());
        assertEquals(ReservationStatus.PENDING, response.getReservationStatus());
        assertEquals(1, response.getTables().size());
        assertEquals(10, response.getTables().get(0).getTableNumber());
        assertEquals("customer1", response.getCreatedBy());

        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(reservationTableRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("TC-02: Successful multiple tables reservation creation")
    void createReservation_success_multipleTables() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L, 2L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(table1, table2));
        when(reservationTableRepository.findConflictingReservations(eq(List.of(1L, 2L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        Reservation savedEntity = Reservation.builder()
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        savedEntity.setId(101L);
        savedEntity.setCreatedBy("customer2");

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedEntity);

        ReservationResponseDTO response = reservationService.createReservation(dto, "customer2");

        assertNotNull(response);
        assertEquals(2, response.getTables().size());
        assertEquals("RES-05102026-002", response.getReservationNo());
    }

    @Test
    @DisplayName("TC-04: End time before start time should throw RequestInvalidException")
    void createReservation_fail_endTimeBeforeStartTime() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(endTime)
                .endTime(startTime)
                .tableIds(List.of(1L))
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("End time must be after start time", ex.getMessage());
    }

    @Test
    @DisplayName("TC-06: Duplicate table IDs in request should throw RequestInvalidException")
    void createReservation_fail_duplicateTableIds() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L, 1L))
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Duplicate table IDs in request", ex.getMessage());
    }

    @Test
    @DisplayName("TC-07: Non-TABLE reservation type in Phase 1 should throw RequestInvalidException")
    void createReservation_fail_unsupportedReservationType() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Only TABLE reservations are supported in Phase 1", ex.getMessage());
    }

    @Test
    @DisplayName("TC-08: Table not found should throw ResourceNotFoundException")
    void createReservation_fail_tableNotFound() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(999L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("One or more requested tables do not exist", ex.getMessage());
    }

    @Test
    @DisplayName("TC-09: Locked table should throw RequestInvalidException")
    void createReservation_fail_tableLocked() {
        table1.setStatusCode(TableStatus.LOCKED);
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertTrue(ex.getMessage().contains("currently locked"));
    }

    @Test
    @DisplayName("TC-10 / TC-11 / TC-12 / TC-13: Overlapping table conflict should throw ReservationConflictException")
    void createReservation_fail_overlapConflict() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));

        Reservation existingReservation = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        ReservationTable conflict = ReservationTable.builder()
                .reservation(existingReservation)
                .table(table1)
                .build();

        when(reservationTableRepository.findConflictingReservations(eq(List.of(1L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(List.of(conflict));

        ReservationConflictException ex = assertThrows(ReservationConflictException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertTrue(ex.getMessage().contains("already reserved"));
    }

    @Test
    @DisplayName("TC-14: Adjacent time slots are allowed without conflict")
    void createReservation_success_adjacentTimeAllowed() {
        // Request for a slot immediately adjacent to an existing reservation: no conflict returned by query
        Instant adjacentStart = endTime;
        Instant adjacentEnd = adjacentStart.plus(Duration.ofHours(2));

        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(adjacentStart)
                .endTime(adjacentEnd)
                .tableIds(List.of(1L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservations(eq(List.of(1L)), anyCollection(), eq(adjacentStart), eq(adjacentEnd)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);

        Reservation savedEntity = Reservation.builder()
                .reservationNo("RES-05102026-005")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(adjacentStart)
                .endTime(adjacentEnd)
                .build();
        savedEntity.setId(105L);
        savedEntity.setCreatedBy("customer1");

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedEntity);

        ReservationResponseDTO response = reservationService.createReservation(dto, "customer1");

        assertNotNull(response);
        assertEquals(105L, response.getId());
        assertEquals("RES-05102026-005", response.getReservationNo());
    }

    @Test
    @DisplayName("TC-15: Completed or No-show reservations do not block table availability")
    @SuppressWarnings("unchecked")
    void createReservation_success_completedAndNoShowDoNotBlock() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));

        // Capture blocking statuses passed to repository
        ArgumentCaptor<Collection<ReservationStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        when(reservationTableRepository.findConflictingReservations(eq(List.of(1L)), statusCaptor.capture(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(10L);

        Reservation savedEntity = Reservation.builder()
                .reservationNo("RES-05102026-010")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        savedEntity.setId(110L);
        savedEntity.setCreatedBy("customer1");

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedEntity);

        ReservationResponseDTO response = reservationService.createReservation(dto, "customer1");

        assertNotNull(response);
        // Verify that COMPLETED and NO_SHOW are strictly NOT in the blocking statuses list
        Collection<ReservationStatus> queriedStatuses = statusCaptor.getValue();
        assertTrue(queriedStatuses.contains(ReservationStatus.PENDING));
        assertTrue(queriedStatuses.contains(ReservationStatus.CONFIRMED));
        assertTrue(queriedStatuses.contains(ReservationStatus.IN_PROGRESS));
        assertFalse(queriedStatuses.contains(ReservationStatus.COMPLETED));
        assertFalse(queriedStatuses.contains(ReservationStatus.NO_SHOW));
    }

    // ==========================================
    // PHASE 2: UPDATE RESERVATION TESTS
    // ==========================================

    @Test
    @DisplayName("P2-TC-01: Successful reservation update for a single table")
    void updateReservation_success_singleTable() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Old note")
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");
        existing.setCreatedDate(now);

        Instant newStartTime = startTime.plus(Duration.ofHours(1));
        Instant newEndTime = endTime.plus(Duration.ofHours(1));
        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.TABLE)
                .startTime(newStartTime)
                .endTime(newEndTime)
                .tableIds(List.of(1L))
                .note("Updated note")
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), eq(newStartTime), eq(newEndTime)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");

        assertNotNull(response);
        assertEquals(reservationId, response.getId());
        assertEquals("RES-05102026-001", response.getReservationNo());
        assertEquals(PartySize.MEDIUM, response.getPartySize());
        assertEquals("Updated note", response.getNote());
        assertEquals(ReservationStatus.PENDING, response.getReservationStatus());
        assertEquals(1, response.getTables().size());
        assertEquals(10, response.getTables().get(0).getTableNumber());
        assertEquals("customer1", response.getCreatedBy());

        verify(reservationTableRepository).deleteByReservationId(reservationId);
        verify(reservationTableRepository).saveAll(anyList());
        verify(reservationRepository).save(existing);
    }

    @Test
    @DisplayName("P2-TC-02: Update fields preserves immutable fields (id, reservationNo, reservationStatus, createdBy, createdDate)")
    void updateReservation_success_preservesImmutableFields() {
        Long reservationId = 2L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Old")
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");
        existing.setCreatedDate(now);

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime.plus(Duration.ofHours(1)))
                .endTime(endTime.plus(Duration.ofHours(1)))
                .tableIds(List.of(1L))
                .note("New")
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");

        assertEquals(reservationId, response.getId());
        assertEquals("RES-05102026-002", response.getReservationNo());
        assertEquals(ReservationStatus.PENDING, response.getReservationStatus());
        assertEquals("customer1", response.getCreatedBy());
        assertEquals(now, response.getCreatedDate());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("P2-TC-03: Update replaces table associations correctly")
    void updateReservation_success_updatesTables() {
        Long reservationId = 3L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-003")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(2L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(2L))).thenReturn(List.of(table2));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(2L)), eq(reservationId), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");

        assertEquals(1, response.getTables().size());
        assertEquals(2L, response.getTables().get(0).getId());
        assertEquals(11, response.getTables().get(0).getTableNumber());

        verify(reservationTableRepository).deleteByReservationId(reservationId);
        verify(reservationTableRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("P2-TC-04: Self conflict is excluded when updating with same tables and overlapping slot")
    void updateReservation_success_selfConflictExcluded() {
        Long reservationId = 4L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-004")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime.plus(Duration.ofMinutes(30)))
                .endTime(endTime.plus(Duration.ofMinutes(30)))
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");
        assertNotNull(response);

        verify(reservationTableRepository).findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), any(), any());
    }

    @Test
    @DisplayName("P2-TC-05: Adjacent time slot allowed without conflict during update")
    void updateReservation_success_adjacentTimeSlotAllowed() {
        Long reservationId = 5L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-005")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        Instant adjacentStart = endTime;
        Instant adjacentEnd = adjacentStart.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(adjacentStart)
                .endTime(adjacentEnd)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), eq(adjacentStart), eq(adjacentEnd)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");
        assertNotNull(response);
    }

    @Test
    @DisplayName("P2-TC-06: Reservation not found should throw ResourceNotFoundException")
    void updateReservation_fail_notFound() {
        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.updateReservation(999L, dto, "customer1"));
        assertEquals("Reservation not found with id: 999", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-07: Ownership mismatch should throw AccessDeniedException")
    void updateReservation_fail_ownershipMismatch() {
        Long reservationId = 7L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-007")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("original_customer");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.updateReservation(reservationId, dto, "other_customer"));
        assertEquals("You are not allowed to update this reservation", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-07B: Null createdBy should throw AccessDeniedException")
    void updateReservation_fail_nullCreatedBy() {
        Long reservationId = 71L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-071")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy(null);

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
    }

    @Test
    @DisplayName("P2-TC-08: PENDING status allowed for update")
    void updateReservation_success_pendingStatusAllowed() {
        Long reservationId = 8L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-008")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> reservationService.updateReservation(reservationId, dto, "customer1"));
    }

    @Test
    @DisplayName("P2-TC-09: CONFIRMED status rejected with RequestInvalidException")
    void updateReservation_fail_confirmedStatusRejected() {
        Long reservationId = 9L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-009")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only reservations with PENDING status can be updated", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-10: IN_PROGRESS status rejected with RequestInvalidException")
    void updateReservation_fail_inProgressStatusRejected() {
        Long reservationId = 10L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-010")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only reservations with PENDING status can be updated", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-11: COMPLETED status rejected with RequestInvalidException")
    void updateReservation_fail_completedStatusRejected() {
        Long reservationId = 11L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-011")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.COMPLETED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only reservations with PENDING status can be updated", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-12: NO_SHOW status rejected with RequestInvalidException")
    void updateReservation_fail_noShowStatusRejected() {
        Long reservationId = 12L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-012")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.NO_SHOW)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only reservations with PENDING status can be updated", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-13: End time before or equal to start time should throw RequestInvalidException")
    void updateReservation_fail_endTimeBeforeStartTime() {
        Long reservationId = 13L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-013")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(endTime)
                .endTime(startTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("End time must be after start time", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-14: Unsupported reservation type should throw RequestInvalidException")
    void updateReservation_fail_unsupportedReservationType() {
        Long reservationId = 14L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-014")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only TABLE reservations are supported", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-15: Duplicate table IDs in request should throw RequestInvalidException")
    void updateReservation_fail_duplicateTableIds() {
        Long reservationId = 15L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-015")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L, 1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Duplicate table IDs in request", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-16: Table not found should throw ResourceNotFoundException")
    void updateReservation_fail_tableNotFound() {
        Long reservationId = 16L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-016")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(999L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("One or more requested tables do not exist", ex.getMessage());
    }

    @Test
    @DisplayName("P2-TC-17: Locked table should throw RequestInvalidException")
    void updateReservation_fail_tableLocked() {
        Long reservationId = 17L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-017")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        RestaurantTable lockedTable = RestaurantTable.builder()
                .tableNumber(10)
                .capacity(4)
                .statusCode(TableStatus.LOCKED)
                .build();
        lockedTable.setId(1L);

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(lockedTable));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertTrue(ex.getMessage().contains("currently locked"));
    }

    @Test
    @DisplayName("P2-TC-18: Overlapping conflict with another active reservation should throw ReservationConflictException")
    void updateReservation_fail_conflictWithOtherActiveReservation() {
        Long reservationId = 18L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-018")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));

        Reservation otherReservation = Reservation.builder()
                .reservationNo("RES-05102026-999")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        otherReservation.setId(99L);

        ReservationTable conflictTable = ReservationTable.builder()
                .reservation(otherReservation)
                .table(table1)
                .build();

        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(List.of(conflictTable));

        ReservationConflictException ex = assertThrows(ReservationConflictException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertTrue(ex.getMessage().contains("already reserved"));
    }

    @Test
    @DisplayName("P2-TC-19: COMPLETED and NO_SHOW reservations do not block during update")
    @SuppressWarnings("unchecked")
    void updateReservation_success_completedAndNoShowDoNotBlock() {
        Long reservationId = 19L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-019")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));

        ArgumentCaptor<Collection<ReservationStatus>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), statusCaptor.capture(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");
        assertNotNull(response);

        Collection<ReservationStatus> queriedStatuses = statusCaptor.getValue();
        assertTrue(queriedStatuses.contains(ReservationStatus.PENDING));
        assertTrue(queriedStatuses.contains(ReservationStatus.CONFIRMED));
        assertTrue(queriedStatuses.contains(ReservationStatus.IN_PROGRESS));
        assertFalse(queriedStatuses.contains(ReservationStatus.COMPLETED));
        assertFalse(queriedStatuses.contains(ReservationStatus.NO_SHOW));
    }

    @Test
    @DisplayName("P2-TC-20: Update succeeds with null note or valid note")
    void updateReservation_success_nullNote() {
        Long reservationId = 20L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-020")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Had a note")
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note(null)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(restaurantTableRepository.findAllById(List.of(1L))).thenReturn(List.of(table1));
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(
                eq(List.of(1L)), eq(reservationId), anyCollection(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");
        assertNotNull(response);
        assertNull(response.getNote());
    }
}