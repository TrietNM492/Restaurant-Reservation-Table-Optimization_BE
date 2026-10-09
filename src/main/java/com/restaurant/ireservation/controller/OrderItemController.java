package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.AddOrderItemRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateOrderItemRequestDTO;
import com.restaurant.ireservation.dto.response.OrderItemResponseDTO;
import com.restaurant.ireservation.service.OrderItemService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order-items")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemController {

    final OrderItemService orderItemService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'CUSTOMER')")
    @ApiMessage("Add order item successfully")
    public OrderItemResponseDTO addOrderItem(
            @Valid @RequestBody AddOrderItemRequestDTO request
    ) {
        return orderItemService.addOrderItem(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'CUSTOMER')")
    @ApiMessage("Update order item successfully")
    public OrderItemResponseDTO updateOrderItem(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateOrderItemRequestDTO request
    ) {
        return orderItemService.updateOrderItem(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'CUSTOMER')")
    @ApiMessage("Delete order item successfully")
    public void deleteOrderItem(
            @PathVariable("id") Long id
    ) {
        orderItemService.deleteOrderItem(id);
    }
}

