package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.util.constants.Constants;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Entity
@Table(name = "customer_information")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class CustomerInformation {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    User user;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Column(name = "indentity_card")
    String indentityCard;

    @Column(length = 500)
    String address;

    Integer point = 0;

    @Enumerated(EnumType.STRING)
    RankCustomer customerRank;


}
