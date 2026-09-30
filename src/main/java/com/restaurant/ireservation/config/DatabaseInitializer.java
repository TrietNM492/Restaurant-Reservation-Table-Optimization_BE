package com.restaurant.ireservation.config;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import com.restaurant.ireservation.domain.entity.StaffInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Department;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.domain.enumerations.PermissionMethod;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.PermissionRepository;
import com.restaurant.ireservation.repository.RoleRepository;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    final UserRepository userRepository;
    final StaffInformationRepository staffProfileRepository;
    final PermissionRepository permissionRepository;
    final RoleRepository roleRepository;
    final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> START INIT DATABASE <<<");
        Long permissionCnt = this.permissionRepository.count();
        Long userCnt = this.userRepository.count();
        Long roleCnt = this.roleRepository.count();

        if (permissionCnt == 0) {
            Set<Permission> permissionList = new HashSet<>();
            permissionList.add(new Permission("CREATE_PERMISSION", PermissionMethod.POST));
            permissionList.add(
                    new Permission("UPDATE_PERMISSION",
                            PermissionMethod.PUT));
            permissionList
                    .add(new Permission("DELETE_PERMISSION", PermissionMethod.DELETE));
            permissionList.add(
                    new Permission("GET_PERMISSION",
                            PermissionMethod.GET));
            permissionList.add(new Permission("GET_ALL_PERMISSION",
                    PermissionMethod.GET));

            permissionList.add(
                    new Permission("CREATE_ROLE",
                            PermissionMethod.POST));
            permissionList.add(
                    new Permission("UPDATE_ROLE",
                            PermissionMethod.PUT));
        }

        if (roleCnt == 0) {
            var permissions = new HashSet<>(this.permissionRepository.findAll());
            var roleAdmin = new Role();
            var roles = new ArrayList<Role>();
            roleAdmin.setName(AuthoritiesConstants.ADMIN);
            roleAdmin.setDescription("SUPER ADMIN HAS FULL PERMISSIONS");
            roleAdmin.setPermissions(permissions);
            roleAdmin.setCreatedBy("SYSTEM");
            var roleUser = new Role();
            roleUser.setName(AuthoritiesConstants.CUSTOMER);
            roleUser.setDescription("CUSTOMER USE APP");
            roleUser.setPermissions(permissions);
            roleUser.setCreatedBy("SYSTEM");
            roles.add(roleUser);
            roles.add(roleAdmin);
            this.roleRepository.saveAll(roles);
        }
        if (userCnt == 0) {
            User admin = new User();
            admin.setFullname("Castorice");
            admin.setActive(true);
            admin.setGender(Gender.MALE);
            admin.setPhone("0912312312");
            admin.setEmail("CasCake@gmail.com");
            var roleAdmin = this.roleRepository.findOneByName(AuthoritiesConstants.ADMIN)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found!"));
            admin.setRole(roleAdmin);
            admin.setPassword(this.passwordEncoder.encode("123123"));
            admin.setUsername("cascas");
            admin.setCreatedBy("SYSTEM");
            userRepository.save(admin);
            StaffInformation information = new StaffInformation();
            information.setUser(admin);
            information.setEmployeeCode("CasCom");
            information.setDepartment(Department.MANAGER);
            information.setJoinDate(LocalDate.now());
            information.setResignationDate(null);
            information.setSalary(33550336);
            staffProfileRepository.save(information);
        }
        if (permissionCnt != 0 && roleCnt != 0 && userCnt != 0) {
            System.out.println(">>> SKIP PROCESSING INITIALIZE <<<");
        } else {
            System.out.println(">>> INIT DATABASE SUCCESSFULLY");
        }
    }
}

