package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
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
}