package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.PreparationTimePredictionRequestDTO;
import com.restaurant.ireservation.dto.response.PreparationTimePredictionResponseDTO;
import com.restaurant.ireservation.service.PreparationTimeService;
import com.restaurant.ireservation.dto.request.WaitingTimeRequestDTO;
import com.restaurant.ireservation.dto.response.WaitingTimeResponseDTO;
import com.restaurant.ireservation.service.PredictionService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PredictionController {

    PredictionService predictionService;
    PreparationTimeService preparationTimeService;


    @PostMapping("/waiting-time")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @ApiMessage("Waiting time calculated successfully")
    public WaitingTimeResponseDTO calculateWaitingTime(
            @Valid @RequestBody WaitingTimeRequestDTO requestDTO,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getSubject();
        return predictionService.predictWaitingTime(requestDTO, username);
    }

    @PostMapping("/preparation-time")
    @ApiMessage("Food preparation time estimated successfully")
    public ResponseEntity<PreparationTimePredictionResponseDTO> predictPreparationTime(
            @Valid @RequestBody PreparationTimePredictionRequestDTO request
    ) {
        return ResponseEntity.ok(preparationTimeService.predictPreparationTime(request));
    }
}
