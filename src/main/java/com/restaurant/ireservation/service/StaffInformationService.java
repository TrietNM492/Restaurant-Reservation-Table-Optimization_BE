package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.StaffInformation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.Department;
import com.restaurant.ireservation.dto.request.ChangeUserStatus;
import com.restaurant.ireservation.dto.request.StaffInformationRequest;
import com.restaurant.ireservation.dto.request.UpdateUserStaffRequest;
import com.restaurant.ireservation.dto.response.StaffInformationDTO;
import com.restaurant.ireservation.dto.response.StaffInformationResponse;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class StaffInformationService {

    final StaffInformationRepository staffInformationRepository;
    final UserRepository userRepository;
    final UserService userService;

    public ResultPaginationDTO fetchStaff(String q, Pageable pageable) {
        var listStaff = staffInformationRepository.getStaffsAndAccountInformation(pageable, q);
        List<StaffInformationDTO> staffInformationDTOS = toListStaffs(listStaff);
//        List<StaffInformation> staffInformationDTOS = listStaff.getContent();
        var res = new ResultPaginationDTO();
        res.setResult(staffInformationDTOS);
        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(listStaff.getTotalPages());
        meta.setTotal(listStaff.getTotalElements());
        meta.setHasNext(listStaff.hasNext());
        meta.setHasPrevious(listStaff.hasPrevious());
        res.setMeta(meta);
        return res;
    }

    private List<StaffInformationDTO> toListStaffs(Page<StaffInformation> listStaff) {
        List<StaffInformationDTO> staffInformationDTOS = listStaff.stream().map(staff -> {
            StaffInformationDTO dto = new StaffInformationDTO();
            dto.setSalary(staff.getSalary());
            dto.setTenure(staff.tenureStaff());
            dto.setDepartment(staff.getDepartment());
            dto.setJoinDate(staff.getJoinDate());
            dto.setResignationDate(staff.getResignationDate());
            dto.setEmployeeCode(staff.getEmployeeCode());
            var userRes = new StaffInformationDTO.InformationStaff();
            userRes.setPhone(staff.getUser().getPhone());
            userRes.setGender(staff.getUser().getGender().toString());
            userRes.setFullname(staff.getUser().getFullname());
            userRes.setEmail(staff.getUser().getEmail());
            userRes.setActive(staff.getUser().isActive());
            dto.setUser(userRes);
            return dto;
        }).toList();
        return staffInformationDTOS;
    }

    private String removeVietnameseAccent(String text) {
        if (text == null) {
            return null;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d")
                .replace("Đ", "D");
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        return text.substring(0, 1).toUpperCase()
                + text.substring(1).toLowerCase();
    }

    private String generateBaseEmployeeCode(String fullname) {
        if (fullname == null || fullname.isBlank()) {
            throw new IllegalArgumentException("Fullname is required!");
        }
        String[] parts = fullname.trim().split("\\s+");
        String lastName = removeVietnameseAccent(parts[parts.length - 1]);
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            initials.append(removeVietnameseAccent(parts[i]).substring(0, 1).toUpperCase());
        }

        return capitalize(lastName) + initials;
    }

    public String generateUniqueEmployeeCode(String fullname) {
        String baseCode = generateBaseEmployeeCode(fullname);
        List<String> codes = staffInformationRepository.findStaffInformationByPrefix(baseCode);
        if (codes.isEmpty()) {
            return baseCode;
        }


        int max = 0;
        for (String code : codes) {
            if (code.equals(baseCode)) {
                max = Math.max(max, 0);
                continue;
            }
            String suffix = code.substring(baseCode.length());
            if (suffix.matches("\\d+")) {
                max = Math.max(max, Integer.parseInt(suffix));
            }
        }
        return baseCode + (max + 1);
    }

    @Transactional
    public StaffInformationResponse createStaff(StaffInformationRequest request) {
        User newStaff = userService.createStaff(request);
        StaffInformation information = new StaffInformation();
        information.setUser(newStaff);
        information.setJoinDate(LocalDate.now());
        information.setDepartment(Department.valueOf(request.getDepartment().toUpperCase()));
        information.setEmployeeCode(generateUniqueEmployeeCode(newStaff.getFullname()));
        information.setSalary(information.baseSalary());
        staffInformationRepository.save(information);
        return toStaffInformationResponse(information, newStaff);
    }

    private StaffInformationResponse toStaffInformationResponse(StaffInformation request, User staffUser) {
        StaffInformationResponse response = new StaffInformationResponse();
        response.setDepartment(request.getDepartment().toString());
        response.setEmail(staffUser.getEmail());
        response.setFullname(staffUser.getFullname());
        response.setGender(staffUser.getGender().toString());
        response.setUsername(staffUser.getUsername());
        response.setJoinDate(request.getJoinDate());
        response.setSalary(request.getSalary());
        response.setPhone(staffUser.getPhone());
        response.setEmployeeCode(request.getEmployeeCode());
        response.setResignationDate(request.getResignationDate());
        response.setId(staffUser.getId());
        response.setAvatar(staffUser.getAvatar());
        return response;

    }

    public StaffInformationResponse fetchStaffById(Long staffId) {
        StaffInformation staffInformation = staffInformationRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff Not Found!"));
        User userStaff = userRepository.findById(staffInformation.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bad Logic"));
        return toStaffInformationResponse(staffInformation, userStaff);
    }

    @Transactional
    public StaffInformationResponse updateStaff(UpdateUserStaffRequest request) {
        StaffInformation staffInformation =staffInformationRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff Not Found!"));

        User userStaff = userService.updateStaff(request, staffInformation);
        staffInformation.setDepartment(Department.valueOf(request.getDepartment().toUpperCase()));
        return toStaffInformationResponse(staffInformation, userStaff);

    }

    @Transactional
    public ChangeUserStatus toggleChangeStaffStatus(ChangeUserStatus request) {
        StaffInformation staffInformation = staffInformationRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff Not Found!"));
        User staffUser = userRepository.findById(staffInformation.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bad Logic"));
        userService.toggleActionActivated(staffUser.getId());

        ChangeUserStatus status = new ChangeUserStatus();
        status.setId(request.getId());
        status.setReason(request.getReason());
        status.setNotifyUser(request.isNotifyUser());
        return status;

    }


}
