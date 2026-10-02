package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.security.CustomUserDetails;
import com.restaurant.ireservation.service.CustomerService;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserController {

    final UserService userService;
    final CustomerService customerService;
    final StaffInformationService staffInformationService;

    @PostMapping("/change-avatar")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<String> changeAvatar(@RequestPart("avatarUrl")MultipartFile file, @AuthenticationPrincipal CustomUserDetails user) throws URISyntaxException, IOException {
        Long userId = user.getUser().getId();
        if (userId == null) {
            throw new RuntimeException("You are not login");
        }
        userService.changeAvatarAccount(file, userId);


        return ResponseEntity.ok("Upload Avatar");

    }

}
