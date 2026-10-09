package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.ChangeTableStatusRequest;
import com.restaurant.ireservation.dto.request.TableRequest;
import com.restaurant.ireservation.dto.response.TableResponse;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Unit Tests for AdminTableController
 */
@ExtendWith(MockitoExtension.class)
class AdminTableControllerTest {

    @Mock
    TableService tableService;

    @InjectMocks
    AdminTableController adminTableController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminTableController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/v1/admin/tables: returns 200 OK with pagination")
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

        mockMvc.perform(get("/api/v1/admin/tables")
                        .param("q", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.result[0].tableNumber").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/admin/tables/{id}: returns 200 OK when found")
    void fetchTableById_found_returns200() throws Exception {
        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(10)
                .capacity(4)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        when(tableService.fetchTableById(1L)).thenReturn(responseDTO);

        mockMvc.perform(get("/api/v1/admin/tables/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tableNumber").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/admin/tables/{id}: returns 404 when not found")
    void fetchTableById_notFound_returns404() throws Exception {
        when(tableService.fetchTableById(99L))
                .thenThrow(new ResourceNotFoundException("Table not found with id: 99"));

        mockMvc.perform(get("/api/v1/admin/tables/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/admin/tables: valid request returns 201 Created")
    void createTable_validRequest_returns201() throws Exception {
        TableRequest request = TableRequest.builder()
                .tableNumber(20)
                .capacity(4)
                .areaId(1L)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(2L)
                .tableNumber(20)
                .capacity(4)
                .statusCode(TableStatus.AVAILABLE)
                .build();

        when(tableService.createTable(any(TableRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/admin/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.tableNumber").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/admin/tables: invalid request returns 400 Bad Request")
    void createTable_invalidRequest_returns400() throws Exception {
        TableRequest request = TableRequest.builder()
                .tableNumber(null) // Missing table number
                .capacity(0) // Invalid capacity
                .areaId(null) // Missing areaId
                .build();

        mockMvc.perform(post("/api/v1/admin/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/tables/{id}: valid request returns 200 OK")
    void updateTableWithId_validRequest_returns200() throws Exception {
        TableRequest request = TableRequest.builder()
                .tableNumber(25)
                .capacity(6)
                .areaId(1L)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(25)
                .capacity(6)
                .build();

        when(tableService.updateTable(any(TableRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(put("/api/v1/admin/tables/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tableNumber").value(25));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/tables: valid request returns 200 OK")
    void updateTable_bodyId_returns200() throws Exception {
        TableRequest request = TableRequest.builder()
                .id(1L)
                .tableNumber(25)
                .capacity(6)
                .areaId(1L)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .tableNumber(25)
                .capacity(6)
                .build();

        when(tableService.updateTable(any(TableRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(put("/api/v1/admin/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/tables/status: valid request returns 200 OK")
    void changeTableStatus_validRequest_returns200() throws Exception {
        ChangeTableStatusRequest request = ChangeTableStatusRequest.builder()
                .id(1L)
                .statusCode(TableStatus.LOCKED)
                .build();

        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .statusCode(TableStatus.LOCKED)
                .build();

        when(tableService.changeTableStatus(any(ChangeTableStatusRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(patch("/api/v1/admin/tables/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("LOCKED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/tables/{id}/toggle-lock: returns 200 OK")
    void toggleLockTable_returns200() throws Exception {
        TableResponse responseDTO = TableResponse.builder()
                .id(1L)
                .statusCode(TableStatus.LOCKED)
                .build();

        when(tableService.toggleLockTable(1L)).thenReturn(responseDTO);

        mockMvc.perform(patch("/api/v1/admin/tables/1/toggle-lock")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("LOCKED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/tables/{id}: returns 200 OK")
    void deleteTable_success_returns200() throws Exception {
        doNothing().when(tableService).deleteTable(1L);

        mockMvc.perform(delete("/api/v1/admin/tables/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(tableService, times(1)).deleteTable(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/tables/{id}: when table has reservations returns 400")
    void deleteTable_hasReservations_returns400() throws Exception {
        doThrow(new RequestInvalidException("Cannot delete table because it has associated reservations"))
                .when(tableService).deleteTable(1L);

        mockMvc.perform(delete("/api/v1/admin/tables/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Cannot delete table because it has associated reservations"));
    }
}
