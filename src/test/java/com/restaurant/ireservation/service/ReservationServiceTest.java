package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationRoom;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.RoomReservationBookingRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.RoomRepository;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
    ReservationRoomRepository reservationRoomRepository;

    @Mock
    RoomRepository roomRepository;

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
    Instant pastStartTime;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        startTime = now.plus(Duration.ofHours(2));
        endTime = now.plus(Duration.ofHours(4));
        pastStartTime = now.minus(Duration.ofMinutes(15));
        reservationService.setClock(Clock.systemUTC());

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

    @AfterEach
    void tearDown() {
        reservationService.setClock(Clock.systemUTC());
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String authority) {
        Authentication auth = mock(Authentication.class);
        lenient().doReturn(List.of(new SimpleGrantedAuthority(authority))).when(auth).getAuthorities();
        SecurityContext context = mock(SecurityContext.class);
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
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
    @DisplayName("TC-07: Non-TABLE and non-VIP_ROOM reservation type should throw RequestInvalidException")
    void createReservation_fail_unsupportedReservationType() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.PRIVATE_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Only TABLE and VIP_ROOM reservations are supported", ex.getMessage());
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
                .reservationType(ReservationType.PRIVATE_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.updateReservation(reservationId, dto, "customer1"));
        assertEquals("Only TABLE and VIP_ROOM reservations are supported", ex.getMessage());
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

    @Test
    @DisplayName("P3-TC-01: Successfully cancel reservation with PENDING status")
    void cancelReservation_success_fromPending() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Quiet table")
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        ReservationTable resTable = ReservationTable.builder()
                .reservation(existing)
                .table(table1)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(List.of(resTable));

        ReservationResponseDTO response = reservationService.cancelReservation(reservationId, "customer1");

        assertNotNull(response);
        assertEquals(reservationId, response.getId());
        assertEquals(ReservationStatus.CANCELLED, response.getReservationStatus());
        assertEquals(1, response.getTables().size());
        assertEquals(10, response.getTables().get(0).getTableNumber());

        verify(reservationRepository).save(argThat(r -> r.getReservationStatus() == ReservationStatus.CANCELLED));
        verify(reservationTableRepository, never()).deleteByReservationId(anyLong());
        verify(reservationTableRepository, never()).delete(any());
        verify(reservationTableRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("P3-TC-02: Successfully cancel reservation with CONFIRMED status")
    void cancelReservation_success_fromConfirmed() {
        Long reservationId = 2L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.cancelReservation(reservationId, "customer1");

        assertNotNull(response);
        assertEquals(ReservationStatus.CANCELLED, response.getReservationStatus());
        verify(reservationRepository).save(argThat(r -> r.getReservationStatus() == ReservationStatus.CANCELLED));
    }

    @Test
    @DisplayName("P3-TC-03: Cancellation fails with 404 when reservation does not exist")
    void cancelReservation_fail_notFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.cancelReservation(999L, "customer1"));

        assertEquals("Reservation not found with id: 999", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-04: Cancellation fails with 403 when user is not the creator")
    void cancelReservation_fail_ownershipMismatch() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer2");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("You are not allowed to cancel this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-05: Cancellation fails with 403 when reservation createdBy is null")
    void cancelReservation_fail_nullCreatedBy() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy(null);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("You are not allowed to cancel this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-06: Cancellation fails when reservation is already CANCELLED")
    void cancelReservation_fail_alreadyCancelled() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.CANCELLED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("Reservation is already cancelled", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-07: Cancellation fails when reservation is IN_PROGRESS")
    void cancelReservation_fail_inProgress() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be cancelled", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-08: Cancellation fails when reservation is COMPLETED")
    void cancelReservation_fail_completed() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.COMPLETED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be cancelled", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-09: Cancellation fails when reservation is NO_SHOW")
    void cancelReservation_fail_noShow() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .reservationStatus(ReservationStatus.NO_SHOW)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.cancelReservation(reservationId, "customer1"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be cancelled", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-TC-10: Cancellation retains ReservationTable records for historical audit")
    void cancelReservation_preservesTableLinks() {
        Long reservationId = 10L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-010")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        ReservationTable resTable1 = ReservationTable.builder().reservation(existing).table(table1).build();
        ReservationTable resTable2 = ReservationTable.builder().reservation(existing).table(table2).build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(List.of(resTable1, resTable2));

        ReservationResponseDTO response = reservationService.cancelReservation(reservationId, "customer1");

        assertNotNull(response);
        assertEquals(2, response.getTables().size());
        verify(reservationTableRepository).findByReservationId(reservationId);
        verify(reservationTableRepository, never()).deleteByReservationId(anyLong());
        verify(reservationTableRepository, never()).delete(any());
        verify(reservationTableRepository, never()).deleteAll(any());
    }

    // ==========================================
    // Phase 4: Check-in Tests
    // ==========================================

    @Test
    @DisplayName("P4-TC-01: Successfully check in reservation with PENDING status")
    void checkInReservation_success_fromPending() {
        Long reservationId = 1L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(pastStartTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.IN_PROGRESS, response.getReservationStatus());
        verify(reservationRepository).save(argThat(r -> r.getReservationStatus() == ReservationStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("P4-TC-02: Successfully check in reservation with CONFIRMED status")
    void checkInReservation_success_fromConfirmed() {
        Long reservationId = 2L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(pastStartTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.IN_PROGRESS, response.getReservationStatus());
        verify(reservationRepository).save(argThat(r -> r.getReservationStatus() == ReservationStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("P4-TC-02b: Check-in fails when reservation is scheduled in the future")
    void checkInReservation_fail_scheduledInFuture() {
        Long reservationId = 201L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-201")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(now.plus(Duration.ofHours(1)))
                .endTime(now.plus(Duration.ofHours(3)))
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Cannot check in before the scheduled reservation time", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-02c: Check-in fails when reservation startTime is null")
    void checkInReservation_fail_nullStartTime() {
        Long reservationId = 202L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-202")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(null)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Cannot check in before the scheduled reservation time", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-02d: Check-in succeeds at exact startTime boundary using controllable Clock")
    void checkInReservation_success_exactStartTimeBoundary() {
        Long reservationId = 203L;
        Instant boundaryTime = Instant.parse("2026-10-15T12:00:00Z");
        reservationService.setClock(Clock.fixed(boundaryTime, ZoneOffset.UTC));

        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-203")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(boundaryTime)
                .endTime(boundaryTime.plus(Duration.ofHours(2)))
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.IN_PROGRESS, response.getReservationStatus());
    }

    @Test
    @DisplayName("P4-TC-02e: Check-in succeeds when current time is past startTime")
    void checkInReservation_success_pastStartTime() {
        Long reservationId = 204L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-204")
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(now.minus(Duration.ofMinutes(30)))
                .endTime(now.plus(Duration.ofHours(1)))
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.IN_PROGRESS, response.getReservationStatus());
    }

    @Test
    @DisplayName("P4-TC-03: Check-in fails when reservation is already IN_PROGRESS")
    void checkInReservation_fail_alreadyInProgress() {
        Long reservationId = 3L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-003")
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Reservation is already checked in", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-04: Check-in fails when reservation status is COMPLETED")
    void checkInReservation_fail_completedStatus() {
        Long reservationId = 4L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-004")
                .reservationStatus(ReservationStatus.COMPLETED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be checked in", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-05: Check-in fails when reservation status is NO_SHOW")
    void checkInReservation_fail_noShowStatus() {
        Long reservationId = 5L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-005")
                .reservationStatus(ReservationStatus.NO_SHOW)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be checked in", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-06: Check-in fails when reservation status is CANCELLED")
    void checkInReservation_fail_cancelledStatus() {
        Long reservationId = 6L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-006")
                .reservationStatus(ReservationStatus.CANCELLED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.checkInReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with PENDING or CONFIRMED status can be checked in", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-07: Check-in fails with 404 when reservation does not exist")
    void checkInReservation_fail_notFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.checkInReservation(999L, "admin_user"));

        assertEquals("Reservation not found with id: 999", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-08: Customer owner fails check-in with 403 AccessDeniedException")
    void checkInReservation_customerOwner_forbidden() {
        Long reservationId = 8L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-008")
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(pastStartTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.CUSTOMER);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.checkInReservation(reservationId, "customer1"));

        assertEquals("You are not allowed to check in this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-09: Customer non-owner fails check-in with 403 AccessDeniedException")
    void checkInReservation_customerNonOwner_forbidden() {
        Long reservationId = 9L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-009")
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.CUSTOMER);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.checkInReservation(reservationId, "customer2"));

        assertEquals("You are not allowed to check in this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-12: Admin can check in another customer's reservation")
    void checkInReservation_admin_canOperateOnOtherCustomer() {
        Long reservationId = 12L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-012")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(pastStartTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.IN_PROGRESS, response.getReservationStatus());
    }

    @Test
    @DisplayName("P4-TC-13: Check-in retains ReservationTable records and returns table list")
    void checkInReservation_preservesTableLinks() {
        Long reservationId = 13L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-013")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(pastStartTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        ReservationTable resTable1 = ReservationTable.builder().reservation(existing).table(table1).build();
        ReservationTable resTable2 = ReservationTable.builder().reservation(existing).table(table2).build();

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(List.of(resTable1, resTable2));

        ReservationResponseDTO response = reservationService.checkInReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(2, response.getTables().size());
        verify(reservationTableRepository).findByReservationId(reservationId);
        verify(reservationTableRepository, never()).deleteByReservationId(anyLong());
        verify(reservationTableRepository, never()).delete(any());
        verify(reservationTableRepository, never()).deleteAll(any());
    }

    // ==========================================
    // Phase 4: Complete Tests
    // ==========================================

    @Test
    @DisplayName("P4-TC-14: Successfully complete reservation with IN_PROGRESS status")
    void completeReservation_success_fromInProgress() {
        Long reservationId = 14L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-014")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.completeReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.COMPLETED, response.getReservationStatus());
        verify(reservationRepository).save(argThat(r -> r.getReservationStatus() == ReservationStatus.COMPLETED));
    }

    @Test
    @DisplayName("P4-TC-15: Complete fails when reservation status is PENDING")
    void completeReservation_fail_pendingStatus() {
        Long reservationId = 15L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-015")
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.completeReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with IN_PROGRESS status can be completed", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-16: Complete fails when reservation status is CONFIRMED")
    void completeReservation_fail_confirmedStatus() {
        Long reservationId = 16L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-016")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.completeReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with IN_PROGRESS status can be completed", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-17: Complete fails when reservation is already COMPLETED")
    void completeReservation_fail_alreadyCompleted() {
        Long reservationId = 17L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-017")
                .reservationStatus(ReservationStatus.COMPLETED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.completeReservation(reservationId, "admin_user"));

        assertEquals("Reservation is already completed", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-18: Complete fails when reservation status is NO_SHOW")
    void completeReservation_fail_noShowStatus() {
        Long reservationId = 18L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-018")
                .reservationStatus(ReservationStatus.NO_SHOW)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.completeReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with IN_PROGRESS status can be completed", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-19: Complete fails when reservation status is CANCELLED")
    void completeReservation_fail_cancelledStatus() {
        Long reservationId = 19L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-019")
                .reservationStatus(ReservationStatus.CANCELLED)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.completeReservation(reservationId, "admin_user"));

        assertEquals("Only reservations with IN_PROGRESS status can be completed", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-20: Complete fails with 404 when reservation does not exist")
    void completeReservation_fail_notFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.completeReservation(999L, "admin_user"));

        assertEquals("Reservation not found with id: 999", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-21: Customer owner fails completion with 403 AccessDeniedException")
    void completeReservation_customerOwner_forbidden() {
        Long reservationId = 21L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-021")
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.CUSTOMER);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.completeReservation(reservationId, "customer1"));

        assertEquals("You are not allowed to complete this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-22: Customer non-owner fails completion with 403 AccessDeniedException")
    void completeReservation_customerNonOwner_forbidden() {
        Long reservationId = 22L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-022")
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.CUSTOMER);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reservationService.completeReservation(reservationId, "customer2"));

        assertEquals("You are not allowed to complete this reservation", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("P4-TC-25: Admin can complete another customer's reservation")
    void completeReservation_admin_canOperateOnOtherCustomer() {
        Long reservationId = 25L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-025")
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        ReservationResponseDTO response = reservationService.completeReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(ReservationStatus.COMPLETED, response.getReservationStatus());
    }

    @Test
    @DisplayName("P4-TC-26: Complete retains ReservationTable records and returns table list")
    void completeReservation_preservesTableLinks() {
        Long reservationId = 26L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-026")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        ReservationTable resTable1 = ReservationTable.builder().reservation(existing).table(table1).build();
        ReservationTable resTable2 = ReservationTable.builder().reservation(existing).table(table2).build();

        mockSecurityContext(AuthoritiesConstants.ADMIN);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationTableRepository.findByReservationId(reservationId)).thenReturn(List.of(resTable1, resTable2));

        ReservationResponseDTO response = reservationService.completeReservation(reservationId, "admin_user");

        assertNotNull(response);
        assertEquals(2, response.getTables().size());
        verify(reservationTableRepository).findByReservationId(reservationId);
        verify(reservationTableRepository, never()).deleteByReservationId(anyLong());
        verify(reservationTableRepository, never()).delete(any());
        verify(reservationTableRepository, never()).deleteAll(any());
    }

    // ==========================================
    // Phase 5: Customer Reservation History Tests
    // ==========================================

    @Test
    @DisplayName("P5-TC-01: Successfully retrieve customer reservation history")
    void getMyReservations_success_returnsCustomerReservations() {
        Reservation res1 = Reservation.builder()
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Near window")
                .build();
        res1.setId(1L);
        res1.setCreatedBy("customer1");

        Reservation res2 = Reservation.builder()
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime.plus(Duration.ofDays(1)))
                .endTime(endTime.plus(Duration.ofDays(1)))
                .build();
        res2.setId(2L);
        res2.setCreatedBy("customer1");

        ReservationTable rt1 = ReservationTable.builder().reservation(res1).table(table1).build();
        ReservationTable rt2 = ReservationTable.builder().reservation(res2).table(table2).build();

        when(reservationRepository.findByCreatedBy("customer1")).thenReturn(List.of(res1, res2));
        when(reservationTableRepository.findByReservationId(1L)).thenReturn(List.of(rt1));
        when(reservationTableRepository.findByReservationId(2L)).thenReturn(List.of(rt2));

        List<ReservationResponseDTO> result = reservationService.getMyReservations("customer1");

        assertNotNull(result);
        assertEquals(2, result.size());

        ReservationResponseDTO dto1 = result.get(0);
        assertEquals(1L, dto1.getId());
        assertEquals("RES-05102026-001", dto1.getReservationNo());
        assertEquals(ReservationStatus.PENDING, dto1.getReservationStatus());
        assertEquals("customer1", dto1.getCreatedBy());
        assertEquals(1, dto1.getTables().size());
        assertEquals(10, dto1.getTables().get(0).getTableNumber());

        ReservationResponseDTO dto2 = result.get(1);
        assertEquals(2L, dto2.getId());
        assertEquals("RES-05102026-002", dto2.getReservationNo());
        assertEquals(ReservationStatus.CONFIRMED, dto2.getReservationStatus());
        assertEquals("customer1", dto2.getCreatedBy());
        assertEquals(1, dto2.getTables().size());
        assertEquals(11, dto2.getTables().get(0).getTableNumber());

        verify(reservationRepository).findByCreatedBy("customer1");
    }

    @Test
    @DisplayName("P5-TC-02: Isolated - queries only reservations created by authenticated customer")
    void getMyReservations_isolated_onlyReturnsTargetCustomerReservations() {
        when(reservationRepository.findByCreatedBy("customer_alice")).thenReturn(Collections.emptyList());

        List<ReservationResponseDTO> result = reservationService.getMyReservations("customer_alice");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(reservationRepository).findByCreatedBy("customer_alice");
        verify(reservationRepository, never()).findByCreatedBy("customer_bob");
    }

    @Test
    @DisplayName("P5-TC-03: Customer with no reservations receives empty list")
    void getMyReservations_noReservations_returnsEmptyList() {
        when(reservationRepository.findByCreatedBy("customer_new")).thenReturn(Collections.emptyList());

        List<ReservationResponseDTO> result = reservationService.getMyReservations("customer_new");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(reservationRepository).findByCreatedBy("customer_new");
        verify(reservationTableRepository, never()).findByReservationId(anyLong());
    }

    @Test
    @DisplayName("P5-TC-04: Multiple reservations with various statuses are all included in history")
    void getMyReservations_multipleStatuses_allIncluded() {
        Reservation r1 = Reservation.builder().reservationNo("R-1").reservationStatus(ReservationStatus.PENDING).build();
        r1.setId(1L);
        r1.setCreatedBy("customer1");
        Reservation r2 = Reservation.builder().reservationNo("R-2").reservationStatus(ReservationStatus.CONFIRMED).build();
        r2.setId(2L);
        r2.setCreatedBy("customer1");
        Reservation r3 = Reservation.builder().reservationNo("R-3").reservationStatus(ReservationStatus.CANCELLED).build();
        r3.setId(3L);
        r3.setCreatedBy("customer1");
        Reservation r4 = Reservation.builder().reservationNo("R-4").reservationStatus(ReservationStatus.COMPLETED).build();
        r4.setId(4L);
        r4.setCreatedBy("customer1");

        when(reservationRepository.findByCreatedBy("customer1")).thenReturn(List.of(r1, r2, r3, r4));
        when(reservationTableRepository.findByReservationId(anyLong())).thenReturn(Collections.emptyList());

        List<ReservationResponseDTO> result = reservationService.getMyReservations("customer1");

        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(ReservationStatus.PENDING, result.get(0).getReservationStatus());
        assertEquals(ReservationStatus.CONFIRMED, result.get(1).getReservationStatus());
        assertEquals(ReservationStatus.CANCELLED, result.get(2).getReservationStatus());
        assertEquals(ReservationStatus.COMPLETED, result.get(3).getReservationStatus());
    }

    @Test
    @DisplayName("P5-TC-05: Blank or null username throws AccessDeniedException")
    void getMyReservations_unauthenticated_nullOrBlankUsername_throwsAccessDeniedException() {
        assertThrows(AccessDeniedException.class, () -> reservationService.getMyReservations(null));
        assertThrows(AccessDeniedException.class, () -> reservationService.getMyReservations("   "));
        verify(reservationRepository, never()).findByCreatedBy(anyString());
    }

    // ==========================================
    // VIP Room Reservation Tests [4]
    // ==========================================

    @Test
    @DisplayName("VIP-TC-01: Successfully create VIP room reservation")
    void createReservation_vipRoom_success() {
        Room vipRoom = Room.builder()
                .roomName("VIP Suite 1")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(300.0)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        vipRoom.setId(10L);

        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .note("Executive lunch")
                .build();

        when(roomRepository.findAllById(List.of(10L))).thenReturn(List.of(vipRoom));
        when(reservationRoomRepository.findConflictingReservations(eq(List.of(10L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReservationResponseDTO response = reservationService.createReservation(dto, "customer1");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(ReservationType.VIP_ROOM, response.getReservationType());
        assertEquals(ReservationStatus.PENDING, response.getReservationStatus());
        assertEquals("customer1", response.getCreatedBy());
        assertTrue(response.getTables().isEmpty());
        assertEquals(1, response.getRooms().size());
        assertEquals(10L, response.getRooms().get(0).getId());
        assertEquals("VIP Suite 1", response.getRooms().get(0).getRoomName());
        assertEquals(300.0, response.getRooms().get(0).getPrice());

        verify(reservationRoomRepository).saveAll(anyList());
        verify(reservationTableRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("VIP-TC-02: Missing roomIds in VIP reservation should throw RequestInvalidException")
    void createReservation_vipRoom_fail_noRoomIds() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(null)
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("At least one VIP room must be selected", ex.getMessage());

        CreateReservationRequestDTO dtoEmpty = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(Collections.emptyList())
                .build();

        RequestInvalidException exEmpty = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dtoEmpty, "customer1"));
        assertEquals("At least one VIP room must be selected", exEmpty.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-03: Duplicate roomIds in VIP reservation should throw RequestInvalidException")
    void createReservation_vipRoom_fail_duplicateRoomIds() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L, 10L))
                .build();

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Duplicate room IDs in request", ex.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-04: Non-existent room in VIP reservation should throw ResourceNotFoundException")
    void createReservation_vipRoom_fail_roomNotFound() {
        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(999L))
                .build();

        when(roomRepository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("One or more requested rooms do not exist", ex.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-05: Non-VIP room type in VIP reservation should throw RequestInvalidException")
    void createReservation_vipRoom_fail_notVipRoom() {
        Room privateRoom = Room.builder()
                .roomName("Private Room 1")
                .roomType(RoomType.PRIVATE_ROOM)
                .capacity(10)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        privateRoom.setId(20L);

        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(20L))
                .build();

        when(roomRepository.findAllById(List.of(20L))).thenReturn(List.of(privateRoom));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Room Private Room 1 is not a VIP room", ex.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-06: Maintenance room in VIP reservation should throw RequestInvalidException")
    void createReservation_vipRoom_fail_maintenanceRoom() {
        Room maintenanceRoom = Room.builder()
                .roomName("VIP Diamond")
                .roomType(RoomType.VIP_ROOM)
                .capacity(15)
                .roomStatus(RoomStatus.MAINTENANCE)
                .build();
        maintenanceRoom.setId(30L);

        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(30L))
                .build();

        when(roomRepository.findAllById(List.of(30L))).thenReturn(List.of(maintenanceRoom));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Room VIP Diamond is currently under maintenance", ex.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-07: Conflicting VIP room reservation should throw ReservationConflictException")
    void createReservation_vipRoom_fail_timeConflict() {
        Room vipRoom = Room.builder()
                .roomName("VIP Diamond")
                .roomType(RoomType.VIP_ROOM)
                .capacity(15)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        vipRoom.setId(30L);

        Reservation existingReservation = Reservation.builder()
                .reservationNo("RES-EXISTING")
                .reservationStatus(ReservationStatus.CONFIRMED)
                .build();

        ReservationRoom conflict = ReservationRoom.builder()
                .reservation(existingReservation)
                .room(vipRoom)
                .build();

        CreateReservationRequestDTO dto = CreateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(30L))
                .build();

        when(roomRepository.findAllById(List.of(30L))).thenReturn(List.of(vipRoom));
        when(reservationRoomRepository.findConflictingReservations(eq(List.of(30L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(List.of(conflict));

        ReservationConflictException ex = assertThrows(ReservationConflictException.class,
                () -> reservationService.createReservation(dto, "customer1"));
        assertEquals("Room VIP Diamond is already reserved during the requested time slot", ex.getMessage());
    }

    @Test
    @DisplayName("VIP-TC-08: Successfully update VIP room reservation")
    void updateReservation_vipRoom_success() {
        Long reservationId = 50L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-050")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        Room newVipRoom = Room.builder()
                .roomName("VIP Gold")
                .roomType(RoomType.VIP_ROOM)
                .capacity(16)
                .price(400.0)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        newVipRoom.setId(12L);

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.XLARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(12L))
                .note("Updated to VIP Gold")
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(roomRepository.findAllById(List.of(12L))).thenReturn(List.of(newVipRoom));
        when(reservationRoomRepository.findConflictingReservationsExcludingReservation(eq(List.of(12L)), eq(reservationId), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");

        assertNotNull(response);
        assertEquals(PartySize.XLARGE, response.getPartySize());
        assertEquals("Updated to VIP Gold", response.getNote());
        assertEquals(1, response.getRooms().size());
        assertEquals(12L, response.getRooms().get(0).getId());
        assertEquals("VIP Gold", response.getRooms().get(0).getRoomName());

        verify(reservationRoomRepository).deleteByReservationId(reservationId);
        verify(reservationTableRepository).deleteByReservationId(reservationId);
        verify(reservationRoomRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("VIP-TC-09: Update VIP reservation conflict excluding self allows keeping same room")
    void updateReservation_vipRoom_selfConflictExcluded() {
        Long reservationId = 50L;
        Reservation existing = Reservation.builder()
                .reservationNo("RES-05102026-050")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        existing.setId(reservationId);
        existing.setCreatedBy("customer1");

        Room sameRoom = Room.builder()
                .roomName("VIP Gold")
                .roomType(RoomType.VIP_ROOM)
                .capacity(16)
                .price(400.0)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        sameRoom.setId(12L);

        UpdateReservationRequestDTO dto = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(12L))
                .note("Adjusted note only")
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(existing));
        when(roomRepository.findAllById(List.of(12L))).thenReturn(List.of(sameRoom));
        when(reservationRoomRepository.findConflictingReservationsExcludingReservation(eq(List.of(12L)), eq(reservationId), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.updateReservation(reservationId, dto, "customer1");

        assertNotNull(response);
        assertEquals("Adjusted note only", response.getNote());
        assertEquals(1, response.getRooms().size());
        assertEquals(12L, response.getRooms().get(0).getId());
    }

    @Test
    @DisplayName("VIP-TC-10: getMyReservations correctly maps VIP room details")
    void getMyReservations_vipRoom_success() {
        Reservation vipRes = Reservation.builder()
                .reservationNo("RES-VIP-01")
                .partySize(PartySize.LARGE)
                .reservationType(ReservationType.VIP_ROOM)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        vipRes.setId(77L);
        vipRes.setCreatedBy("customer1");

        Room room = Room.builder()
                .roomName("VIP Palace")
                .roomType(RoomType.VIP_ROOM)
                .capacity(20)
                .price(500.0)
                .build();
        room.setId(5L);

        ReservationRoom resRoom = ReservationRoom.builder()
                .reservation(vipRes)
                .room(room)
                .build();

        when(reservationRepository.findByCreatedBy("customer1")).thenReturn(List.of(vipRes));
        when(reservationTableRepository.findByReservationId(77L)).thenReturn(Collections.emptyList());
        when(reservationRoomRepository.findByReservationId(77L)).thenReturn(List.of(resRoom));

        List<ReservationResponseDTO> result = reservationService.getMyReservations("customer1");

        assertNotNull(result);
        assertEquals(1, result.size());
        ReservationResponseDTO res = result.get(0);
        assertEquals("RES-VIP-01", res.getReservationNo());
        assertEquals(ReservationType.VIP_ROOM, res.getReservationType());
        assertTrue(res.getTables().isEmpty());
        assertEquals(1, res.getRooms().size());
        assertEquals("VIP Palace", res.getRooms().get(0).getRoomName());
        assertEquals(500.0, res.getRooms().get(0).getPrice());
    }

    @Test
    @DisplayName("Dedicated Room Reservation: createRoomReservation successfully creates VIP room reservation")
    void createRoomReservation_success() {
        Room vipRoom = Room.builder()
                .roomName("Presidential Suite")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(600.0)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        vipRoom.setId(88L);

        RoomReservationBookingRequestDTO bookingDTO = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.LARGE)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(88L))
                .note("Executive meeting")
                .build();

        when(roomRepository.findAllById(List.of(88L))).thenReturn(List.of(vipRoom));
        when(reservationRoomRepository.findConflictingReservations(eq(List.of(88L)), anyCollection(), eq(startTime), eq(endTime)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(888L);
            return r;
        });

        ReservationResponseDTO response = reservationService.createRoomReservation(bookingDTO, "customer1");

        assertNotNull(response);
        assertEquals(888L, response.getId());
        assertEquals(ReservationType.VIP_ROOM, response.getReservationType());
        assertEquals(ReservationStatus.PENDING, response.getReservationStatus());
        assertEquals("customer1", response.getCreatedBy());
        assertEquals("Executive meeting", response.getNote());
        assertTrue(response.getTables().isEmpty());
        assertEquals(1, response.getRooms().size());
        assertEquals(88L, response.getRooms().get(0).getId());
        assertEquals("Presidential Suite", response.getRooms().get(0).getRoomName());

        verify(reservationRoomRepository).saveAll(anyList());
        verify(reservationTableRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Dedicated Room Reservation: createRoomReservation rejects non-VIP room")
    void createRoomReservation_rejectNonVipRoom() {
        Room standardRoom = Room.builder()
                .roomName("General Dining Room")
                .roomType(RoomType.PRIVATE_ROOM)
                .capacity(8)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        standardRoom.setId(99L);

        RoomReservationBookingRequestDTO bookingDTO = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(99L))
                .build();

        when(roomRepository.findAllById(List.of(99L))).thenReturn(List.of(standardRoom));

        RequestInvalidException ex = assertThrows(RequestInvalidException.class,
                () -> reservationService.createRoomReservation(bookingDTO, "customer1"));
        assertEquals("Room General Dining Room is not a VIP room", ex.getMessage());
    }
}
