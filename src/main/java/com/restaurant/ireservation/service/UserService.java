package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class UserService {

    final UserRepository userRepository;

    public Optional<UserSecurityRes> getUserSecurityByUsername(String username) {
        return userRepository.findOneWithAuthoritiesByUsername(username.toLowerCase())
                .map(this::getUserSecurity);
    }

    public UserSecurityRes getUserSecurity(User user) {
        var res = new UserSecurityRes();
        res.setId(user.getId());
        res.setUsername(user.getUsername());
        res.setEmail(user.getEmail());
        res.setGender(user.getGender().toString());
        res.setSessionId(user.getSessionId());
        res.setRole(user.getRole().getName());
        var permissions = user.getRole().getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        permissions.add(user.getRole().getName());
        res.setPermission(permissions);
        return res;

    }


}
