package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.config.CorsConfig;
import com.restaurant.ireservation.config.CustomJwtAuthenticationConverter;
import com.restaurant.ireservation.config.SecurityConfig;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.security.SessionManager;
import com.restaurant.ireservation.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security MVC Integration Test for Reservation Status Management endpoints.
 * Verifies that Spring Security method authorization (@PreAuthorize) enforces ROLE_ADMIN
 * at the HTTP request layer and blocks ROLE_CUSTOMER with HTTP 403 Forbidden.
 */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, CorsConfig.class, CustomJwtAuthenticationConverter.class, GlobalException.class})
class ReservationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private SessionManager sessionManager;

    // ==========================================
    // Check-in Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-01: ROLE_CUSTOMER receives HTTP 403 Forbidden when calling check-in")
    void checkInReservation_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/check-in")
                .with(jwt().jwt(builder -> builder.subject("customer_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(reservationService, never()).checkInReservation(any(), any());
    }

    @Test
    @DisplayName("SEC-02: ROLE_ADMIN can reach check-in endpoint and returns HTTP 200 OK")
    void checkInReservation_asAdmin_returns200Ok() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .createdBy("customer_user")
                .build();

        when(reservationService.checkInReservation(eq(1L), eq("admin_user")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/check-in")
                .with(jwt().jwt(builder -> builder.subject("admin_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.reservationStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.reservationNo").value("RES-05102026-001"));

        verify(reservationService).checkInReservation(eq(1L), eq("admin_user"));
    }

    // ==========================================
    // Complete Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-03: ROLE_CUSTOMER receives HTTP 403 Forbidden when calling complete")
    void completeReservation_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/complete")
                .with(jwt().jwt(builder -> builder.subject("customer_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(reservationService, never()).completeReservation(any(), any());
    }

    @Test
    @DisplayName("SEC-04: ROLE_ADMIN can reach complete endpoint and returns HTTP 200 OK")
    void completeReservation_asAdmin_returns200Ok() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.COMPLETED)
                .createdBy("customer_user")
                .build();

        when(reservationService.completeReservation(eq(1L), eq("admin_user")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/complete")
                .with(jwt().jwt(builder -> builder.subject("admin_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.reservationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reservationNo").value("RES-05102026-001"));

        verify(reservationService).completeReservation(eq(1L), eq("admin_user"));
    }

    // ==========================================
    // Unauthenticated Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-05: Unauthenticated request to check-in returns HTTP 401 Unauthorized")
    void checkInReservation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/check-in"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).checkInReservation(any(), any());
    }

    @Test
    @DisplayName("SEC-06: Unauthenticated request to complete returns HTTP 401 Unauthorized")
    void completeReservation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/complete"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).completeReservation(any(), any());
    }

    // ==========================================
    // Admin List Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-07: ROLE_CUSTOMER receives HTTP 403 Forbidden when calling getReservations")
    void getReservations_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .with(jwt().jwt(builder -> builder.subject("customer_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(reservationService, never()).getReservations(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-08: ROLE_ADMIN can reach getReservations endpoint and returns HTTP 200 OK")
    void getReservations_asAdmin_returns200Ok() throws Exception {
        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        when(reservationService.getReservations(any(), any(), any(), any(), any(), any()))
                .thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/reservations")
                .with(jwt().jwt(builder -> builder.subject("admin_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        verify(reservationService).getReservations(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-09: Unauthenticated request to getReservations returns HTTP 401 Unauthorized")
    void getReservations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).getReservations(any(), any(), any(), any(), any(), any());
    }

    // ==========================================
    // Confirm Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-10: ROLE_CUSTOMER receives HTTP 403 Forbidden when calling confirmReservation")
    void confirmReservation_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/confirm")
                .with(jwt().jwt(builder -> builder.subject("customer_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(reservationService, never()).confirmReservation(any(), any());
    }

    @Test
    @DisplayName("SEC-11: ROLE_ADMIN can reach confirmReservation endpoint and returns HTTP 200 OK")
    void confirmReservation_asAdmin_returns200Ok() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .createdBy("customer_user")
                .build();

        when(reservationService.confirmReservation(eq(1L), eq("admin_user")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/confirm")
                .with(jwt().jwt(builder -> builder.subject("admin_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.reservationStatus").value("CONFIRMED"));

        verify(reservationService).confirmReservation(eq(1L), eq("admin_user"));
    }

    @Test
    @DisplayName("SEC-12: Unauthenticated request to confirmReservation returns HTTP 401 Unauthorized")
    void confirmReservation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/confirm"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).confirmReservation(any(), any());
    }

    // ==========================================
    // Reject Security Tests
    // ==========================================

    @Test
    @DisplayName("SEC-13: ROLE_CUSTOMER receives HTTP 403 Forbidden when calling rejectReservation")
    void rejectReservation_asCustomer_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/reject")
                .with(jwt().jwt(builder -> builder.subject("customer_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(reservationService, never()).rejectReservation(any(), any(), any());
    }

    @Test
    @DisplayName("SEC-14: ROLE_ADMIN can reach rejectReservation endpoint and returns HTTP 200 OK")
    void rejectReservation_asAdmin_returns200Ok() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CANCELLED)
                .createdBy("customer_user")
                .build();

        when(reservationService.rejectReservation(eq(1L), any(), eq("admin_user")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/reject")
                .with(jwt().jwt(builder -> builder.subject("admin_user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.reservationStatus").value("CANCELLED"));

        verify(reservationService).rejectReservation(eq(1L), any(), eq("admin_user"));
    }

    @Test
    @DisplayName("SEC-15: Unauthenticated request to rejectReservation returns HTTP 401 Unauthorized")
    void rejectReservation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/reject"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).rejectReservation(any(), any(), any());
    }
}
