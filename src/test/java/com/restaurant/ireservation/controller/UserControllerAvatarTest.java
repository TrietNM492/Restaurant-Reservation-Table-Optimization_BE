package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.error.FileUploadException;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.service.CustomerService;
import com.restaurant.ireservation.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerAvatarTest {

    @Mock
    UserService userService;

    @Mock
    UserRepository userRepository;

    @Mock
    CustomerService customerService;

    @InjectMocks
    UserController userController;

    private MockMvc mockMvc;
    private Jwt mockJwt;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockJwt = mock(Jwt.class);
        lenient().when(mockJwt.getSubject()).thenReturn("testuser");

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockJwt;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalException())
                .setCustomArgumentResolvers(jwtResolver)
                .build();

        mockUser = User.builder()
                .username("testuser")
                .build();
        mockUser.setId(10L);
    }

    @Test
    @DisplayName("changeAvatar returns HTTP 200 with plain-text newAvatar prefix on success")
    void changeAvatar_success_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.png", "image/png", new byte[]{1, 2, 3});
        String cloudUrl = "https://res.cloudinary.com/test-cloud/image/upload/v1/restaurant/user-avatars/avatar-10-abc.png";

        when(userRepository.findUserByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(userService.changeAvatarAccount(any(), eq(10L))).thenReturn(cloudUrl);

        mockMvc.perform(multipart("/api/v1/users/change-avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("newAvatar: " + cloudUrl));

        verify(userService).changeAvatarAccount(any(), eq(10L));
    }

    @Test
    @DisplayName("changeAvatar returns HTTP 404 when user is not found")
    void changeAvatar_userNotFound_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.png", "image/png", new byte[]{1, 2, 3});

        when(userRepository.findUserByUsername("testuser")).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/v1/users/change-avatar").file(file))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("changeAvatar returns HTTP 400 when file validation fails")
    void changeAvatar_invalidFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "bad.txt", "text/plain", new byte[]{1, 2, 3});

        when(userRepository.findUserByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(userService.changeAvatarAccount(any(), eq(10L)))
                .thenThrow(new RequestInvalidException("Unsupported image content type: text/plain"));

        mockMvc.perform(multipart("/api/v1/users/change-avatar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unsupported image content type: text/plain"));
    }

    @Test
    @DisplayName("changeAvatar returns HTTP 500 when Cloudinary upload fails")
    void changeAvatar_uploadException_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(userRepository.findUserByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(userService.changeAvatarAccount(any(), eq(10L)))
                .thenThrow(new FileUploadException("Cloudinary storage is not configured. Missing required credentials."));

        mockMvc.perform(multipart("/api/v1/users/change-avatar").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Cloudinary storage is not configured. Missing required credentials."));
    }
}
