package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Gender;
import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.dto.request.ChangeCustomerProfileDTO;
import com.restaurant.ireservation.dto.response.CustomerResponse;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.CustomerRepository;
import com.restaurant.ireservation.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerService {

    final UserService userService;
    final UserRepository userRepository;
    final CustomerRepository customerRepository;

    @Transactional
    public CustomerInformation createCustomer(User user) {
        CustomerInformation customerInformation = CustomerInformation.builder()
                .user(user)
                .customerRank(RankCustomer.GOLDEN)
                .point(0)
                .build();
        return customerRepository.save(customerInformation);
    }


    @Transactional
    public CustomerResponse editCustomerProfile(ChangeCustomerProfileDTO profileDTO, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        user.setFullname(profileDTO.fullname());
        user.setPhone(profileDTO.phone());
        user.setGender(Gender.valueOf(profileDTO.gender().toUpperCase()));
        userRepository.save(user);
        CustomerInformation information = customerRepository.findOneByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        information.setAddress(profileDTO.address());
        information.setDateOfBirth(profileDTO.dateOfBirth());
        information.setIndentityCard(profileDTO.indentityCard());
        customerRepository.save(information);
        return toCustomerResponse(user, information);
    }

    public CustomerResponse fetchCustomerProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        CustomerInformation information = customerRepository.findOneByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User Not Found!"));
        return toCustomerResponse(user, information);
    }

    private CustomerResponse toCustomerResponse(User user, CustomerInformation information) { {
        CustomerResponse response = new CustomerResponse();
        CustomerResponse.CustomerProfile profile = new CustomerResponse.CustomerProfile();
        profile.setCustomerRank(information.getCustomerRank().toString());
        profile.setFullname(user.getFullname());
        profile.setGender(user.getGender().toString());
        profile.setPoint(information.getPoint());
        profile.setDateOfBirth(information.getDateOfBirth());
        profile.setAddress(information.getAddress());
        profile.setPoint(information.getPoint());
        profile.setPhone(user.getPhone());
        profile.setIndentityCard(information.getIndentityCard());
        profile.setAvatar(user.getAvatar());
        response.setProfile(profile);
        return response;
    }

    }

}
