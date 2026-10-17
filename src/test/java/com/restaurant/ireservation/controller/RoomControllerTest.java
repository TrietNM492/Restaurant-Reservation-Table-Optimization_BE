package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.ChangeRoomStatusRequest;
import com.restaurant.ireservation.dto.response.RoomResponse;
import com.restaurant.ireservation.error.GlobalException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Unit Tests for RoomController (VIP Room Management [6])
 */
@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    RoomService roomService;

    @InjectMocks
    RoomController roomController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    RoomResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
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
    // GET /api/v1/rooms
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/rooms: returns 200 OK with pagination")
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

        mockMvc.perform(get("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.result[0].roomName").value("VIP Suite A"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/rooms/available
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/rooms/available: returns 200 OK with available rooms")
    void fetchAvailableRooms_returns200() throws Exception {
        when(roomService.fetchAvailableRooms()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/rooms/available")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].roomName").value("VIP Suite A"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/rooms/type/{roomType}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/rooms/type/{roomType}: returns 200 OK with rooms of that type")
    void fetchRoomsByType_returns200() throws Exception {
        when(roomService.fetchRoomsByType(eq(RoomType.VIP_ROOM))).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/rooms/type/VIP_ROOM")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomType").value("VIP_ROOM"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/rooms/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/rooms/{id}: returns 200 OK when found")
    void fetchRoomById_found_returns200() throws Exception {
        when(roomService.fetchRoomById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomName").value("VIP Suite A"))
                .andExpect(jsonPath("$.roomType").value("VIP_ROOM"));
    }

    @Test
    @DisplayName("GET /api/v1/rooms/{id}: returns 404 when not found")
    void fetchRoomById_notFound_returns404() throws Exception {
        when(roomService.fetchRoomById(99L)).thenThrow(new ResourceNotFoundException("Room not found with id: 99"));

        mockMvc.perform(get("/api/v1/rooms/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/rooms/{id}/status
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/rooms/{id}/status: returns 200 on success")
    void changeRoomStatusById_returns200() throws Exception {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .roomStatus(RoomStatus.RESERVED)
                .build();

        RoomResponse reserved = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.RESERVED)
                .build();

        when(roomService.changeRoomStatus(any(ChangeRoomStatusRequest.class))).thenReturn(reserved);

        mockMvc.perform(patch("/api/v1/rooms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("RESERVED"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/rooms/status
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/rooms/status: returns 200 on success")
    void changeRoomStatus_returns200() throws Exception {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .id(1L)
                .roomStatus(RoomStatus.CLEANING)
                .build();

        RoomResponse cleaning = RoomResponse.builder()
                .id(1L)
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.CLEANING)
                .build();

        when(roomService.changeRoomStatus(any(ChangeRoomStatusRequest.class))).thenReturn(cleaning);

        mockMvc.perform(patch("/api/v1/rooms/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("CLEANING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/rooms/status: returns 400 when status is null")
    void changeRoomStatus_nullStatus_returns400() throws Exception {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .id(1L)
                .build();

        mockMvc.perform(patch("/api/v1/rooms/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
