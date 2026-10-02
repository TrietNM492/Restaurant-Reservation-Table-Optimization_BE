package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import com.restaurant.ireservation.security.JwtService;
import com.restaurant.ireservation.security.SessionManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class UserService {

    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    final RoleRepository roleRepository;
    final JwtService jwtService;
    final FileService fileService;


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
        res.setActive(user.isActive());
        res.setFullname(user.getFullname());
        res.setPassword(user.getPassword());
        var permissions = user.getRole().getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        permissions.add(user.getRole().getName());
        res.setPermission(permissions);
        return res;

    }

    public UserSecurityRes createUser(RegisterAccount registerAccount) {
        String encryptedPassword = passwordEncoder.encode(registerAccount.getPassword());
        Role role = roleRepository.findOneByName(AuthoritiesConstants.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found!"));
        User newUser = User.builder()
                .username(registerAccount.getUsername().toLowerCase())
                .password(encryptedPassword)
                .fullname(registerAccount.getFullname())
                .email(registerAccount.getEmail())
                .isActive(true)
                .role(role)
                .gender(Gender.DEFAULT)
                .build();
        User savedUser = userRepository.save(newUser);
        return getUserSecurity(savedUser);

    }

    public void changeAvatarAccount(MultipartFile file, Long userId) throws IOException, URISyntaxException {
        String fileName = fileService.getNameAvatarUrl(file);
        int updated = userRepository.updateUserAvatarById(userId, fileName);
        if (updated <= 0) {
            throw new ResourceNotFoundException("User Not Found!");
        }

    }


}
