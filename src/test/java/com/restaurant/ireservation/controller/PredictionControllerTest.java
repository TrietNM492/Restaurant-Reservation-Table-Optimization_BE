package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.WaitStatus;
import com.restaurant.ireservation.dto.request.WaitingTimeRequestDTO;
import com.restaurant.ireservation.dto.response.WaitingTimeResponseDTO;
import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimeBreakdownDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.PredictionService;
import com.restaurant.ireservation.util.ResFormatResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.service.PreparationTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    static final String URL_PREDICTIONS = "/api/v1/predictions/preparation-time";
    static final String URL_WAITING = "/api/v1/predictions/waiting-time";
    static final String USERNAME = "customer@example.com";

    @Mock
    PredictionService predictionService;
    PreparationTimeService preparationTimeService;

    @InjectMocks
    PredictionController predictionController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = mock(Jwt.class);
        lenient().when(mockJwt.getSubject()).thenReturn(USERNAME);

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockJwt;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(predictionController)
                .setControllerAdvice(new GlobalException(), new ResFormatResponse())
                .setCustomArgumentResolvers(jwtResolver)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("TC-01: Valid request returns 200 OK with waiting time prediction payload in RestResponse envelope")
    void calculateWaitingTime_validRequest_returns200() throws Exception {
        WaitingTimeResponseDTO responseDTO = WaitingTimeResponseDTO.builder()
                .reservationId(100L)
                .waitStatus(WaitStatus.WAITING)
                .estimatedWaitMinutes(15)
                .estimatedReadyTime(Instant.parse("2026-10-23T19:15:00Z"))
                .message("Table is currently being cleaned. Estimated ready at 19:15 based on standard turnaround.")
                .build();

        when(predictionService.predictWaitingTime(any(WaitingTimeRequestDTO.class), eq(USERNAME)))
                .thenReturn(responseDTO);

        WaitingTimeRequestDTO requestDTO = new WaitingTimeRequestDTO(100L);

        mockMvc.perform(post(URL_WAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.reservationId").value(100))
                .andExpect(jsonPath("$.data.waitStatus").value("WAITING"))
                .andExpect(jsonPath("$.data.estimatedWaitMinutes").value(15))
                .andExpect(jsonPath("$.data.estimatedReadyTime").exists())
                .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    @Test
    @DisplayName("TC-02: Missing reservationId returns 400 Bad Request")
    void calculateWaitingTime_missingReservationId_returns400() throws Exception {
        WaitingTimeRequestDTO requestDTO = new WaitingTimeRequestDTO(null);

        mockMvc.perform(post(URL_WAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-03: Ownership mismatch throws 403 Forbidden")
    void calculateWaitingTime_ownershipMismatch_returns403() throws Exception {
        when(predictionService.predictWaitingTime(any(WaitingTimeRequestDTO.class), eq(USERNAME)))
                .thenThrow(new AccessDeniedException("You are not allowed to view waiting time for this reservation"));

        WaitingTimeRequestDTO requestDTO = new WaitingTimeRequestDTO(100L);

        mockMvc.perform(post(URL_WAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-04: Non-existent reservation returns 404 Not Found")
    void calculateWaitingTime_notFound_returns404() throws Exception {
        when(predictionService.predictWaitingTime(any(WaitingTimeRequestDTO.class), eq(USERNAME)))
                .thenThrow(new ResourceNotFoundException("Reservation not found with id: 999"));

        WaitingTimeRequestDTO requestDTO = new WaitingTimeRequestDTO(999L);

        mockMvc.perform(post(URL_WAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound());
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

        mockMvc.perform(post(URL_PREDICTIONS)
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

        mockMvc.perform(post(URL_PREDICTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Either orderId or a non-empty items list must be provided."));
    }
}
