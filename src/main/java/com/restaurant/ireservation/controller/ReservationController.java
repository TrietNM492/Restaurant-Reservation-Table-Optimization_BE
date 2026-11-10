package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.RejectReservationRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.service.ReservationService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

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

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Cancel reservation successfully")
    public ReservationResponseDTO cancelReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.cancelReservation(id, username);
    }

    @PostMapping("/{id}/check-in")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ApiMessage("Check-in reservation successfully")
    public ReservationResponseDTO checkInReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.checkInReservation(id, username);
    }

    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ApiMessage("Complete reservation successfully")
    public ReservationResponseDTO completeReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.completeReservation(id, username);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ApiMessage("Fetch all reservations successfully")
    public ResultPaginationDTO getReservations(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) ReservationStatus status,
            @RequestParam(name = "type", required = false) ReservationType type,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            Pageable pageable
    ) {
        return reservationService.getReservations(q, status, type, from, to, pageable);
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ApiMessage("Confirm reservation successfully")
    public ReservationResponseDTO confirmReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.confirmReservation(id, username);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ApiMessage("Reject reservation successfully")
    public ReservationResponseDTO rejectReservation(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RejectReservationRequestDTO requestDTO,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.rejectReservation(id, requestDTO, username);
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Get my reservations successfully")
    public List<ReservationResponseDTO> getMyReservations(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        return reservationService.getMyReservations(username);
    }
}
