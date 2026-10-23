package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.config.*;
import com.restaurant.ireservation.dto.request.MultiTableAllocationRequest;
import com.restaurant.ireservation.dto.response.MultiTableAllocationResponse;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.security.SessionManager;
import com.restaurant.ireservation.service.MultiTableAllocationService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MultiTableAllocationController.class)
@Import({SecurityConfig.class, CorsConfig.class, CustomJwtAuthenticationConverter.class})
class MultiTableAllocationControllerTest {
    private static final String URL = "/api/v1/table-allocation/combinations";
    @Autowired MockMvc mvc;
    @MockBean MultiTableAllocationService service;
    @MockBean SessionManager sessionManager;

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STAFF", "ROLE_MANAGER", "ROLE_ADMIN"})
    void authorizedRolesCanAllocateAndList(String role) throws Exception {
        when(service.allocate(any())).thenReturn(MultiTableAllocationResponse.builder().reservationId(10L).totalCapacity(8).build());
        when(service.getCombinations(eq(10L), any())).thenReturn(new ResultPaginationDTO());
        var user = jwt().authorities(new SimpleGrantedAuthority(role));
        mvc.perform(post(URL).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":10,\"tableIds\":[1,2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCapacity").value(8));
        mvc.perform(get(URL).with(user).param("reservationId", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void customersCannotAllocateOrList() throws Exception {
        var user = jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        mvc.perform(post(URL).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":10,\"tableIds\":[1,2]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL).with(user)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCannotAccess() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationId\":10,\"tableIds\":[1,2]}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reservationId\":10,\"tableIds\":[1]}",
            "{\"reservationId\":-1,\"tableIds\":[1,2]}",
            "{\"reservationId\":10,\"tableIds\":[1,null]}",
            "{\"reservationId\":10,\"tableIds\":[1,0]}"})
    void invalidRequestsReturn400(String body) throws Exception {
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void conflictsReturn409() throws Exception {
        when(service.allocate(any(MultiTableAllocationRequest.class)))
                .thenThrow(new ReservationConflictException("Table already reserved"));
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reservationId\":10,\"tableIds\":[1,2]}"))
                .andExpect(status().isConflict());
    }
}
