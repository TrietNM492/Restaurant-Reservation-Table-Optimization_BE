package com.restaurant.ireservation.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerResponse {

    ResLoginDTO user;
    CustomerProfile profile;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerProfile {
        String avatar;
        LocalDate dateOfBirth;
        String indentityCard;
        String address;

        Integer point;

        String customerRank;

    }


}
