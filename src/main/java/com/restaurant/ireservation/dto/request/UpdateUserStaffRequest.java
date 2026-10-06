package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStaffRequest {
    Long id;
    @Email(message = "Must valid with email!!!!")
    String email;
    @NotBlank(message = "Fullname cant be blank")
    String fullname;
    String gender;
    String phone;
    @NotBlank(message = "Must choose a role")
    String role;
    String department;
}
