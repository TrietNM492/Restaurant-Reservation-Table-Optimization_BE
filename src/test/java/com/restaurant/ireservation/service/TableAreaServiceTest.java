package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.dto.request.TableAreaRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
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
 * Module: Table Area Management [6] Unit Tests
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@ExtendWith(MockitoExtension.class)
class TableAreaServiceTest {

    @Mock
    TableAreaRepository tableAreaRepository;

    @Mock
    RestaurantTableRepository restaurantTableRepository;

    @InjectMocks
    TableAreaService tableAreaService;

    TableArea sampleArea;

    @BeforeEach
    void setUp() {
        sampleArea = TableArea.builder()
                .code("INDOOR_MAIN")
                .name(AreaName.INDOOR)
                .description("Main indoor dining area")
                .build();
        sampleArea.setId(1L);
    }

    @Test
    @DisplayName("fetchTableAreas: returns paginated result")
    void fetchTableAreas_returnsPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TableArea> page = new PageImpl<>(List.of(sampleArea), pageable, 1);

        when(tableAreaRepository.searchTableAreas(eq("indoor"), isNull(), eq(AreaName.INDOOR), eq(pageable)))
                .thenReturn(page);

        ResultPaginationDTO result = tableAreaService.fetchTableAreas("indoor", null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<?> items = (List<?>) result.getResult();
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("fetchTableAreaById: returns table area when exists")
    void fetchTableAreaById_success() {
        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));

        TableAreaResponse response = tableAreaService.fetchTableAreaById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("INDOOR_MAIN");
        assertThat(response.getName()).isEqualTo(AreaName.INDOOR);
    }

    @Test
    @DisplayName("fetchTableAreaById: throws ResourceNotFoundException when not found")
    void fetchTableAreaById_notFound_throwsException() {
        when(tableAreaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableAreaService.fetchTableAreaById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table area not found with id: 99");
    }

    @Test
    @DisplayName("createTableArea: successfully creates new table area")
    void createTableArea_success() {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("outdoor_terrace")
                .name(AreaName.OUTDOOR)
                .description("Terrace with garden view")
                .build();

        TableArea savedArea = TableArea.builder()
                .code("OUTDOOR_TERRACE")
                .name(AreaName.OUTDOOR)
                .description("Terrace with garden view")
                .build();
        savedArea.setId(2L);

        when(tableAreaRepository.existsByCode("OUTDOOR_TERRACE")).thenReturn(false);
        when(tableAreaRepository.save(any(TableArea.class))).thenReturn(savedArea);

        TableAreaResponse response = tableAreaService.createTableArea(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getCode()).isEqualTo("OUTDOOR_TERRACE");
        assertThat(response.getName()).isEqualTo(AreaName.OUTDOOR);
    }

    @Test
    @DisplayName("createTableArea: throws RequestInvalidException when code already exists")
    void createTableArea_duplicateCode_throwsException() {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("indoor_main")
                .name(AreaName.INDOOR)
                .build();

        when(tableAreaRepository.existsByCode("INDOOR_MAIN")).thenReturn(true);

        assertThatThrownBy(() -> tableAreaService.createTableArea(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Table area code already exists: INDOOR_MAIN");
    }

    @Test
    @DisplayName("updateTableArea: successfully updates table area")
    void updateTableArea_success() {
        TableAreaRequest request = TableAreaRequest.builder()
                .id(1L)
                .code("indoor_vip")
                .name(AreaName.INDOOR)
                .description("Updated description")
                .build();

        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(tableAreaRepository.existsByCodeAndIdNot("INDOOR_VIP", 1L)).thenReturn(false);
        when(tableAreaRepository.save(any(TableArea.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableAreaResponse response = tableAreaService.updateTableArea(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("INDOOR_VIP");
        assertThat(response.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("updateTableArea: throws RequestInvalidException when id is null")
    void updateTableArea_nullId_throwsException() {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("INDOOR_VIP")
                .name(AreaName.INDOOR)
                .build();

        assertThatThrownBy(() -> tableAreaService.updateTableArea(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Id is required for updating table area");
    }

    @Test
    @DisplayName("updateTableArea: throws ResourceNotFoundException when area does not exist")
    void updateTableArea_notFound_throwsException() {
        TableAreaRequest request = TableAreaRequest.builder()
                .id(99L)
                .code("INDOOR_VIP")
                .name(AreaName.INDOOR)
                .build();

        when(tableAreaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableAreaService.updateTableArea(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table area not found with id: 99");
    }

    @Test
    @DisplayName("updateTableArea: throws RequestInvalidException when new code is already taken")
    void updateTableArea_duplicateCode_throwsException() {
        TableAreaRequest request = TableAreaRequest.builder()
                .id(1L)
                .code("GARDEN_AREA")
                .name(AreaName.GARDEN)
                .build();

        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(tableAreaRepository.existsByCodeAndIdNot("GARDEN_AREA", 1L)).thenReturn(true);

        assertThatThrownBy(() -> tableAreaService.updateTableArea(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Table area code already exists: GARDEN_AREA");
    }

    @Test
    @DisplayName("deleteTableArea: successfully deletes when not associated with any tables")
    void deleteTableArea_success() {
        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(restaurantTableRepository.existsByAreaId(1L)).thenReturn(false);

        tableAreaService.deleteTableArea(1L);

        verify(tableAreaRepository, times(1)).delete(sampleArea);
    }

    @Test
    @DisplayName("deleteTableArea: throws ResourceNotFoundException when area not found")
    void deleteTableArea_notFound_throwsException() {
        when(tableAreaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableAreaService.deleteTableArea(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Table area not found with id: 99");
    }

    @Test
    @DisplayName("deleteTableArea: throws RequestInvalidException when tables exist in this area")
    void deleteTableArea_assignedTables_throwsException() {
        when(tableAreaRepository.findById(1L)).thenReturn(Optional.of(sampleArea));
        when(restaurantTableRepository.existsByAreaId(1L)).thenReturn(true);

        assertThatThrownBy(() -> tableAreaService.deleteTableArea(1L))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot delete table area because it is assigned to existing tables");

        verify(tableAreaRepository, never()).delete(any());
    }
}
