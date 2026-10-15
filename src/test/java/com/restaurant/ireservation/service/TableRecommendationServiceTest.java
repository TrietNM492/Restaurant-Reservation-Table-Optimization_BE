package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.TableRecommendationRequest;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationType;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableRecommendationServiceTest {

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @Mock
    TableAreaRepository tableAreaRepository;

    @InjectMocks
    TableRecommendationService service;

    TableArea indoor;
    TableArea garden;
    Instant start;

    @BeforeEach
    void setUp() {
        indoor = TableArea.builder().code("IN").name(AreaName.INDOOR).build();
        indoor.setId(1L);
        garden = TableArea.builder().code("GD").name(AreaName.GARDEN).build();
        garden.setId(2L);
        start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
    }

    RestaurantTable table(long id, int capacity, TableArea area, TableStatus status) {
        RestaurantTable t = RestaurantTable.builder()
                .tableNumber((int) id)
                .capacity(capacity)
                .area(area)
                .statusCode(status)
                .build();
        t.setId(id);
        return t;
    }

    ReservationTable booking(RestaurantTable table, Instant from, Instant to) {
        Reservation r = Reservation.builder().startTime(from).endTime(to).build();
        return ReservationTable.builder().reservation(r).table(table).build();
    }

    TableRecommendationRequest request(int guests) {
        return TableRecommendationRequest.builder().guestCount(guests).startTime(start).build();
    }

    @Test
    @DisplayName("Returns best-fit table first and fills response metadata")
    void recommend_bestFitFirst() {
        RestaurantTable t2 = table(1, 2, indoor, TableStatus.AVAILABLE);
        RestaurantTable t4 = table(2, 4, indoor, TableStatus.AVAILABLE);
        RestaurantTable t8 = table(3, 8, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(t2, t4, t8));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(request(3));

        assertEquals(3, res.getGuestCount());
        assertEquals(PartySize.SMALL, res.getSuggestedPartySize());
        assertEquals(start, res.getStartTime());
        assertEquals(start.plus(Duration.ofMinutes(90)), res.getEndTime());
        assertEquals(90, res.getDurationMinutes());
        assertEquals(3, res.getAvailableTableCount());
        assertEquals(2, res.getRecommendations().size());
        assertEquals(List.of(2L), res.getRecommendations().get(0).getTableIds());
        assertEquals(1, res.getRecommendations().get(0).getRank());
        assertEquals(RecommendationType.SINGLE_TABLE, res.getRecommendations().get(0).getType());
        assertEquals(1, res.getRecommendations().get(0).getSpareSeats());
        assertEquals(AreaName.INDOOR, res.getRecommendations().get(0).getTables().get(0).getAreaName());
        assertNull(res.getMessage());
    }

    @Test
    @DisplayName("LOCKED tables are never recommended")
    void recommend_excludesLockedTables() {
        RestaurantTable locked = table(1, 4, indoor, TableStatus.LOCKED);
        RestaurantTable ok = table(2, 6, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(locked, ok));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(request(4));

        assertEquals(1, res.getRecommendations().size());
        assertEquals(List.of(2L), res.getRecommendations().get(0).getTableIds());
    }

    @Test
    @DisplayName("Walk-in (startTime omitted): IN_USE and CLEANING tables are excluded")
    void recommend_walkInExcludesOccupiedTables() {
        RestaurantTable inUse = table(1, 4, indoor, TableStatus.IN_USE);
        RestaurantTable cleaning = table(2, 4, indoor, TableStatus.CLEANING);
        RestaurantTable free = table(3, 4, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(inUse, cleaning, free));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(
                TableRecommendationRequest.builder().guestCount(4).build());

        assertEquals(1, res.getRecommendations().size());
        assertEquals(List.of(3L), res.getRecommendations().get(0).getTableIds());
    }

    @Test
    @DisplayName("Future slot: IN_USE table is still recommended (status only describes 'right now')")
    void recommend_futureSlotIgnoresCurrentOccupancy() {
        RestaurantTable inUse = table(1, 4, indoor, TableStatus.IN_USE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(inUse));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(request(4));

        assertEquals(1, res.getRecommendations().size());
    }

    @Test
    @DisplayName("Table with an overlapping booking is excluded")
    void recommend_excludesBookedTable() {
        RestaurantTable booked = table(1, 4, indoor, TableStatus.AVAILABLE);
        RestaurantTable free = table(2, 4, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(booked, free));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any()))
                .thenReturn(List.of(booking(booked, start.plusSeconds(600), start.plusSeconds(6000))));

        TableRecommendationResponse res = service.recommend(request(4));

        assertEquals(1, res.getAvailableTableCount());
        assertEquals(List.of(2L), res.getRecommendations().get(0).getTableIds());
    }

    @Test
    @DisplayName("Falls back to a table combination and explains it in the message")
    void recommend_combinationFallback() {
        RestaurantTable a = table(1, 4, indoor, TableStatus.AVAILABLE);
        RestaurantTable b = table(2, 4, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(a, b));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(request(8));

        assertEquals(1, res.getRecommendations().size());
        assertEquals(RecommendationType.TABLE_COMBINATION, res.getRecommendations().get(0).getType());
        assertEquals(2, res.getRecommendations().get(0).getTableIds().size());
        assertEquals(PartySize.MEDIUM, res.getSuggestedPartySize());
        assertNotNull(res.getMessage());
    }

    @Test
    @DisplayName("allowCombination=false: no combination, empty result with explanatory message")
    void recommend_noCombinationWhenDisabled() {
        RestaurantTable a = table(1, 4, indoor, TableStatus.AVAILABLE);
        RestaurantTable b = table(2, 4, indoor, TableStatus.AVAILABLE);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(a, b));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(
                TableRecommendationRequest.builder().guestCount(8).startTime(start).allowCombination(false).build());

        assertTrue(res.getRecommendations().isEmpty());
        assertNotNull(res.getMessage());
    }

    @Test
    @DisplayName("No usable tables: empty result and the booking query is skipped")
    void recommend_noUsableTables() {
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc())
                .thenReturn(List.of(table(1, 4, indoor, TableStatus.LOCKED)));

        TableRecommendationResponse res = service.recommend(request(2));

        assertTrue(res.getRecommendations().isEmpty());
        assertEquals(0, res.getAvailableTableCount());
        assertNotNull(res.getMessage());
        verify(reservationTableRepository, never()).findActiveBookingsInWindow(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Preferred area ranks matching tables first")
    void recommend_preferredArea() {
        RestaurantTable inIndoor = table(1, 4, indoor, TableStatus.AVAILABLE);
        RestaurantTable inGarden = table(2, 4, garden, TableStatus.AVAILABLE);
        when(tableAreaRepository.existsById(2L)).thenReturn(true);
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc()).thenReturn(List.of(inIndoor, inGarden));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(
                TableRecommendationRequest.builder().guestCount(4).startTime(start).preferredAreaId(2L).build());

        assertEquals(List.of(2L), res.getRecommendations().get(0).getTableIds());
        assertEquals(1.0, res.getRecommendations().get(0).getScoreBreakdown().getAreaMatch().doubleValue());
    }

    @Test
    @DisplayName("Unknown preferred area throws ResourceNotFoundException")
    void recommend_unknownArea() {
        when(tableAreaRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.recommend(
                TableRecommendationRequest.builder().guestCount(2).startTime(start).preferredAreaId(99L).build()));
    }

    @Test
    @DisplayName("Start time in the past throws RequestInvalidException")
    void recommend_pastStartTime() {
        TableRecommendationRequest past = TableRecommendationRequest.builder()
                .guestCount(2)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        assertThrows(RequestInvalidException.class, () -> service.recommend(past));
    }

    @Test
    @DisplayName("Custom duration is applied to endTime")
    void recommend_customDuration() {
        when(restaurantTableRepository.findAllByOrderByTableNumberAsc())
                .thenReturn(List.of(table(1, 4, indoor, TableStatus.AVAILABLE)));
        when(reservationTableRepository.findActiveBookingsInWindow(any(), any(), any(), any())).thenReturn(List.of());

        TableRecommendationResponse res = service.recommend(
                TableRecommendationRequest.builder().guestCount(4).startTime(start).durationMinutes(120).build());

        assertEquals(start.plus(Duration.ofMinutes(120)), res.getEndTime());
        assertEquals(120, res.getDurationMinutes());
    }

    @Test
    @DisplayName("toPartySize maps head count to PartySize buckets")
    void toPartySize_buckets() {
        assertEquals(PartySize.SINGLE, TableRecommendationService.toPartySize(1));
        assertEquals(PartySize.SMALL, TableRecommendationService.toPartySize(2));
        assertEquals(PartySize.SMALL, TableRecommendationService.toPartySize(4));
        assertEquals(PartySize.MEDIUM, TableRecommendationService.toPartySize(5));
        assertEquals(PartySize.MEDIUM, TableRecommendationService.toPartySize(8));
        assertEquals(PartySize.LARGE, TableRecommendationService.toPartySize(9));
        assertEquals(PartySize.LARGE, TableRecommendationService.toPartySize(12));
        assertEquals(PartySize.XLARGE, TableRecommendationService.toPartySize(13));
    }
}
