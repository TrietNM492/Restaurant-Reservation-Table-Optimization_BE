package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.enumerations.PermissionMethod;
import com.restaurant.ireservation.dto.request.PermissionRequest;
import com.restaurant.ireservation.error.DuplicationException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.PermissionRepository;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionService {
    final PermissionRepository permissionRepository;
    final RoleRepository roleRepository;

    @Transactional
    public Permission createPermission(PermissionRequest permission) {
        if (permissionRepository.existsByName(permission.name())) {
            throw new DuplicationException("This permission exist");
        }
        Permission permissionSaved = new Permission();
        permissionSaved.setPermissionMethod(PermissionMethod.valueOf(permission.method().toUpperCase()));
        permissionSaved.setName(permission.name());
        Permission savedPermission = permissionRepository.save(permissionSaved);
        Set<Role> roles = toRoles(permission.rolesName());
        roles.forEach(role -> role.getPermissions().add(savedPermission));
        roleRepository.saveAll(roles);
        savedPermission.setRoles(roles);
        return savedPermission;
    }

    private Set<Role> toRoles(Collection<String> roleNames) {
        Set<String> uniqueNames = roleNames.stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Role> roles = roleRepository.findAllByUpperNameIn(uniqueNames);
        if (roles.size() != uniqueNames.size()) {
            Set<String> foundNames = roles.stream().map(Role::getName)
                    .collect(Collectors.toSet());
            Set<String> missingNames = uniqueNames.stream()
                    .filter(name -> foundNames.stream().noneMatch(found -> found.equalsIgnoreCase(name)))
                    .collect(Collectors.toSet());
            throw new ResourceNotFoundException("Roles not found: " + missingNames);
        }
        return roles.stream().collect(Collectors.toSet());
    }

    @Transactional
    public Permission updatePermission(PermissionRequest permissionRequest) {
        if (permissionRepository.existsByNameAndIdNot(permissionRequest.name(), permissionRequest.id())) {
            throw new DuplicationException("This permission exist");
        }
        Permission permissionFounded = permissionRepository.findById(permissionRequest.id())
                .orElseThrow(() -> new ResourceNotFoundException("Id not exist"));
        permissionFounded.setName(permissionRequest.name());
        permissionFounded.setPermissionMethod(PermissionMethod.valueOf(permissionRequest.method().toUpperCase()));
        List<Role> currentRoles = roleRepository.findAllWithPermissionsByPermissionId(permissionFounded.getId());
        Set<Role> requestedRoles = toRoles(permissionRequest.rolesName());
        Set<Long> requestedRoleIds = requestedRoles.stream().map(Role::getId).collect(Collectors.toSet());
        currentRoles.stream()
                .filter(role -> !requestedRoleIds.contains(role.getId()))
                .forEach(role -> role.getPermissions().remove(permissionFounded));
        requestedRoles.forEach(role -> role.getPermissions().add(permissionFounded));
        currentRoles.addAll(requestedRoles);
        roleRepository.saveAll(currentRoles);
        permissionFounded.setRoles(requestedRoles);
        return permissionRepository.save(permissionFounded);
    }

    public ResultPaginationDTO fetchAllPermission(String q, Pageable pageable) {
        var res = new ResultPaginationDTO();
        Page<Permission> permissionPage = permissionRepository.fetchListPermissionByName(q, pageable);
        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(permissionPage.getTotalPages());
        meta.setTotal(permissionPage.getTotalElements());
        meta.setHasNext(permissionPage.hasNext());
        meta.setHasPrevious(permissionPage.hasPrevious());

        Set<Permission> permissions = toPermissions(permissionPage);
        res.setResult(permissions);
        res.setMeta(meta);
        return res;
    }

    private Set<Permission> toPermissions(Page<Permission> permissions) {
        Set<Permission> permissionSet = permissions.stream().map(per -> {
            Permission permission = new Permission();
            permission.setId(per.getId());
            permission.setPermissionMethod(per.getPermissionMethod());
            permission.setRoles(per.getRoles());
            return permission;
        }).collect(Collectors.toSet());
        return permissionSet;
    }

    @Transactional
    public String deletePermission(Long permissionId) {
        if (permissionId == null) {
            throw new IllegalArgumentException("Id is null");
        }
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Not Found Permission!"));
        List<Role> roles = roleRepository.findAllWithPermissionsByPermissionId(permissionId);
        roles.forEach(role -> role.getPermissions().remove(permission));
        roleRepository.saveAllAndFlush(roles);
        permissionRepository.delete(permission);
        return String.format("Delete Permission with id = %d", permissionId);
    }


}
