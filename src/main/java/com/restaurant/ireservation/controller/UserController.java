package com.restaurant.ireservation.controller;

import com.nimbusds.jose.proc.SecurityContext;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.dto.request.ChangeCustomerProfileDTO;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.error.UnauthorizeException;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.CustomUserDetails;
import com.restaurant.ireservation.service.CustomerService;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserController {

    final UserService userService;
    final UserRepository userRepository;
    final CustomerService customerService;

    @PostMapping("/change-avatar")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<String> changeAvatar(@RequestPart("avatarUrl")MultipartFile file, @AuthenticationPrincipal CustomUserDetails user) throws URISyntaxException, IOException {
        Long userId = user.getUser().getId();
        if (userId == null) {
            throw new RuntimeException("You are not login");
        }
        String filename = userService.changeAvatarAccount(file, userId);


        return ResponseEntity.ok(String.format("newAvatar: %s", filename));

    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
//    @Secured("ROLE_CUSTOMER")
    public ResponseEntity<CustomerResponse> updateProfile(@Valid @RequestBody ChangeCustomerProfileDTO profileDTO, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        Long userId = user.getId();
        if (userId == null) {
            throw new UnauthorizeException("Not Login, Can't do that!");
        }

        if (!userRepository.existsById(userId)) {
            throw new RequestInvalidException("This Error for User Profile");
        }

        CustomerResponse response = customerService.editCustomerProfile(profileDTO, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<CustomerResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        org.springframework.security.core.Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println(authentication);
        System.out.println(jwt.getClaims());
        authentication.getAuthorities()
                .forEach(a -> System.out.println(a.getAuthority()));
        String username = jwt.getSubject();
        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        Long userId = user.getId();
        if (userId == null) {
            throw new UnauthorizeException("Not Login, Can't do that!");
        }
        var res = customerService.fetchCustomerProfile(userId);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/activated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> changeUserStatus(@RequestBody Long userId) {
        if (userId == null) {
            throw new RequestInvalidException("User ID missing request!");
        }
        String message = userService.toggleActionActivated(userId);
        return ResponseEntity.ok(message);
    }


//    Change Password -> Nhap mk cu -> nhap mk moi, neu mk cu ok thi doi mk moi
//    Reset Pass/quen mk -> gui mail co link activated -> user click xong keu nhap mk moi


}
