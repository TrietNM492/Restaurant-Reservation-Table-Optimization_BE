package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimeBreakdownDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.service.PreparationTimeService;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    static final String URL = "/api/v1/predictions/preparation-time";

    @Mock
    PreparationTimeService preparationTimeService;

    @InjectMocks
    PredictionController predictionController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(predictionController)
                .setControllerAdvice(new GlobalException())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /preparation-time: returns 200 OK with prediction details")
    void predictPreparationTime_returns200() throws Exception {
        PreparationTimeBreakdownDTO breakdown = PreparationTimeBreakdownDTO.builder()
                .baseCookingMinutes(20)
                .queueWaitingMinutes(5)
                .historicalBiasMinutes(0)
                .activeKitchenOrdersCount(3)
                .parallelKitchenSlots(3)
                .isVipPriority(false)
                .longestDishName("Grilled Chicken")
                .longestDishMinutes(20)
                .build();

        PreparationTimePredictionResponseDTO response = PreparationTimePredictionResponseDTO.builder()
                .orderId(12L)
                .estimatedMinutes(25)
                .estimatedReadyTime(Instant.now().plusSeconds(1500))
                .breakdown(breakdown)
                .calculationTimestamp(Instant.now())
                .build();

        when(preparationTimeService.predictPreparationTime(any(PreparationTimePredictionRequestDTO.class)))
                .thenReturn(response);

        PreparationTimePredictionRequestDTO request = PreparationTimePredictionRequestDTO.builder()
                .orderId(12L)
                .build();

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(12))
                .andExpect(jsonPath("$.estimatedMinutes").value(25))
                .andExpect(jsonPath("$.breakdown.baseCookingMinutes").value(20))
                .andExpect(jsonPath("$.breakdown.queueWaitingMinutes").value(5))
                .andExpect(jsonPath("$.breakdown.parallelKitchenSlots").value(3))
                .andExpect(jsonPath("$.breakdown.longestDishName").value("Grilled Chicken"));
    }

    @Test
    @DisplayName("POST /preparation-time: returns 400 Bad Request when request is invalid")
    void predictPreparationTime_invalidRequest_returns400() throws Exception {
        when(preparationTimeService.predictPreparationTime(any(PreparationTimePredictionRequestDTO.class)))
                .thenThrow(new RequestInvalidException("Either orderId or a non-empty items list must be provided."));

        PreparationTimePredictionRequestDTO request = PreparationTimePredictionRequestDTO.builder().build();

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Either orderId or a non-empty items list must be provided."));
    }
}
