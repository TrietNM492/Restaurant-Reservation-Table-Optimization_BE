package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.PermissionMethod;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission extends BaseEntity {

    String name;
    @Enumerated(EnumType.STRING)
    PermissionMethod permissionMethod;

    @ManyToMany(mappedBy = "permissions")
    Set<Role> roles;

    public Permission(String name, PermissionMethod method) {
        this.name = name;
        this.permissionMethod = method;
    }
}
