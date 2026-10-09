package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.enumerations.PermissionMethod;
import com.restaurant.ireservation.dto.request.RoleRequest;
import com.restaurant.ireservation.repository.PermissionRepository;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    RoleRepository roleRepository;

    @Mock
    PermissionRepository permissionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    RoleService roleService;

    @Test
    void createRole_resolvesPermissionIdsAndReturnsResponseDto() {
        Permission permission = new Permission("CREATE_RESERVATION", PermissionMethod.POST);
        permission.setId(8L);
        when(permissionRepository.findAllById(Set.of(8L))).thenReturn(List.of(permission));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(12L);
            return role;
        });

        var response = roleService.createRole(new RoleRequest(
                "manager", "Restaurant manager", List.of(8L)));

        assertEquals(12L, response.getId());
        assertEquals("ROLE_MANAGER", response.getName());
        assertTrue(response.getCode().startsWith("MAN-"));
        assertEquals(1, response.getPermissions().size());
        assertEquals("CREATE_RESERVATION", response.getPermissions().getFirst().getName());
        assertNotNull(response.getCreateDate());
        verify(roleRepository).save(any(Role.class));
    }
}
