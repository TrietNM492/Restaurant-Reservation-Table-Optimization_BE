package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.CreateOrderRequestDTO;
import com.restaurant.ireservation.dto.response.OrderResponseDTO;
import com.restaurant.ireservation.service.OrderService;
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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderController {

    final OrderService orderService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'CUSTOMER')")
    @ApiMessage("Create order successfully")
    public OrderResponseDTO createOrder(
            @Valid @RequestBody CreateOrderRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = (jwt != null) ? jwt.getSubject() : "anonymous";
        return orderService.createOrder(request, username);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER') or hasRole('CUSTOMER')")
    @ApiMessage("Get customer order details successfully")
    public OrderResponseDTO getCustomerOrderStatus(
            @RequestParam(name = "orderId", required = false) Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = (jwt != null) ? jwt.getSubject() : null;
        return orderService.getCustomerOrderStatus(orderId, username);
    }
}
