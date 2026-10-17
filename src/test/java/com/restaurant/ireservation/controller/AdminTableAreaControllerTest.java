package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import com.restaurant.ireservation.dto.request.TableAreaRequest;
import com.restaurant.ireservation.dto.response.TableAreaResponse;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.TableAreaService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Module: Table Area Management [6] Controller Unit Tests
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@ExtendWith(MockitoExtension.class)
class AdminTableAreaControllerTest {

    @Mock
    TableAreaService tableAreaService;

    @InjectMocks
    AdminTableAreaController adminTableAreaController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminTableAreaController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/v1/admin/table-areas: returns 200 OK with pagination")
    void fetchAllTableAreas_returns200() throws Exception {
        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(1);
        meta.setPageSize(10);
        meta.setPages(1);
        meta.setTotal(1);
        paginationDTO.setMeta(meta);

        TableAreaResponse responseDTO = TableAreaResponse.builder()
                .id(1L)
                .code("INDOOR_MAIN")
                .name(AreaName.INDOOR)
                .description("Main room")
                .build();
        paginationDTO.setResult(List.of(responseDTO));

        when(tableAreaService.fetchTableAreas(any(), any(), any(Pageable.class))).thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/admin/table-areas")
                        .param("q", "indoor")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.result[0].code").value("INDOOR_MAIN"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/table-areas/{id}: returns 200 OK when found")
    void fetchTableAreaById_found_returns200() throws Exception {
        TableAreaResponse responseDTO = TableAreaResponse.builder()
                .id(1L)
                .code("INDOOR_MAIN")
                .name(AreaName.INDOOR)
                .description("Main room")
                .build();

        when(tableAreaService.fetchTableAreaById(1L)).thenReturn(responseDTO);

        mockMvc.perform(get("/api/v1/admin/table-areas/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("INDOOR_MAIN"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/table-areas/{id}: returns 404 when not found")
    void fetchTableAreaById_notFound_returns404() throws Exception {
        when(tableAreaService.fetchTableAreaById(99L))
                .thenThrow(new ResourceNotFoundException("Table area not found with id: 99"));

        mockMvc.perform(get("/api/v1/admin/table-areas/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/table-areas: valid request returns 201 Created")
    void createTableArea_validRequest_returns201() throws Exception {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("GARDEN_V1")
                .name(AreaName.GARDEN)
                .description("Outdoor garden section")
                .build();

        TableAreaResponse responseDTO = TableAreaResponse.builder()
                .id(2L)
                .code("GARDEN_V1")
                .name(AreaName.GARDEN)
                .description("Outdoor garden section")
                .build();

        when(tableAreaService.createTableArea(any(TableAreaRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/admin/table-areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("GARDEN_V1"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/table-areas: invalid request returns 400 Bad Request")
    void createTableArea_invalidRequest_returns400() throws Exception {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("") // Blank code
                .name(null) // Null name
                .build();

        mockMvc.perform(post("/api/v1/admin/table-areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/table-areas/{id}: valid request returns 200 OK")
    void updateTableAreaWithId_validRequest_returns200() throws Exception {
        TableAreaRequest request = TableAreaRequest.builder()
                .code("INDOOR_UPDATED")
                .name(AreaName.INDOOR)
                .description("Updated")
                .build();

        TableAreaResponse responseDTO = TableAreaResponse.builder()
                .id(1L)
                .code("INDOOR_UPDATED")
                .name(AreaName.INDOOR)
                .description("Updated")
                .build();

        when(tableAreaService.updateTableArea(any(TableAreaRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(put("/api/v1/admin/table-areas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("INDOOR_UPDATED"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/table-areas: valid request with body id returns 200 OK")
    void updateTableArea_bodyId_returns200() throws Exception {
        TableAreaRequest request = TableAreaRequest.builder()
                .id(1L)
                .code("WINDOW_SEAT")
                .name(AreaName.WINDOW)
                .description("Window view")
                .build();

        TableAreaResponse responseDTO = TableAreaResponse.builder()
                .id(1L)
                .code("WINDOW_SEAT")
                .name(AreaName.WINDOW)
                .description("Window view")
                .build();

        when(tableAreaService.updateTableArea(any(TableAreaRequest.class))).thenReturn(responseDTO);

        mockMvc.perform(put("/api/v1/admin/table-areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("WINDOW_SEAT"));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/table-areas/{id}: returns 200 OK")
    void deleteTableArea_success_returns200() throws Exception {
        doNothing().when(tableAreaService).deleteTableArea(1L);

        mockMvc.perform(delete("/api/v1/admin/table-areas/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(tableAreaService, times(1)).deleteTableArea(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/table-areas/{id}: when assigned tables exist returns 400")
    void deleteTableArea_assignedTables_returns400() throws Exception {
        doThrow(new RequestInvalidException("Cannot delete table area because it is assigned to existing tables"))
                .when(tableAreaService).deleteTableArea(1L);

        mockMvc.perform(delete("/api/v1/admin/table-areas/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Cannot delete table area because it is assigned to existing tables"));
    }
}
