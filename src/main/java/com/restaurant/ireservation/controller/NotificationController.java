package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.dto.response.ReservationInformationNotificationResponse;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.service.NotificationProcessingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class NotificationController {

    NotificationProcessingService notificationService;
    UserRepository userRepository;

    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public ResponseEntity<Page<ReservationInformationNotificationResponse>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
            ) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 50));
        String username = jwt.getSubject();
        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        Long userId = user.getId();
        return ResponseEntity.ok(notificationService.getHistory(userId, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        Long userId = user.getId();
        Map<String, Long> count = Map.of("count", notificationService.getUnreadCount(userId));
        return ResponseEntity.ok(count);
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId
    ) {
        String username = jwt.getSubject();
        User user = userRepository.findUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        Long userId = user.getId();
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }


}
