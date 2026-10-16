package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.RoomReservationBookingRequestDTO;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomReservationControllerTest {

    @Mock
    ReservationService reservationService;

    @InjectMocks
    RoomReservationController roomReservationController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(roomReservationController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(jwtResolver)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("RR-CTRL-01: Valid VIP room reservation request returns HTTP 201 Created")
    void bookRoomReservation_validRequest_returns201() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .note("VIP Dining Experience")
                .build();

        ReservationResponseDTO mockResponse = ReservationResponseDTO.builder()
                .id(200L)
                .reservationNo("RES-VIP-002")
                .partySize(PartySize.MEDIUM)
                .reservationType(ReservationType.VIP_ROOM)
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .note("VIP Dining Experience")
                .rooms(List.of(ReservationResponseDTO.RoomSummaryDTO.builder()
                        .id(10L)
                        .roomName("Diamond Suite")
                        .roomType(RoomType.VIP_ROOM)
                        .capacity(10)
                        .price(150.0)
                        .build()))
                .createdBy("test_customer")
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.reservationNo").value("RES-VIP-002"))
                .andExpect(jsonPath("$.reservationType").value("VIP_ROOM"))
                .andExpect(jsonPath("$.rooms[0].roomName").value("Diamond Suite"));
    }

    @Test
    @DisplayName("RR-CTRL-02: Null partySize fails validation returning HTTP 400 Bad Request")
    void bookRoomReservation_nullPartySize_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(null)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("RR-CTRL-03: Past startTime fails validation returning HTTP 400 Bad Request")
    void bookRoomReservation_pastStartTime_returns400() throws Exception {
        Instant startTime = Instant.now().minus(Duration.ofHours(1));
        Instant endTime = Instant.now().plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("RR-CTRL-04: Empty roomIds fails validation returning HTTP 400 Bad Request")
    void bookRoomReservation_emptyRoomIds_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of())
                .build();

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("RR-CTRL-05: Null roomIds fails validation returning HTTP 400 Bad Request")
    void bookRoomReservation_nullRoomIds_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(null)
                .build();

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("RR-CTRL-06: End time before start time throws RequestInvalidException returning HTTP 400")
    void bookRoomReservation_endTimeBeforeStartTime_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(4));
        Instant endTime = Instant.now().plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new RequestInvalidException("End time must be after start time"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("End time must be after start time"));
    }

    @Test
    @DisplayName("RR-CTRL-07: Non-existent room throws ResourceNotFoundException returning HTTP 404")
    void bookRoomReservation_roomNotFound_returns404() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(999L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new ResourceNotFoundException("One or more requested rooms do not exist"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("One or more requested rooms do not exist"));
    }

    @Test
    @DisplayName("RR-CTRL-08: Non-VIP room throws RequestInvalidException returning HTTP 400")
    void bookRoomReservation_nonVipRoom_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(5L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Room Standard Hall is not a VIP room"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Room Standard Hall is not a VIP room"));
    }

    @Test
    @DisplayName("RR-CTRL-09: Room in MAINTENANCE throws RequestInvalidException returning HTTP 400")
    void bookRoomReservation_maintenanceRoom_returns400() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new RequestInvalidException("Room Diamond Suite is currently under maintenance"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Room Diamond Suite is currently under maintenance"));
    }

    @Test
    @DisplayName("RR-CTRL-10: Overlapping room reservation throws ReservationConflictException returning HTTP 409")
    void bookRoomReservation_conflict_returns409() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new ReservationConflictException("Room Diamond Suite is already reserved during the requested time slot"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Reservation Conflict"))
                .andExpect(jsonPath("$.message").value("Room Diamond Suite is already reserved during the requested time slot"));
    }

    @Test
    @DisplayName("RR-CTRL-11: AccessDeniedException maps to HTTP 403 Forbidden")
    void bookRoomReservation_accessDenied_returns403() throws Exception {
        Instant startTime = Instant.now().plus(Duration.ofHours(2));
        Instant endTime = startTime.plus(Duration.ofHours(2));

        RoomReservationBookingRequestDTO request = RoomReservationBookingRequestDTO.builder()
                .partySize(PartySize.MEDIUM)
                .startTime(startTime)
                .endTime(endTime)
                .roomIds(List.of(10L))
                .build();

        when(reservationService.createRoomReservation(any(RoomReservationBookingRequestDTO.class), eq("test_customer")))
                .thenThrow(new AccessDeniedException("Access is denied"));

        mockMvc.perform(post("/api/v1/room-reservation/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }
}
