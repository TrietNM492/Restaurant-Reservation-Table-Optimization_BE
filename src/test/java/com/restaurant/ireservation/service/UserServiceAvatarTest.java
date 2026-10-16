package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceAvatarTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    RoleRepository roleRepository;

    @Mock
    JwtService jwtService;

    @Mock
    FileService fileService;

    @Mock
    CloudinaryService cloudinaryService;

    @InjectMocks
    UserService userService;

    private Long userId;
    private MockMultipartFile mockFile;
    private User testUser;
    private String newAvatarUrl;
    private String oldAvatarUrl;

    @BeforeEach
    void setUp() {
        userId = 42L;
        mockFile = new MockMultipartFile("avatarUrl", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        oldAvatarUrl = "https://res.cloudinary.com/test-cloud/image/upload/v1/restaurant/user-avatars/old-avatar.png";
        newAvatarUrl = "https://res.cloudinary.com/test-cloud/image/upload/v2/restaurant/user-avatars/avatar-42-new.jpg";

        testUser = User.builder()
                .username("customer1")
                .avatar(oldAvatarUrl)
                .build();
        testUser.setId(userId);
    }

    @Test
    @DisplayName("changeAvatarAccount updates DB and cleans up old Cloudinary avatar on success")
    void changeAvatar_success_updatesDbAndCleansUpOldAvatar() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileService.getNameAvatarUrl(mockFile, userId)).thenReturn(newAvatarUrl);
        when(userRepository.updateUserAvatarById(userId, newAvatarUrl)).thenReturn(1);

        String result = userService.changeAvatarAccount(mockFile, userId);

        assertEquals(newAvatarUrl, result);
        verify(userRepository).updateUserAvatarById(userId, newAvatarUrl);
        verify(cloudinaryService).deleteAsset(oldAvatarUrl);
        verify(cloudinaryService, never()).deleteAsset(newAvatarUrl);
    }

    @Test
    @DisplayName("changeAvatarAccount does not delete old avatar if user had no previous avatar")
    void changeAvatar_success_nullOldAvatar_skipsOldAvatarDeletion() throws Exception {
        testUser.setAvatar(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileService.getNameAvatarUrl(mockFile, userId)).thenReturn(newAvatarUrl);
        when(userRepository.updateUserAvatarById(userId, newAvatarUrl)).thenReturn(1);

        String result = userService.changeAvatarAccount(mockFile, userId);

        assertEquals(newAvatarUrl, result);
        verify(cloudinaryService, never()).deleteAsset(any());
    }

    @Test
    @DisplayName("changeAvatarAccount throws ResourceNotFoundException if user does not exist before upload")
    void changeAvatar_userNotFound_throwsBeforeUpload() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.changeAvatarAccount(mockFile, userId));
        verifyNoInteractions(fileService);
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("changeAvatarAccount triggers compensating deletion and throws ResourceNotFoundException on zero-row DB update")
    void changeAvatar_zeroRowUpdate_triggersCompensatingDeletion() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileService.getNameAvatarUrl(mockFile, userId)).thenReturn(newAvatarUrl);
        when(userRepository.updateUserAvatarById(userId, newAvatarUrl)).thenReturn(0);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> userService.changeAvatarAccount(mockFile, userId));
        assertEquals("User Not Found!", ex.getMessage());

        verify(cloudinaryService).deleteAsset(newAvatarUrl);
        verify(cloudinaryService, never()).deleteAsset(oldAvatarUrl);
    }

    @Test
    @DisplayName("changeAvatarAccount triggers compensating deletion and rethrows on database exception")
    void changeAvatar_dbException_triggersCompensatingDeletionAndRethrows() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileService.getNameAvatarUrl(mockFile, userId)).thenReturn(newAvatarUrl);
        when(userRepository.updateUserAvatarById(userId, newAvatarUrl))
                .thenThrow(new RuntimeException("Database connection lost"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changeAvatarAccount(mockFile, userId));
        assertEquals("Database connection lost", ex.getMessage());

        verify(cloudinaryService).deleteAsset(newAvatarUrl);
        verify(cloudinaryService, never()).deleteAsset(oldAvatarUrl);
    }

    @Test
    @DisplayName("changeAvatarAccount preserves database exception even if compensating deletion fails")
    void changeAvatar_compensatingDeletionFails_doesNotMaskDatabaseException() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(fileService.getNameAvatarUrl(mockFile, userId)).thenReturn(newAvatarUrl);
        when(userRepository.updateUserAvatarById(userId, newAvatarUrl))
                .thenThrow(new RuntimeException("Primary DB failure"));
        doThrow(new RuntimeException("Cloudinary deletion failed")).when(cloudinaryService).deleteAsset(newAvatarUrl);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changeAvatarAccount(mockFile, userId));
        assertEquals("Primary DB failure", ex.getMessage());
    }
}
