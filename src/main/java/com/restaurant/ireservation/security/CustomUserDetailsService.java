package com.restaurant.ireservation.security;

import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.UserNotActivatedException;
import com.restaurant.ireservation.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String lowercaseUsername = username.toLowerCase();

        return userService.getUserSecurityByUsername(lowercaseUsername)
                .map(user -> {
                    try {
                        return createUserDetails(lowercaseUsername, user);
                    } catch (UserNotActivatedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseThrow(() -> new UsernameNotFoundException("User or password incorrect!"));
    }

    private UserDetails createUserDetails(String username, UserSecurityRes user) throws UserNotActivatedException {
        if (!user.isActive()) {
            throw new UserNotActivatedException("User " + username + " was not activated");
        }
        return new CustomUserDetails(user);
    }
}
