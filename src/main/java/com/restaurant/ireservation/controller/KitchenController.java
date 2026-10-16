package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import com.restaurant.ireservation.dto.response.KitchenQueueResponseDTO;
import com.restaurant.ireservation.service.KitchenService;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchen/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KitchenController {

    final KitchenService kitchenService;

    @GetMapping
    @ApiMessage("Fetch kitchen orders successfully")
    public List<KitchenQueueResponseDTO> getKitchenOrders(
            @RequestParam(name = "status", required = false) OrderKitchenStatus status,
            @RequestParam(name = "isVip", required = false) Boolean isVip,
            @RequestParam(name = "search", required = false) String search
    ) {
        return kitchenService.getKitchenOrders(status, isVip, search);
    }

    @GetMapping("/{id}")
    @ApiMessage("Fetch kitchen order detail successfully")
    public KitchenQueueResponseDTO getKitchenOrderById(@PathVariable("id") Long id) {
        return kitchenService.getKitchenOrderById(id);
    }
}
