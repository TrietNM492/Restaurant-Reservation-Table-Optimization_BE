package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.enumerations.PermissionMethod;
import com.restaurant.ireservation.dto.request.PermissionRequest;
import com.restaurant.ireservation.error.DuplicationException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.PermissionRepository;
import com.restaurant.ireservation.repository.RoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionService {
    final PermissionRepository permissionRepository;
    final RoleRepository roleRepository;

    public Permission createPermission(PermissionRequest permission) {
        if (permissionRepository.existsByName(permission.name())) {
            throw new DuplicationException("This permission exist");
        }
        Permission permissionSaved = new Permission();
        permissionSaved.setPermissionMethod(PermissionMethod.valueOf(permission.method().toUpperCase()));
        permissionSaved.setName(permission.name());
        Set<Role> roles = permission.rolesName().stream().map(this::toRole).collect(Collectors.toSet());
        permissionSaved.setRoles(roles);

        return permissionRepository.save(permissionSaved);
    }

    private Role toRole(String roleString) {
        Role role = roleRepository.findOneByName(roleString)
                .orElseThrow(() -> new ResourceNotFoundException("Not Found Role!"));
        return role;
    }



}
