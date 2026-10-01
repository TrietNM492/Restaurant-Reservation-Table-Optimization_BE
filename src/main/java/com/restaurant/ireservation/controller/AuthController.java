package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.LoginDTO;
import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.dto.response.ResLoginDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.security.CustomUserDetails;
import com.restaurant.ireservation.service.AuthService;
import com.restaurant.ireservation.util.constants.Constants;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AuthController {

    final AuthenticationManagerBuilder authenticationManagerBuilder;
    final AuthService authService;
    final PasswordEncoder passwordEncoder;

    @Value("${ijres.jwt.refresh-token-validity-sec}")
    Long refreshTokenExpiration;

    @PostMapping("/register")
    public CustomerResponse registerByCustomer(@Valid @RequestBody RegisterAccount registerAccount) {
        if (!registerAccount.getPassword().equals(registerAccount.getPasswordAgain())) {
            throw new RuntimeException("Password not match");
        }

        return authService.register(registerAccount);


    }

    @PostMapping("/login")
    public ResponseEntity<ResLoginDTO> loginByCustomer(@Valid @RequestBody LoginDTO loginDTO) {
        var usernamePasswordAuthentication = new UsernamePasswordAuthenticationToken(
                loginDTO.username().toLowerCase(),
                loginDTO.password()
        );
        try {
            Authentication auth = authenticationManagerBuilder.getObject()
                    .authenticate(usernamePasswordAuthentication);
            SecurityContextHolder.getContext().setAuthentication(auth);
            var userDetails = (CustomUserDetails) auth.getPrincipal();
            var user = userDetails.getUser();
            System.out.println(
                    passwordEncoder.matches(
                            loginDTO.password(),
                            "$2a$10$okrGlkwZwWlIhwqoc.w.Tu9JWb3eRnsbeAMBh0xVCS5lJfoFDKIn2"
                            ));
            var res = authService.generateResLoginDTO(user);
            ResponseCookie cookie = ResponseCookie
                    .from("refresh_token", authService.getRefreshTokenById(user.getId()))
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration)
                    .build();
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(res);
        } catch (BadCredentialsException e) {
            throw new RequestInvalidException("Username or password incorrect");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @CookieValue(name = "refresh_token", defaultValue = Constants.REFRESH_TOKEN_EXPIRED) String refreshToken
    ) {
        if (refreshToken.equals(Constants.REFRESH_TOKEN_EXPIRED)) {
            throw new BadCredentialsException("Refresh Token Invalid!");
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok("Logout OK");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ResLoginDTO> refreshToken(
            @CookieValue(name = "refresh_token", required = true) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new RequestInvalidException("Not found Refresh Token");
        }
        var resLoginDto = authService.processRefreshToken(refreshToken);
        var cookie = ResponseCookie
                .from("refresh_token", authService.getRefreshTokenById(resLoginDto.getUser().getId()))
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(resLoginDto);
    }


}
