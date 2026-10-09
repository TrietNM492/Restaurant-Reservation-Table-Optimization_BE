package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.dto.request.RolePermissionsRequest;
import com.restaurant.ireservation.dto.request.RoleRequest;
import com.restaurant.ireservation.dto.response.PermissionResponse;
import com.restaurant.ireservation.dto.response.RoleResponse;
import com.restaurant.ireservation.error.DuplicationException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.PermissionRepository;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleService {

    final RoleRepository roleRepository;
    final PermissionRepository permissionRepository;
    final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ResultPaginationDTO fetchAllRole(String q, Pageable pageable) {
        Page<Role> page = roleRepository.findByNameContainingIgnoreCase(q == null ? "" : q.trim(), pageable);
        List<RoleResponse> response = List.of();
        if (page.hasContent()) {
            List<Long> roleIds = page.getContent().stream().map(Role::getId).toList();
            Map<Long, Role> rolesWithPermissions = roleRepository.findAllWithPermissionsByIdIn(roleIds)
                    .stream()
                    .collect(Collectors.toMap(Role::getId, Function.identity()));
            response = page.getContent().stream()
                    .map(role -> toRoleResponse(rolesWithPermissions.get(role.getId())))
                    .toList();
        }

        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        meta.setHasNext(page.hasNext());
        meta.setHasPrevious(page.hasPrevious());

        var result = new ResultPaginationDTO();
        result.setMeta(meta);
        result.setResult(response);
        return result;
    }

    @Transactional(readOnly = true)
    public RoleResponse fetchRoleById(Long id) {
        return toRoleResponse(findRoleWithPermissions(id));
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String name = normalizeName(request.getName());
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicationException("Role name already exists");
        }

        Role role = new Role();
        role.setName(name);
        role.setCode(generateCode(name));
        role.setDescription(request.getDescription());
        role.setPermissions(resolvePermissions(request.getPermissionIds()));
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = findRoleWithPermissions(id);
        String name = normalizeName(request.getName());
        if (roleRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicationException("Role name already exists");
        }

        role.setName(name);
        role.setDescription(request.getDescription());
        role.setPermissions(resolvePermissions(request.getPermissionIds()));
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRolePermissions(Long id, RolePermissionsRequest request) {
        Role role = findRoleWithPermissions(id);
        role.setPermissions(resolvePermissions(request.permissionIds()));
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (userRepository.existsByRole_Id(id)) {
            throw new RequestInvalidException("Cannot delete a role that is assigned to users");
        }
        roleRepository.delete(role);
    }

    private Role findRoleWithPermissions(Long id) {
        return roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private Set<Permission> resolvePermissions(List<Long> permissionIds) {
        Set<Long> uniqueIds = new HashSet<>(permissionIds);
        if (uniqueIds.size() != permissionIds.size()) {
            throw new RequestInvalidException("Permission IDs must not contain duplicates");
        }
        if (uniqueIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Permission> permissions = permissionRepository.findAllById(uniqueIds);
        if (permissions.size() != uniqueIds.size()) {
            Set<Long> foundIds = permissions.stream().map(Permission::getId).collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(uniqueIds);
            missingIds.removeAll(foundIds);
            throw new ResourceNotFoundException("Permissions not found: " + missingIds);
        }
        return new HashSet<>(permissions);
    }

    private String normalizeName(String name) {
        String normalizedName = name.trim().toUpperCase(Locale.ROOT);
        return normalizedName.startsWith("ROLE_") ? normalizedName : "ROLE_" + normalizedName;
    }

    private String generateCode(String name) {
        String prefix = name.replaceFirst("^ROLE_", "");
        prefix = prefix.substring(0, Math.min(prefix.length(), 3));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return prefix + "-" + suffix;
    }

    private RoleResponse toRoleResponse(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .name(permission.getName())
                        .method(permission.getPermissionMethod().name())
                        .lastModifiedDate(permission.getLastModifiedDate())
                        .build())
                .toList();
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .createDate(role.getCreatedDate())
                .lastModifiedDate(role.getLastModifiedDate())
                .build();
    }

}
