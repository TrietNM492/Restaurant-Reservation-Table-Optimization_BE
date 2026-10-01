package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.dto.request.RegisterAccount;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.dto.response.ResLoginDTO;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.error.UserAlreadyUsedException;
import com.restaurant.ireservation.repository.CustomerRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.JwtService;
import com.restaurant.ireservation.security.SessionManager;
import io.jsonwebtoken.Claims;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
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
//        CustomerInformation customerInformation = new CustomerInformation();
        User user1 = userRepository.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException("User Not found!"));
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

    @Transactional
    public ResLoginDTO generateResLoginDTO(UserSecurityRes user) {
        User userFounded = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
        var res = new ResLoginDTO();
        var userLogin = new ResLoginDTO.UserLogin();
        userLogin.setUsername(user.getUsername());
        userLogin.setId(user.getId());
        userLogin.setEmail(user.getEmail());
        userLogin.setRole(user.getRole());
        userLogin.setName(user.getFullname());
        userLogin.setAvatar(userFounded.getAvatar());
        res.setUser(userLogin);
        String sessionId = sessionManager.createSession(user.getId());

        var permissions = user.getPermission();
        permissions.add(user.getRole());
        String accessToken = jwtService.createAccessToken(user.getUsername(), res, sessionId, permissions);
        res.setAccessToken(accessToken);
        String newRefreshToken = jwtService.createRefreshToken(res.getUser());
        int updated = userRepository.updateRefreshTokenById(user.getId(), newRefreshToken);
        if (updated <= 0) {
            throw new ResourceNotFoundException("User Not Found!");
        }
        res.setExpiresIn(expiresIn);
        return res;

    }

    public CustomerResponse getCustomerInformation(ResLoginDTO user) {
        CustomerInformation customer = customerRepository.findOneByUserId(user.getUser().getId()).orElseThrow(
                () -> new UsernameNotFoundException("Can't not found customer!")
        );
        CustomerResponse response = new CustomerResponse();
        CustomerResponse.CustomerProfile profile = new CustomerResponse.CustomerProfile();
        profile.setDateOfBirth(customer.getDateOfBirth());
        profile.setCustomerRank(customer.getCustomerRank().toString());
        profile.setAddress(customer.getAddress());
        profile.setPoint(customer.getPoint());
        profile.setIndentityCard(customer.getIndentityCard());
        response.setUser(user);
        response.setProfile(profile);
        return response;
    }

    public String getRefreshTokenById(Long userId) {
        return userRepository.getRefreshTokenById(userId);
    }

    @Transactional
    public void logout(String refreshToken) {
        Claims claims = jwtService.parseRefreshTokenIgnoreExpire(refreshToken);
        var username = claims.getSubject();
        System.out.println(username);
        if (username == null) {
            throw new BadCredentialsException("User null!");
        }
        var updated = userRepository.resetRefreshTokenByUsername(username);
        if (updated <= 0) {
            throw new RequestInvalidException("User Not Found!");
        }
        sessionManager.invalidateSession(username);
    }

    @Transactional
    public ResLoginDTO processRefreshToken(String refreshToken) {
        Claims claims = jwtService.parseRefreshTokenIgnoreExpire(refreshToken);
        var username = claims.getSubject();
        if (username == null) {
            throw new BadCredentialsException("Invalid Token");
        }
        UserSecurityRes res = userService.getUserSecurityByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        String currentRefreshToken = userRepository.getRefreshTokenById(res.getId());
        if (currentRefreshToken == null || !currentRefreshToken.equals(refreshToken)) {
            throw new BadCredentialsException("Refresh token invalid or you are not logged in!");
        }
        return generateResLoginDTO(res);
    }






}
