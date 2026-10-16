package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.dto.request.TableRecommendationRequest;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationOption;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationType;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.TableRecommendationService;
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
class TableRecommendationControllerTest {

    static final String URL = "/api/v1/tables/recommendations";

    @Mock
    TableRecommendationService tableRecommendationService;

    @InjectMocks
    TableRecommendationController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalException())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tables/recommendations: returns 200 OK with ranked options")
    void recommend_returns200() throws Exception {
        TableRecommendationResponse response = TableRecommendationResponse.builder()
                .guestCount(4)
                .suggestedPartySize(PartySize.SMALL)
                .availableTableCount(3)
                .recommendations(List.of(RecommendationOption.builder()
                        .rank(1)
                        .type(RecommendationType.SINGLE_TABLE)
                        .tableIds(List.of(7L))
                        .totalCapacity(4)
                        .score(92.5)
                        .build()))
                .build();
        when(tableRecommendationService.recommend(any(TableRecommendationRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":4,\"durationMinutes\":90,\"preferredAreaId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(4))
                .andExpect(jsonPath("$.suggestedPartySize").value("SMALL"))
                .andExpect(jsonPath("$.recommendations[0].rank").value(1))
                .andExpect(jsonPath("$.recommendations[0].type").value("SINGLE_TABLE"))
                .andExpect(jsonPath("$.recommendations[0].tableIds[0]").value(7));
    }

    @Test
    @DisplayName("POST: missing guestCount fails @NotNull validation returning HTTP 400")
    void recommend_missingGuestCount_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST: guestCount = 0 fails @Min validation returning HTTP 400")
    void recommend_zeroGuests_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST: durationMinutes out of range returns HTTP 400")
    void recommend_durationTooLong_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":2,\"durationMinutes\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST: unknown preferred area returns HTTP 404")
    void recommend_unknownArea_returns404() throws Exception {
        when(tableRecommendationService.recommend(any(TableRecommendationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Table area not found with id: 99"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":2,\"preferredAreaId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/tables/recommendations/alternatives: returns 200 OK with alternatives")
    void recommendAlternatives_returns200() throws Exception {
        TableRecommendationResponse response = TableRecommendationResponse.builder()
                .guestCount(4)
                .recommendations(List.of())
                .alternatives(List.of(TableRecommendationResponse.AlternativeOption.builder()
                        .rank(1)
                        .type(TableRecommendationResponse.AlternativeType.LATER_START)
                        .shiftMinutes(30)
                        .score(88.5)
                        .explanation("Starts 30 minutes later than requested")
                        .build()))
                .build();
        when(tableRecommendationService.recommendAlternatives(any(TableRecommendationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(URL + "/alternatives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":4,\"maxAlternatives\":3,\"maxShiftMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternatives[0].type").value("LATER_START"))
                .andExpect(jsonPath("$.alternatives[0].shiftMinutes").value(30))
                .andExpect(jsonPath("$.alternatives[0].explanation").value("Starts 30 minutes later than requested"));
    }

    @Test
    @DisplayName("POST /alternatives: maxShiftMinutes out of range returns HTTP 400")
    void recommendAlternatives_invalidShift_returns400() throws Exception {
        mockMvc.perform(post(URL + "/alternatives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":4,\"maxShiftMinutes\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/tables/recommendations: alternativeMode is accepted")
    void recommend_acceptsAlternativeMode() throws Exception {
        when(tableRecommendationService.recommend(any(TableRecommendationRequest.class)))
                .thenReturn(TableRecommendationResponse.builder().guestCount(2).build());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":2,\"alternativeMode\":\"NEVER\"}"))
                .andExpect(status().isOk());
    }
}
