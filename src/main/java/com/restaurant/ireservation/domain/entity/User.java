package com.restaurant.ireservation.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.util.constants.Constants;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity {

    @Column(unique = true)
    @Pattern(regexp = Constants.LOGIN_REGEX, message = "Username must pla")
    String username;

    @Column(name = "password_hash")
    String password;

    @Email
    @Column(unique = true)
    String email;

    @Size(min = 2, max = 200, message = "Fullname pla")
    String fullname;

    @Enumerated(EnumType.STRING)
    Gender gender;

    boolean isActive = true;

    String sessionId;

    String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    Role role;

    @Column(columnDefinition = "LONGTEXT")
    @JsonIgnore
    String refreshToken;


}
