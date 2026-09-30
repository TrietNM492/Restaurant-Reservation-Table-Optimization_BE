package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.repository.CustomerRepository;
import com.restaurant.ireservation.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerService {

    final UserService userService;
    final UserRepository userRepository;
    final CustomerRepository customerRepository;

    public CustomerInformation createCustomer(User user) {
        CustomerInformation customerInformation = CustomerInformation.builder()
                .user(user)
                .customerRank(RankCustomer.GOLDEN)
                .build();
        return customerRepository.save(customerInformation);
    }

}
