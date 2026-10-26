package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.*;
import com.restaurant.ireservation.domain.enumerations.*;
import com.restaurant.ireservation.dto.request.MultiTableAllocationRequest;
import com.restaurant.ireservation.dto.response.MultiTableAllocationResponse;
import com.restaurant.ireservation.error.*;
import com.restaurant.ireservation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiTableAllocationServiceTest {
    @Mock ReservationRepository reservationRepository;
    @Mock RestaurantTableRepository restaurantTableRepository;
    @Mock ReservationTableRepository reservationTableRepository;
    @InjectMocks MultiTableAllocationService service;
    Reservation reservation;
    RestaurantTable first;
    RestaurantTable second;
    MultiTableAllocationRequest request;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder().reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED).partySize(PartySize.MEDIUM)
                .startTime(Instant.now().plusSeconds(7200)).endTime(Instant.now().plusSeconds(10800)).build();
        reservation.setId(10L);
        TableArea area = new TableArea();
        area.setId(1L);
        first = RestaurantTable.builder().tableNumber(1).capacity(4).area(area).statusCode(TableStatus.AVAILABLE).build();
        first.setId(1L);
        second = RestaurantTable.builder().tableNumber(2).capacity(4).area(area).statusCode(TableStatus.AVAILABLE).build();
        second.setId(2L);
        request = new MultiTableAllocationRequest(10L, List.of(1L, 2L));
    }

    void stubReservation() {
        when(reservationRepository.findByIdForAllocation(10L)).thenReturn(Optional.of(reservation));
    }

    void stubTables() {
        stubReservation();
        when(restaurantTableRepository.findAllByIdForAllocation(request.getTableIds())).thenReturn(List.of(first, second));
    }

    @Test
    void allocatesAndExcludesOwnReservationWithTurnaroundBuffer() {
        stubTables();
        var response = service.allocate(request);
        assertEquals(8, response.getTotalCapacity());
        assertEquals(82.6, response.getScore());
        assertEquals(1L, response.getAreaId());
        assertEquals(List.of(1L, 2L), response.getTables().stream().map(t -> t.getId()).toList());
        verify(reservationTableRepository).findConflictingReservationsExcludingReservation(
                eq(request.getTableIds()), eq(10L), anyCollection(),
                eq(reservation.getStartTime().minusSeconds(900)), eq(reservation.getEndTime().plusSeconds(900)));
        var order = inOrder(reservationTableRepository);
        order.verify(reservationTableRepository).deleteByReservationId(10L);
        order.verify(reservationTableRepository).flush();
        order.verify(reservationTableRepository).saveAll(argThat(links -> {
            int count = 0;
            for (ReservationTable link : links) {
                assertSame(reservation, link.getReservation());
                count++;
            }
            return count == 2;
        }));
    }

    @Test
    void duplicatesFailBeforeDatabaseAccess() {
        request.setTableIds(List.of(1L, 1L));
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
        verifyNoInteractions(reservationRepository, reservationTableRepository, restaurantTableRepository);
    }

    @Test
    void missingReservationReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.allocate(request));
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonAllocatableStatus(ReservationStatus status) {
        stubReservation();
        reservation.setReservationStatus(status);
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
        verifyNoInteractions(reservationTableRepository);
    }

    @Test
    void rejectsVipReservation() {
        stubReservation();
        reservation.setReservationType(ReservationType.VIP_ROOM);
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
    }

    @Test
    void rejectsExpiredSlot() {
        stubReservation();
        reservation.setEndTime(Instant.now().minusSeconds(60));
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
    }

    @Test
    void missingTableReturnsNotFound() {
        stubReservation();
        when(restaurantTableRepository.findAllByIdForAllocation(anyCollection())).thenReturn(List.of(first));
        assertThrows(ResourceNotFoundException.class, () -> service.allocate(request));
    }

    @Test
    void rejectsDifferentAreasWithoutChangingAllocation() {
        stubTables();
        TableArea other = new TableArea();
        other.setId(2L);
        second.setArea(other);
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
        verifyNoInteractions(reservationTableRepository);
    }

    @Test
    void rejectsInsufficientCapacity() {
        stubTables();
        second.setCapacity(2);
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
        verifyNoInteractions(reservationTableRepository);
    }

    @Test
    void rejectsLockedTable() {
        stubTables();
        first.setStatusCode(TableStatus.LOCKED);
        assertThrows(RequestInvalidException.class, () -> service.allocate(request));
        verifyNoInteractions(reservationTableRepository);
    }

    @Test
    void rejectsCurrentlyOccupiedTableForNearTermSlot() {
        stubTables();
        reservation.setStartTime(Instant.now().plusSeconds(300));
        first.setStatusCode(TableStatus.IN_USE);
        assertThrows(ReservationConflictException.class, () -> service.allocate(request));
    }

    @Test
    void conflictDoesNotDeleteExistingLinks() {
        stubTables();
        when(reservationTableRepository.findConflictingReservationsExcludingReservation(anyList(), anyLong(),
                anyCollection(), any(), any())).thenReturn(List.of(new ReservationTable()));
        assertThrows(ReservationConflictException.class, () -> service.allocate(request));
        verify(reservationTableRepository, never()).deleteByReservationId(anyLong());
        verify(reservationTableRepository, never()).saveAll(any());
    }

    @Test
    void getReturnsFilteredPageAndCapacity() {
        var pageable = PageRequest.of(0, 20);
        when(reservationRepository.findTableCombinations(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(reservation), pageable, 1));
        when(reservationTableRepository.findByReservationId(10L)).thenReturn(List.of(
                ReservationTable.builder().reservation(reservation).table(first).build(),
                ReservationTable.builder().reservation(reservation).table(second).build()));
        var result = service.getCombinations(10L, pageable);
        assertEquals(1, result.getMeta().getTotal());
        assertEquals(1, result.getMeta().getPage());
        assertEquals(1, ((List<?>) result.getResult()).size());
        assertEquals(82.6, ((MultiTableAllocationResponse) ((List<?>) result.getResult()).getFirst()).getScore());
    }

    @Test
    void allocateAndGetReturnSameScoreAndIgnoreOwnBooking() {
        stubTables();
        List<ReservationTable> ownLinks = List.of(
                ReservationTable.builder().reservation(reservation).table(first).build(),
                ReservationTable.builder().reservation(reservation).table(second).build());
        Reservation neighbour = Reservation.builder()
                .startTime(reservation.getStartTime().minusSeconds(7200))
                .endTime(reservation.getStartTime().minusSeconds(900)).build();
        neighbour.setId(20L);
        List<ReservationTable> neighbours = new ArrayList<>(ownLinks);
        neighbours.add(ReservationTable.builder().reservation(neighbour).table(first).build());
        neighbours.add(ReservationTable.builder().reservation(neighbour).table(second).build());
        when(reservationTableRepository.findActiveBookingsInWindow(anyCollection(), anyCollection(), any(), any()))
                .thenReturn(neighbours);
        var allocated = service.allocate(request);
        var pageable = PageRequest.of(0, 10);
        when(reservationRepository.findTableCombinations(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(reservation), pageable, 1));
        when(reservationTableRepository.findByReservationId(10L)).thenReturn(ownLinks);
        var page = service.getCombinations(10L, pageable);
        var fetched = (MultiTableAllocationResponse) ((List<?>) page.getResult()).getFirst();
        assertEquals(87.1, allocated.getScore());
        assertEquals(allocated.getScore(), fetched.getScore());
        verify(reservationTableRepository, times(2)).findActiveBookingsInWindow(eq(List.of(1L, 2L)),
                anyCollection(), eq(reservation.getStartTime().minusSeconds(21600)),
                eq(reservation.getEndTime().plusSeconds(21600)));
    }
}
