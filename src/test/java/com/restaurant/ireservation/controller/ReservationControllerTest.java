package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.ReservationService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    ReservationService reservationService;

    @InjectMocks
    ReservationController reservationController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = mock(Jwt.class);
        lenient().when(mockJwt.getSubject()).thenReturn("test_customer");

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

        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(jwtResolver)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("TC-01: Valid request returns HTTP 201 Created")
    void createReservation_validRequest_returns201() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        CreateReservationRequestDTO request = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note("Quiet table")
                .build();

        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Quiet table")
                .createdBy("test_customer")
                .build();

        when(reservationService.createReservation(any(CreateReservationRequestDTO.class), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reservationNo").value("RES-05102026-001"));
    }

    @Test
    @DisplayName("TC-03: Past startTime fails @Future validation returning HTTP 400")
    void createReservation_pastStartTime_returns400() throws Exception {
        Instant pastStartTime = Instant.now().minus(Duration.ofHours(1));
        Instant endTime = Instant.now().plus(Duration.ofHours(1));

        CreateReservationRequestDTO request = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(pastStartTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("TC-05: Empty tableIds fails @NotEmpty validation returning HTTP 400")
    void createReservation_emptyTableIds_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        CreateReservationRequestDTO request = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("TC-17: Note exceeding 500 characters fails @Size validation returning HTTP 400")
    void createReservation_noteTooLong_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));
        String longNote = "A".repeat(501);

        CreateReservationRequestDTO request = CreateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note(longNote)
                .build();

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    // ==========================================
    // PHASE 2: UPDATE RESERVATION CONTROLLER TESTS
    // ==========================================

    @Test
    @DisplayName("P2-TC-01: Valid PUT request returns HTTP 200 OK")
    void updateReservation_validRequest_returns200() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note("Updated note")
                .build();

        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("Updated note")
                .createdBy("test_customer")
                .build();

        when(reservationService.updateReservation(eq(1L), any(UpdateReservationRequestDTO.class), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reservationNo").value("RES-05102026-001"))
                .andExpect(jsonPath("$.note").value("Updated note"));
    }

    @Test
    @DisplayName("P2-TC-02: Past startTime fails @Future validation returning HTTP 400")
    void updateReservation_pastStartTime_returns400() throws Exception {
        Instant pastStartTime = Instant.now().minus(Duration.ofHours(1));
        Instant endTime = Instant.now().plus(Duration.ofHours(1));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(pastStartTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("P2-TC-03: Empty tableIds fails @NotEmpty validation returning HTTP 400")
    void updateReservation_emptyTableIds_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(Collections.emptyList())
                .build();

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("P2-TC-04: Note exceeding 500 characters fails @Size validation returning HTTP 400")
    void updateReservation_noteTooLong_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));
        String longNote = "A".repeat(501);

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .note(longNote)
                .build();

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("P2-TC-05: Reservation not found maps correctly to HTTP 404")
    void updateReservation_notFound_returns404() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationService.updateReservation(eq(999L), any(UpdateReservationRequestDTO.class), eq("test_customer")))
                .thenThrow(new ResourceNotFoundException("Reservation not found with id: 999"));

        mockMvc.perform(put("/api/v1/reservations/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 999"));
    }

    @Test
    @DisplayName("P2-TC-06: Conflicting reservation maps correctly to HTTP 409")
    void updateReservation_conflict_returns409() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationService.updateReservation(eq(1L), any(UpdateReservationRequestDTO.class), eq("test_customer")))
                .thenThrow(new ReservationConflictException("Table 10 is already reserved during the requested time slot"));

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation Conflict"))
                .andExpect(jsonPath("$.message").value("Table 10 is already reserved during the requested time slot"));
    }

    @Test
    @DisplayName("P2-TC-07: Ownership/authorization failure maps to HTTP 403 Forbidden")
    void updateReservation_ownershipMismatch_returns403() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        UpdateReservationRequestDTO request = UpdateReservationRequestDTO.builder()
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .startTime(startTime)
                .endTime(endTime)
                .tableIds(List.of(1L))
                .build();

        when(reservationService.updateReservation(eq(1L), any(UpdateReservationRequestDTO.class), eq("test_customer")))
                .thenThrow(new AccessDeniedException("You are not allowed to update this reservation"));

        mockMvc.perform(put("/api/v1/reservations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You are not allowed to update this reservation"));
    }

    @Test
    @DisplayName("P3-TC-01: Valid cancellation request returns HTTP 200 OK")
    void cancelReservation_validRequest_returns200() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CANCELLED)
                .createdBy("test_customer")
                .build();

        when(reservationService.cancelReservation(eq(1L), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.reservationNo").value("RES-05102026-001"));
    }

    @Test
    @DisplayName("P3-TC-02: Non-existent reservation maps correctly to HTTP 404 Not Found")
    void cancelReservation_notFound_returns404() throws Exception {
        when(reservationService.cancelReservation(eq(999L), eq("test_customer")))
                .thenThrow(new ResourceNotFoundException("Reservation not found with id: 999"));

        mockMvc.perform(post("/api/v1/reservations/999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 999"));
    }

    @Test
    @DisplayName("P3-TC-03: Ownership mismatch maps correctly to HTTP 403 Forbidden")
    void cancelReservation_ownershipMismatch_returns403() throws Exception {
        when(reservationService.cancelReservation(eq(1L), eq("test_customer")))
                .thenThrow(new AccessDeniedException("You are not allowed to cancel this reservation"));

        mockMvc.perform(post("/api/v1/reservations/1/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You are not allowed to cancel this reservation"));
    }

    @Test
    @DisplayName("P3-TC-04: Non-cancellable status maps correctly to HTTP 400 Bad Request")
    void cancelReservation_invalidStatus_returns400() throws Exception {
        when(reservationService.cancelReservation(eq(1L), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Only reservations with PENDING or CONFIRMED status can be cancelled"));

        mockMvc.perform(post("/api/v1/reservations/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only reservations with PENDING or CONFIRMED status can be cancelled"));
    }

    @Test
    @DisplayName("P3-TC-05: Already cancelled reservation maps correctly to HTTP 400 Bad Request")
    void cancelReservation_alreadyCancelled_returns400() throws Exception {
        when(reservationService.cancelReservation(eq(1L), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Reservation is already cancelled"));

        mockMvc.perform(post("/api/v1/reservations/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Reservation is already cancelled"));
    }

    // ==========================================
    // Phase 4: Check-in Tests
    // ==========================================

    @Test
    @DisplayName("P4-SEC-01: Check-in endpoint is secured with ROLE_ADMIN")
    void checkInReservation_isSecuredWithRoleAdmin() throws NoSuchMethodException {
        PreAuthorize annotation = ReservationController.class
                .getMethod("checkInReservation", Long.class, Jwt.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(annotation, "Endpoint must have @PreAuthorize annotation");
        assertEquals("hasAuthority('ROLE_ADMIN')", annotation.value());
    }

    @Test
    @DisplayName("P4-TC-01: Valid admin check-in request returns HTTP 200 OK")
    void checkInReservation_validRequest_returns200() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.IN_PROGRESS)
                .createdBy("test_customer")
                .build();

        when(reservationService.checkInReservation(eq(1L), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reservationStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reservationNo").value("RES-05102026-001"));
    }

    @Test
    @DisplayName("P4-TC-02: Non-existent reservation for check-in maps to HTTP 404 Not Found")
    void checkInReservation_notFound_returns404() throws Exception {
        when(reservationService.checkInReservation(eq(999L), eq("test_customer")))
                .thenThrow(new ResourceNotFoundException("Reservation not found with id: 999"));

        mockMvc.perform(post("/api/v1/reservations/999/check-in"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 999"));
    }

    @Test
    @DisplayName("P4-TC-03: Access denied for check-in maps to HTTP 403 Forbidden")
    void checkInReservation_accessDenied_returns403() throws Exception {
        when(reservationService.checkInReservation(eq(1L), eq("test_customer")))
                .thenThrow(new AccessDeniedException("You are not allowed to check in this reservation"));

        mockMvc.perform(post("/api/v1/reservations/1/check-in"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You are not allowed to check in this reservation"));
    }

    @Test
    @DisplayName("P4-TC-04: Invalid status for check-in maps to HTTP 400 Bad Request")
    void checkInReservation_invalidStatus_returns400() throws Exception {
        when(reservationService.checkInReservation(eq(1L), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Only reservations with PENDING or CONFIRMED status can be checked in"));

        mockMvc.perform(post("/api/v1/reservations/1/check-in"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only reservations with PENDING or CONFIRMED status can be checked in"));
    }

    @Test
    @DisplayName("P4-TC-04b: Premature check-in maps to HTTP 400 Bad Request")
    void checkInReservation_premature_returns400() throws Exception {
        when(reservationService.checkInReservation(eq(1L), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Cannot check in before the scheduled reservation time"));

        mockMvc.perform(post("/api/v1/reservations/1/check-in"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot check in before the scheduled reservation time"));
    }

    // ==========================================
    // Phase 4: Complete Tests
    // ==========================================

    @Test
    @DisplayName("P4-SEC-02: Complete endpoint is secured with ROLE_ADMIN")
    void completeReservation_isSecuredWithRoleAdmin() throws NoSuchMethodException {
        PreAuthorize annotation = ReservationController.class
                .getMethod("completeReservation", Long.class, Jwt.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(annotation, "Endpoint must have @PreAuthorize annotation");
        assertEquals("hasAuthority('ROLE_ADMIN')", annotation.value());
    }

    @Test
    @DisplayName("P4-TC-05: Valid admin complete request returns HTTP 200 OK")
    void completeReservation_validRequest_returns200() throws Exception {
        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.COMPLETED)
                .createdBy("test_customer")
                .build();

        when(reservationService.completeReservation(eq(1L), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/reservations/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reservationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.reservationNo").value("RES-05102026-001"));
    }

    @Test
    @DisplayName("P4-TC-06: Non-existent reservation for complete maps to HTTP 404 Not Found")
    void completeReservation_notFound_returns404() throws Exception {
        when(reservationService.completeReservation(eq(999L), eq("test_customer")))
                .thenThrow(new ResourceNotFoundException("Reservation not found with id: 999"));

        mockMvc.perform(post("/api/v1/reservations/999/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 999"));
    }

    @Test
    @DisplayName("P4-TC-07: Access denied for complete maps to HTTP 403 Forbidden")
    void completeReservation_accessDenied_returns403() throws Exception {
        when(reservationService.completeReservation(eq(1L), eq("test_customer")))
                .thenThrow(new AccessDeniedException("You are not allowed to complete this reservation"));

        mockMvc.perform(post("/api/v1/reservations/1/complete"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You are not allowed to complete this reservation"));
    }

    @Test
    @DisplayName("P4-TC-08: Invalid status for complete maps to HTTP 400 Bad Request")
    void completeReservation_invalidStatus_returns400() throws Exception {
        when(reservationService.completeReservation(eq(1L), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Only reservations with IN_PROGRESS status can be completed"));

        mockMvc.perform(post("/api/v1/reservations/1/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only reservations with IN_PROGRESS status can be completed"));
    }

    // ==========================================
    // Phase 5: Customer Reservation History Tests
    // ==========================================

    @Test
    @DisplayName("P5-TC-01: Valid request returns HTTP 200 and list of customer reservations")
    void getMyReservations_authenticatedCustomer_returns200AndList() throws Exception {
        ReservationResponseDTO dto1 = ReservationResponseDTO.builder()
                .id(1L)
                .reservationNo("RES-05102026-001")
                .partySize(PartySize.SMALL)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.PENDING)
                .createdBy("test_customer")
                .build();

        ReservationResponseDTO dto2 = ReservationResponseDTO.builder()
                .id(2L)
                .reservationNo("RES-05102026-002")
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.TABLE)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .createdBy("test_customer")
                .build();

        when(reservationService.getMyReservations("test_customer")).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/v1/reservations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].reservationNo").value("RES-05102026-001"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].reservationNo").value("RES-05102026-002"));
    }

    @Test
    @DisplayName("P5-TC-02: Customer with no reservations returns HTTP 200 and empty list")
    void getMyReservations_noReservations_returns200AndEmptyList() throws Exception {
        when(reservationService.getMyReservations("test_customer")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reservations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("P5-TC-03: Unauthenticated or access denied maps to HTTP 403 Forbidden")
    void getMyReservations_accessDenied_returns403() throws Exception {
        when(reservationService.getMyReservations("test_customer"))
                .thenThrow(new AccessDeniedException("User not authenticated"));

        mockMvc.perform(get("/api/v1/reservations/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("User not authenticated"));
    }
}