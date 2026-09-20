package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HelloController {

    final UserService userService;

    @GetMapping("/hello")
    public ResponseEntity<UserSecurityRes> hello() {
        var user = userService.getUserSecurityByUsername("CasCas")
                .orElseThrow(() -> new ResourceNotFoundException("Not Found"));

        return ResponseEntity.ok(user);
    }
}
