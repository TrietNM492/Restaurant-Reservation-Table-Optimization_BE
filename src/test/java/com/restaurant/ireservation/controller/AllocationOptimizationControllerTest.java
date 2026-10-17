package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.AllocationOptimizationRequest;
import com.restaurant.ireservation.dto.response.AllocationOptimizationResponse;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.service.AllocationOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AllocationOptimizationControllerTest {

    static final String URL = "/api/v1/tables/allocation-optimization";

    @Mock
    AllocationOptimizationService allocationOptimizationService;

    @InjectMocks
    AllocationOptimizationController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalException())
                .build();
    }

    @Test
    @DisplayName("POST /preview: returns 200 OK with the plan")
    void preview_returns200() throws Exception {
        AllocationOptimizationResponse response = AllocationOptimizationResponse.builder()
                .applied(false).reservationsInScope(3).movableReservations(2).movesCount(1)
                .moves(List.of()).message("1 reservation can be moved").build();
        when(allocationOptimizationService.preview(any(AllocationOptimizationRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL + "/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMoves\":5,\"lockWindowMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.reservationsInScope").value(3))
                .andExpect(jsonPath("$.movesCount").value(1));
    }

    @Test
    @DisplayName("POST /preview: an empty body is accepted (all fields are optional)")
    void preview_emptyBody_returns200() throws Exception {
        when(allocationOptimizationService.preview(any(AllocationOptimizationRequest.class)))
                .thenReturn(AllocationOptimizationResponse.builder().moves(List.of()).build());

        mockMvc.perform(post(URL + "/preview"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /preview: maxMoves = 0 fails @Min validation returning HTTP 400")
    void preview_invalidMaxMoves_returns400() throws Exception {
        mockMvc.perform(post(URL + "/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMoves\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /apply: returns 200 OK when the plan was applied")
    void apply_returns200() throws Exception {
        AllocationOptimizationResponse response = AllocationOptimizationResponse.builder()
                .applied(true).movesCount(2).moves(List.of()).build();
        when(allocationOptimizationService.apply(any(AllocationOptimizationRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL + "/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.movesCount").value(2));
    }

    @Test
    @DisplayName("POST /apply: conflict while applying returns HTTP 409")
    void apply_conflict_returns409() throws Exception {
        when(allocationOptimizationService.apply(any(AllocationOptimizationRequest.class)))
                .thenThrow(new ReservationConflictException("Reservations changed while optimizing"));

        mockMvc.perform(post(URL + "/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }
}
