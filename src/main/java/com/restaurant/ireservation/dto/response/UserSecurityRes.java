package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.entity.Permission;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityRes {

    Long id;
    String username;
    String email;
    String gender;
    String fullname;
    String role;
    Set<String> permission;
    String sessionId;
    boolean isActive;

}
