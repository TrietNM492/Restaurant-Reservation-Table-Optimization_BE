package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.RoomReservationBookingRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.service.ReservationService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/room-reservation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomReservationController {

    ReservationService reservationService;

    @PostMapping("/booking")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Create VIP room reservation successfully")
    public ReservationResponseDTO bookRoomReservation(
            @Valid @RequestBody RoomReservationBookingRequestDTO requestDTO,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.createRoomReservation(requestDTO, username);
    }
}
