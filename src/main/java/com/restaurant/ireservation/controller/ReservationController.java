package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
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
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReservationController {

    final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Create reservation successfully")
    public ReservationResponseDTO createReservation(
            @Valid @RequestBody CreateReservationRequestDTO requestDTO,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.createReservation(requestDTO, username);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Update reservation successfully")
    public ReservationResponseDTO updateReservation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequestDTO requestDTO,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.updateReservation(id, requestDTO, username);
    }
}
