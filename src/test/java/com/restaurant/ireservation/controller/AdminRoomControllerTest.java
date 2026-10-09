package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.ChangeRoomStatusRequest;
import com.restaurant.ireservation.dto.request.RoomRequest;
import com.restaurant.ireservation.dto.response.RoomResponse;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.RoomService;
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
 * Module: VIP Room Management [6] Controller Unit Tests for AdminRoomController
 */
@ExtendWith(MockitoExtension.class)
class AdminRoomControllerTest {

    @Mock
    RoomService roomService;

    @InjectMocks
    AdminRoomController adminRoomController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    RoomResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminRoomController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleResponse = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.AVAILABLE)
                .description("Premium VIP Room")
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/admin/rooms
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/admin/rooms: returns 200 OK with pagination")
    void fetchAllRooms_returns200() throws Exception {
        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(1);
        meta.setPageSize(10);
        meta.setPages(1);
        meta.setTotal(1);
        paginationDTO.setMeta(meta);
        paginationDTO.setResult(List.of(sampleResponse));

        when(roomService.fetchRooms(any(), any(), any(), any(Pageable.class))).thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/admin/rooms")
                        .param("q", "VIP")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.result[0].roomName").value("VIP Suite A"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/admin/rooms/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/admin/rooms/{id}: returns 200 OK when found")
    void fetchRoomById_found_returns200() throws Exception {
        when(roomService.fetchRoomById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/admin/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomName").value("VIP Suite A"))
                .andExpect(jsonPath("$.roomType").value("VIP_ROOM"))
                .andExpect(jsonPath("$.roomStatus").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/rooms/{id}: returns 404 when not found")
    void fetchRoomById_notFound_returns404() throws Exception {
        when(roomService.fetchRoomById(99L)).thenThrow(new ResourceNotFoundException("Room not found with id: 99"));

        mockMvc.perform(get("/api/v1/admin/rooms/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/admin/rooms
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/admin/rooms: returns 201 Created on success")
    void createRoom_returns201() throws Exception {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .description("Premium VIP Room")
                .build();

        when(roomService.createRoom(any(RoomRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomName").value("VIP Suite A"))
                .andExpect(jsonPath("$.roomType").value("VIP_ROOM"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/rooms: returns 400 when room name is blank")
    void createRoom_blankName_returns400() throws Exception {
        RoomRequest request = RoomRequest.builder()
                .roomName("")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/admin/rooms: returns 400 when room name already exists")
    void createRoom_duplicateName_returns400() throws Exception {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        when(roomService.createRoom(any(RoomRequest.class)))
                .thenThrow(new RequestInvalidException("Room name already exists: VIP Suite A"));

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/admin/rooms/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/admin/rooms/{id}: returns 200 OK on success")
    void updateRoomWithId_returns200() throws Exception {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A Updated")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(600000)
                .build();

        RoomResponse updated = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A Updated")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(600000)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();

        when(roomService.updateRoom(any(RoomRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("VIP Suite A Updated"))
                .andExpect(jsonPath("$.capacity").value(12));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/rooms/{id}: returns 404 when room not found")
    void updateRoomWithId_notFound_returns404() throws Exception {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        when(roomService.updateRoom(any(RoomRequest.class)))
                .thenThrow(new ResourceNotFoundException("Room not found with id: 99"));

        mockMvc.perform(put("/api/v1/admin/rooms/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/admin/rooms/status
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/admin/rooms/status: returns 200 on success")
    void changeRoomStatus_returns200() throws Exception {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .id(1L)
                .roomStatus(RoomStatus.OCCUPIED)
                .build();

        RoomResponse occupied = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.OCCUPIED)
                .build();

        when(roomService.changeRoomStatus(any(ChangeRoomStatusRequest.class))).thenReturn(occupied);

        mockMvc.perform(patch("/api/v1/admin/rooms/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("OCCUPIED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/rooms/status: returns 400 when status is null")
    void changeRoomStatus_nullStatus_returns400() throws Exception {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .id(1L)
                .build();

        mockMvc.perform(patch("/api/v1/admin/rooms/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/admin/rooms/{id}/toggle-maintenance
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/admin/rooms/{id}/toggle-maintenance: returns 200 on success")
    void toggleMaintenance_returns200() throws Exception {
        RoomResponse maintenance = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.MAINTENANCE)
                .build();

        when(roomService.toggleMaintenanceRoom(1L)).thenReturn(maintenance);

        mockMvc.perform(patch("/api/v1/admin/rooms/1/toggle-maintenance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/rooms/{id}/toggle-maintenance: returns 404 when room not found")
    void toggleMaintenance_notFound_returns404() throws Exception {
        when(roomService.toggleMaintenanceRoom(99L))
                .thenThrow(new ResourceNotFoundException("Room not found with id: 99"));

        mockMvc.perform(patch("/api/v1/admin/rooms/99/toggle-maintenance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/admin/rooms/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/admin/rooms/{id}: returns 200 on success")
    void deleteRoom_returns200() throws Exception {
        doNothing().when(roomService).deleteRoom(1L);

        mockMvc.perform(delete("/api/v1/admin/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(roomService, times(1)).deleteRoom(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/rooms/{id}: returns 400 when room has reservations")
    void deleteRoom_hasReservations_returns400() throws Exception {
        doThrow(new RequestInvalidException("Cannot delete room because it has associated reservations"))
                .when(roomService).deleteRoom(1L);

        mockMvc.perform(delete("/api/v1/admin/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/rooms/{id}: returns 404 when room not found")
    void deleteRoom_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Room not found with id: 99"))
                .when(roomService).deleteRoom(99L);

        mockMvc.perform(delete("/api/v1/admin/rooms/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
