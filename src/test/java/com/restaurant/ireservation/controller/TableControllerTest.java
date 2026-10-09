package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.response.AreaFloorPlanResponse;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.dto.response.TableStatusSummaryResponse;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.TableService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Unit Tests for TableController (Table Status Tracking [2])
 */
@ExtendWith(MockitoExtension.class)
class TableControllerTest {

    @Mock
    TableService tableService;

    @InjectMocks
    TableController tableController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tableController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/v1/tables: returns 200 OK with pagination")
    void fetchAllTables_returns200() throws Exception {
        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(1);
        meta.setPageSize(10);
        meta.setPages(1);
        meta.setTotal(1);
        paginationDTO.setMeta(meta);

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .capacity(4)
                .statusCode(TableStatus.AVAILABLE)
                .build();
        paginationDTO.setResult(List.of(responseDTO));

        when(tableService.fetchTables(any(), any(), any(), any(Pageable.class))).thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/tables")
                        .param("q", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.result[0].tableNumber").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/tables/summary: returns 200 OK with status counts")
    void fetchTableStatusSummary_returns200() throws Exception {
        TableStatusSummaryResponse summaryResponse = TableStatusSummaryResponse.builder()
                .totalTables(10)
                .availableTables(6)
                .inUseTables(2)
                .reservedTables(1)
                .cleaningTables(1)
                .lockedTables(0)
                .statusCounts(Map.of(TableStatus.AVAILABLE, 6L))
                .areaSummaries(List.of())
                .build();

        when(tableService.fetchTableStatusSummary()).thenReturn(summaryResponse);

        mockMvc.perform(get("/api/v1/tables/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTables").value(10))
                .andExpect(jsonPath("$.availableTables").value(6));
    }

    @Test
    @DisplayName("GET /api/v1/tables/floor-plan: returns 200 OK with layout")
    void fetchFloorPlan_returns200() throws Exception {
        AreaFloorPlanResponse planResponse = AreaFloorPlanResponse.builder()
                .areaId(1L)
                .areaCode("INDOOR_MAIN")
                .tables(List.of())
                .build();

        when(tableService.fetchFloorPlan()).thenReturn(List.of(planResponse));

        mockMvc.perform(get("/api/v1/tables/floor-plan")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaCode").value("INDOOR_MAIN"));
    }

    @Test
    @DisplayName("GET /api/v1/tables/area/{areaId}: returns 200 OK with tables")
    void fetchTablesByArea_returns200() throws Exception {
        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        when(tableService.fetchTablesByArea(1L)).thenReturn(List.of(responseDTO));

        mockMvc.perform(get("/api/v1/tables/area/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableNumber").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/tables/{id}: returns 200 OK when found")
    void fetchTableById_found_returns200() throws Exception {
        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        when(tableService.fetchTableById(1L)).thenReturn(responseDTO);

        mockMvc.perform(get("/api/v1/tables/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tableNumber").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/tables/{id}: returns 404 when not found")
    void fetchTableById_notFound_returns404() throws Exception {
        when(tableService.fetchTableById(99L))
                .thenThrow(new ResourceNotFoundException("Table not found with id: 99"));

        mockMvc.perform(get("/api/v1/tables/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("PATCH /api/v1/tables/{id}/status: returns 200 OK")
    void changeTableStatusById_returns200() throws Exception {
        ChangeTableStatusRequest request = ChangeTableStatusRequest.builder()
                .statusCode(TableStatus.IN_USE)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .statusCode(TableStatus.IN_USE)
                .build();

        when(tableService.changeTableStatus(any(ChangeTableStatusRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(patch("/api/v1/tables/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("IN_USE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tables/status: returns 200 OK")
    void changeTableStatus_returns200() throws Exception {
        ChangeTableStatusRequest request = ChangeTableStatusRequest.builder()
                .id(1L)
                .statusCode(TableStatus.CLEANING)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .statusCode(TableStatus.CLEANING)
                .build();

        when(tableService.changeTableStatus(any(ChangeTableStatusRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(patch("/api/v1/tables/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("CLEANING"));
    }
}
