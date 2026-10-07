package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.entity.StaffInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.request.StaffInformationRequest;
import com.restaurant.ireservation.dto.request.UpdateUserStaffRequest;
import com.restaurant.ireservation.dto.response.StaffInformationResponse;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.error.UserAlreadyUsedException;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import com.restaurant.ireservation.security.JwtService;
import com.restaurant.ireservation.security.PasswordGenerator;
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

    public String changeAvatarAccount(MultipartFile file, Long userId) throws IOException, URISyntaxException {
        String fileName = fileService.getNameAvatarUrl(file);
        int updated = userRepository.updateUserAvatarById(userId, fileName);
        if (updated <= 0) {
            throw new ResourceNotFoundException("User Not Found!");
        }
        return fileName;

    }

    public String toggleActionActivated(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        user.setActive(!user.isActive());
        return String.format("User with username: %s - active status: %s", user.getUsername(), user.isActive());
    }

    public User createStaff(StaffInformationRequest request) {
        Role role = roleRepository.findOneByName(request.getRole().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Not found this role!"));
        String tempPassword = PasswordGenerator.generate(12);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyUsedException("Email already exist!");
        }
        if (userRepository.findUserByUsername(request.getUsername()).isPresent()) {
            throw new UserAlreadyUsedException("Username already exist!");
        }
        User newStaff = User.builder()
                .fullname(request.getFullname())
                .username(request.getUsername().toLowerCase())
                .email(request.getEmail().toLowerCase())
                .gender(Gender.valueOf(request.getGender().toUpperCase()))
                .role(role)
                .phone(request.getPhone())
                .password(passwordEncoder.encode(tempPassword))
                .isActive(true).build();
        return userRepository.save(newStaff);

    }

    public User updateStaff(UpdateUserStaffRequest request, StaffInformation staffInformation) {
        User userStaff = userRepository.findById(staffInformation.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bad Logic"));
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), request.getId())) {
            throw new UserAlreadyUsedException("Email is Exist!");
        }
        Role role = roleRepository.findOneByName(request.getRole().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Not found this role!"));

        userStaff.setEmail(request.getEmail());
        userStaff.setFullname(request.getFullname());
        userStaff.setGender(Gender.valueOf(request.getGender().toUpperCase()));
        userStaff.setPhone(request.getPhone());
        userStaff.setRole(role);
        return userRepository.save(userStaff);

    }

}
