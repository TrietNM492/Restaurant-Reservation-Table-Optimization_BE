package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleResponse {

    Long id;
    String code;
    String name;
    String description;
    Instant createDate;
    Instant lastModifiedDate;
    List<PermissionResponse> permissions;
}
