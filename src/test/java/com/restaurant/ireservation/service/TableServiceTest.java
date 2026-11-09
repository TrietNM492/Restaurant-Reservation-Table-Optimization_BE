package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.request.TableRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for TableService
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @Mock
    TableAreaRepository tableAreaRepository;

    @Mock
    ReservationTableRepository reservationTableRepository;

    @Mock
    TableAreaService tableAreaService;

    @Mock
    com.restaurant.ireservation.repository.OrderRepository orderRepository;

    @Mock
    com.restaurant.ireservation.repository.KitchenOrderRepository kitchenOrderRepository;

    @Mock
    com.restaurant.ireservation.repository.ReservationRepository reservationRepository;

    @InjectMocks
    TableService tableService;

    TableArea sampleArea;
    RestaurantTable sampleTable;

    @BeforeEach
    void setUp() {
        sampleArea = TableArea.builder()
                .code("INDOOR_MAIN")
                .name(AreaName.INDOOR)
                .description("Main room")
                .build();
        sampleArea.setId(1L);

        sampleTable = RestaurantTable.builder()
                .tableNumber(10)
                .capacity(4)
                .area(sampleArea)
                .statusCode(TableStatus.AVAILABLE)
                .description("Table near window")
                .build();
        sampleTable.setId(1L);
    }

    @Test
    @DisplayName("fetchTables: returns paginated tables")
    void fetchTables_returnsPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<RestaurantTable> page = new PageImpl<>(List.of(sampleTable), pageable, 1);

        when(restaurantTableRepository.searchTables(eq("10"), eq(1L), eq(TableStatus.AVAILABLE), eq(pageable)))
                .thenReturn(page);
        when(tableAreaService.toTableAreaResponse(sampleArea))
                .thenReturn(TableAreaResponse.builder().id(1L).code("INDOOR_MAIN").build());

        ResultPaginationDTO result = tableService.fetchTables("10", 1L, TableStatus.AVAILABLE, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<?> items = (List<?>) result.getResult();
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("fetchTableById: returns table when found")
    void fetchTableById_success() {
        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(tableAreaService.toTableAreaResponse(sampleArea))
                .thenReturn(TableAreaResponse.builder().id(1L).code("INDOOR_MAIN").build());

        TableResponse response = tableService.fetchTableById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTableNumber()).isEqualTo(10);
        assertThat(response.getStatusCode()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    @DisplayName("fetchTableById: throws ResourceNotFoundException when not found")
    void fetchTableById_notFound_throwsException() {
        when(restaurantTableRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.fetchTableById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table not found with id: 99");
    }

    @Test
    @DisplayName("createTable: successfully creates new table")
    void createTable_success() {
        TableRequest request = TableRequest.builder()
                .tableNumber(20)
                .capacity(6)
                .areaId(1L)
                .statusCode(TableStatus.AVAILABLE)
                .description("Big table")
                .build();

        RestaurantTable savedTable = RestaurantTable.builder()
                .tableNumber(20)
                .capacity(6)
                .area(sampleArea)
                .statusCode(TableStatus.AVAILABLE)
                .description("Big table")
                .build();
        savedTable.setId(2L);

        when(restaurantTableRepository.existsByTableNumber(20)).thenReturn(false);
        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(restaurantTableRepository.save(any(RestaurantTable.class))).thenReturn(savedTable);

        TableResponse response = tableService.createTable(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getTableNumber()).isEqualTo(20);
    }

    @Test
    @DisplayName("createTable: throws RequestInvalidException when tableNumber already exists")
    void createTable_duplicateNumber_throwsException() {
        TableRequest request = TableRequest.builder()
                .tableNumber(10)
                .capacity(4)
                .areaId(1L)
                .build();

        when(restaurantTableRepository.existsByTableNumber(10)).thenReturn(true);

        assertThatThrownBy(() -> tableService.createTable(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Table number already exists: 10");
    }

    @Test
    @DisplayName("createTable: throws ResourceNotFoundException when area not found")
    void createTable_areaNotFound_throwsException() {
        TableRequest request = TableRequest.builder()
                .tableNumber(30)
                .capacity(2)
                .areaId(99L)
                .build();

        when(restaurantTableRepository.existsByTableNumber(30)).thenReturn(false);
        when(tableAreaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.createTable(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table area not found with id: 99");
    }

    @Test
    @DisplayName("updateTable: successfully updates table")
    void updateTable_success() {
        TableRequest request = TableRequest.builder()
                .id(1L)
                .tableNumber(15)
                .capacity(4)
                .areaId(1L)
                .statusCode(TableStatus.CLEANING)
                .description("Updated description")
                .build();

        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(restaurantTableRepository.existsByTableNumberAndIdNot(15, 1L)).thenReturn(false);
        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(restaurantTableRepository.save(any(RestaurantTable.class))).thenAnswer(i -> i.getArgument(0));

        TableResponse response = tableService.updateTable(request);

        assertThat(response).isNotNull();
        assertThat(response.getTableNumber()).isEqualTo(15);
        assertThat(response.getStatusCode()).isEqualTo(TableStatus.CLEANING);
    }

    @Test
    @DisplayName("updateTable: throws RequestInvalidException when id is null")
    void updateTable_nullId_throwsException() {
        TableRequest request = TableRequest.builder()
                .tableNumber(15)
                .capacity(4)
                .areaId(1L)
                .build();

        assertThatThrownBy(() -> tableService.updateTable(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Id is required for updating table");
    }

    @Test
    @DisplayName("updateTable: throws ResourceNotFoundException when table not found")
    void updateTable_tableNotFound_throwsException() {
        TableRequest request = TableRequest.builder()
                .id(99L)
                .tableNumber(15)
                .capacity(4)
                .areaId(1L)
                .build();

        when(restaurantTableRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.updateTable(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table not found with id: 99");
    }

    @Test
    @DisplayName("updateTable: throws RequestInvalidException when table number is already taken")
    void updateTable_duplicateNumber_throwsException() {
        TableRequest request = TableRequest.builder()
                .id(1L)
                .tableNumber(20)
                .capacity(4)
                .areaId(1L)
                .build();

        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(restaurantTableRepository.existsByTableNumberAndIdNot(20, 1L)).thenReturn(true);

        assertThatThrownBy(() -> tableService.updateTable(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Table number already exists: 20");
    }

    @Test
    @DisplayName("changeTableStatus: successfully changes status")
    void changeTableStatus_success() {
        ChangeTableStatusRequest request = ChangeTableStatusRequest.builder()
                .id(1L)
                .statusCode(TableStatus.LOCKED)
                .build();

        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(restaurantTableRepository.save(any(RestaurantTable.class))).thenAnswer(i -> i.getArgument(0));

        TableResponse response = tableService.changeTableStatus(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(TableStatus.LOCKED);
    }

    @Test
    @DisplayName("changeTableStatus: when table changed to AVAILABLE or CLEANING, completes active orders")
    void changeTableStatus_toAvailable_completesActiveOrders() {
        ChangeTableStatusRequest request = ChangeTableStatusRequest.builder()
                .id(1L)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        sampleTable.setStatusCode(TableStatus.IN_USE);
        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(restaurantTableRepository.save(any(RestaurantTable.class))).thenAnswer(i -> i.getArgument(0));

        com.restaurant.ireservation.domain.entity.Order activeOrder = com.restaurant.ireservation.domain.entity.Order.builder()
                .orderNo("ORD-08112026-001")
                .orderStatus(com.restaurant.ireservation.domain.enumerations.OrderStatus.OK)
                .build();
        activeOrder.setId(101L);

        when(orderRepository.findActiveOrdersByTableId(eq(1L), any())).thenReturn(List.of(activeOrder));

        TableResponse response = tableService.changeTableStatus(request);

        assertThat(response.getStatusCode()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(activeOrder.getOrderStatus()).isEqualTo(com.restaurant.ireservation.domain.enumerations.OrderStatus.COMPLETED);
        verify(orderRepository).save(activeOrder);
    }

    @Test
    @DisplayName("toggleLockTable: locks available table and unlocks locked table")
    void toggleLockTable_toggleBehavior() {
        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(restaurantTableRepository.save(any(RestaurantTable.class))).thenAnswer(i -> i.getArgument(0));

        // Currently AVAILABLE -> should become LOCKED
        TableResponse response1 = tableService.toggleLockTable(1L);
        assertThat(response1.getStatusCode()).isEqualTo(TableStatus.LOCKED);

        // Currently LOCKED -> should become AVAILABLE
        TableResponse response2 = tableService.toggleLockTable(1L);
        assertThat(response2.getStatusCode()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    @DisplayName("deleteTable: successfully deletes when no reservations exist")
    void deleteTable_success() {
        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(reservationTableRepository.existsByTableId(1L)).thenReturn(false);

        tableService.deleteTable(1L);

        verify(restaurantTableRepository, times(1)).delete(sampleTable);
    }

    @Test
    @DisplayName("deleteTable: throws ResourceNotFoundException when table not found")
    void deleteTable_notFound_throwsException() {
        when(restaurantTableRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.deleteTable(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table not found with id: 99");
    }

    @Test
    @DisplayName("deleteTable: throws RequestInvalidException when table has reservations")
    void deleteTable_hasReservations_throwsException() {
        when(restaurantTableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
        when(reservationTableRepository.existsByTableId(1L)).thenReturn(true);

        assertThatThrownBy(() -> tableService.deleteTable(1L))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot delete table because it has associated reservations");

        verify(restaurantTableRepository, never()).delete(any());
    }

    @Test
    @DisplayName("fetchTableStatusSummary: returns correct statistics")
    void fetchTableStatusSummary_success() {
        when(restaurantTableRepository.count()).thenReturn(5L);
        when(restaurantTableRepository.countByStatusCode(TableStatus.AVAILABLE)).thenReturn(2L);
        when(restaurantTableRepository.countByStatusCode(TableStatus.RESERVED)).thenReturn(1L);
        when(restaurantTableRepository.countByStatusCode(TableStatus.IN_USE)).thenReturn(1L);
        when(restaurantTableRepository.countByStatusCode(TableStatus.CLEANING)).thenReturn(1L);
        when(restaurantTableRepository.countByStatusCode(TableStatus.LOCKED)).thenReturn(0L);

        when(tableAreaRepository.findAll()).thenReturn(List.of(sampleArea));
        when(restaurantTableRepository.findByAreaId(1L)).thenReturn(List.of(sampleTable));

        var summary = tableService.fetchTableStatusSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalTables()).isEqualTo(5L);
        assertThat(summary.getAvailableTables()).isEqualTo(2L);
        assertThat(summary.getAreaSummaries()).hasSize(1);
        assertThat(summary.getAreaSummaries().get(0).getAreaId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("fetchFloorPlan: returns layout grouped by area")
    void fetchFloorPlan_success() {
        when(tableAreaRepository.findAll()).thenReturn(List.of(sampleArea));
        when(restaurantTableRepository.findByAreaIdOrderByTableNumberAsc(1L)).thenReturn(List.of(sampleTable));
        when(tableAreaService.toTableAreaResponse(sampleArea))
                .thenReturn(TableAreaResponse.builder().id(1L).code("INDOOR_MAIN").build());

        var floorPlan = tableService.fetchFloorPlan();

        assertThat(floorPlan).hasSize(1);
        assertThat(floorPlan.get(0).getAreaCode()).isEqualTo("INDOOR_MAIN");
        assertThat(floorPlan.get(0).getTables()).hasSize(1);
    }

    @Test
    @DisplayName("fetchTablesByArea: returns tables belonging to area")
    void fetchTablesByArea_success() {
        when(tableAreaRepository.existsById(1L)).thenReturn(true);
        when(restaurantTableRepository.findByAreaIdOrderByTableNumberAsc(1L)).thenReturn(List.of(sampleTable));
        when(tableAreaService.toTableAreaResponse(sampleArea))
                .thenReturn(TableAreaResponse.builder().id(1L).code("INDOOR_MAIN").build());

        var tables = tableService.fetchTablesByArea(1L);

        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getTableNumber()).isEqualTo(10);
    }

    @Test
    @DisplayName("fetchTablesByArea: throws ResourceNotFoundException when area does not exist")
    void fetchTablesByArea_notFound_throwsException() {
        when(tableAreaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> tableService.fetchTablesByArea(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table area not found with id: 99");
    }
}
