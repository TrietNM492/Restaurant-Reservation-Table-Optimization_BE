package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.TableRecommendationRequest;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse;
import com.restaurant.ireservation.service.TableRecommendationService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tables/recommendations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableRecommendationController {

    TableRecommendationService tableRecommendationService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    @ApiMessage("Fetch table recommendations successfully")
    public TableRecommendationResponse recommendTables(@Valid @RequestBody TableRecommendationRequest request) {
        return tableRecommendationService.recommend(request);
    }

    /**
     * "What else can we offer?": always returns alternative slots (earlier / later start, shorter stay,
     * merged tables) next to the recommendations for the requested slot.
     */
    @PostMapping("/alternatives")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_MANAGER', 'ROLE_ADMIN')")
    @ApiMessage("Fetch alternative table recommendations successfully")
    public TableRecommendationResponse recommendAlternatives(@Valid @RequestBody TableRecommendationRequest request) {
        return tableRecommendationService.recommendAlternatives(request);
    }
}
