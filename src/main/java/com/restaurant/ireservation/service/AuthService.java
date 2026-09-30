package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.dto.response.ResLoginDTO;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.UserAlreadyUsedException;
import com.restaurant.ireservation.repository.CustomerRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.JwtService;
import com.restaurant.ireservation.security.SessionManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService {

    final UserRepository userRepository;
    final UserService userService;
    final CustomerService customerService;
    final CustomerRepository customerRepository;
    final SessionManager sessionManager;
    final JwtService jwtService;
    @Value("${ijres.jwt.access-token-validity-sec}")
    private Long expiresIn;


    private boolean removeNoneActivatedUser(User userChecked) {
        if (userChecked.isActive()) {
            return false;
        }
        userRepository.delete(userChecked);
        userRepository.flush();
        return true;
    }

    @Transactional
    public CustomerResponse register(RegisterAccount registerAccount) {
        userRepository.findUserByUsername(registerAccount.getUsername().toLowerCase()).ifPresent(
                user -> {
                    boolean removed = removeNoneActivatedUser(user);
                    if (!removed) {
                        throw new UserAlreadyUsedException("User Already used!");
                    }
                }

        );
        UserSecurityRes user = userService.createUser(registerAccount);
        CustomerInformation customerInformation = new CustomerInformation();
        User user1 = userRepository.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException("Not found!"));
        String sessionId = sessionManager.createSession(user1.getId());
        user1.setSessionId(sessionId);
        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin();
        userLogin.setId(user1.getId());
        userLogin.setUsername(user.getUsername());
        userLogin.setEmail(user.getEmail());
        userLogin.setRole(user.getRole());
        userLogin.setName(user.getFullname());
        ResLoginDTO resLoginDTO = new ResLoginDTO();
        resLoginDTO.setUser(userLogin);
        String accessToken = jwtService.createAccessToken(user.getUsername(), resLoginDTO, sessionId, user.getPermission());
        resLoginDTO.setAccessToken(accessToken);
        resLoginDTO.setExpiresIn(expiresIn);
        customerService.createCustomer(user1);
        CustomerResponse response = new CustomerResponse();
        response.setUser(resLoginDTO);
        return response;
    }






}
