package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.service.MailService;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.service.UserService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HelloController {

    final UserService userService;
    final StaffInformationService staffInformationService;

    final MailService mailService;

    @GetMapping("/hello")
    public ResponseEntity<UserSecurityRes> hello() {
        var user = userService.getUserSecurityByUsername("CasCas")
                .orElseThrow(() -> new ResourceNotFoundException("Not Found"));

        return ResponseEntity.ok(user);
    }

    @GetMapping("/staffs")
    public ResponseEntity<ResultPaginationDTO> fetchStaff(Pageable pageable,@RequestParam(name = "q", required = false) String q) {
        var staffList = staffInformationService.fetchStaff(q, pageable);
        return ResponseEntity.ok(staffList);
    }

    @PostMapping("/test-reservation")
    public String testReservationEmail(
            @RequestParam String to
    ) throws MessagingException, UnsupportedEncodingException {

        mailService.sendReservationConfirmation(
                to,
                "Nguyen Minh Triet",
                "RES001",
                "19:00, 20/10/2026",
                4
        );

        return "Email sent successfully";
    }


}
