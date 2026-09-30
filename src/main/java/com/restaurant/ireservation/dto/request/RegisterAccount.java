package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
public class RegisterAccount {

    @NotBlank(message = "Username can't be empty!")
    String username;

    @Email(message = "Email in format!")
    String email;

    @NotBlank(message = "Name not blank")
    String fullname;

    String phone;

    @NotBlank(message = "Password can't be empty!")
    @Size(min = 8, max = 16, message = "Password size must in 8 - 16")
    String password;

    @NotBlank(message = "Password can't be empty!")
    @Size(min = 8, max = 16, message = "Password size must in 8 - 16")
    String passwordAgain;

}
