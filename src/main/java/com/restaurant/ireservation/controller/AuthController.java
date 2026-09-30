package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AuthController {

    final AuthenticationManagerBuilder authenticationManagerBuilder;
    final AuthService authService;

    @Value("${ijres.jwt.refresh-token-validity-sec}")
    Long refreshTokenExpiration;

    @PostMapping("/register")
    public CustomerResponse registerByCustomer(@Valid @RequestBody RegisterAccount registerAccount) {
        if (!registerAccount.getPassword().equals(registerAccount.getPasswordAgain())) {
            throw new RuntimeException("Password not match");
        }

        return authService.register(registerAccount);


    }


}
