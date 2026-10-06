package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.Gender;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UserResonseDTO {

    Long id;
    String avatar;

    String username;

    String email;

    String fullname;
    String gender;
    String phone;
}
