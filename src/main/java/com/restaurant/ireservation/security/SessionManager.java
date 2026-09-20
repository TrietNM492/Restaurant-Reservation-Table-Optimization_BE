package com.restaurant.ireservation.security;


import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class SessionManager {
    final UserRepository userRepository;
    final UserService userService;

    public String createSession(Long id) {
        String newSession = UUID.randomUUID().toString();
        int updated = userRepository.updateSessionIdById(id, newSession);
        if (updated > 0) {
            return newSession;
        }
        throw new ResourceNotFoundException("User Not Correct!");
    }

    public boolean isValidSession(String username, String sessionId) {
        return userService.getUserSecurityByUsername(username.toLowerCase())
                .map(user -> sessionId.equals(user.getSessionId()))
                .orElse(false);
    }

    public void invalidateSession(String username) {
        final String userLower = username.toLowerCase();
        userRepository.findUserByUsername(userLower).ifPresent(user -> {
            user.setSessionId(null);
            userRepository.save(user);
        });
    }

}
