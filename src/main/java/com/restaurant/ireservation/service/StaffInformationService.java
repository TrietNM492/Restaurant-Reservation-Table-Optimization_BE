package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.StaffInformation;
import com.restaurant.ireservation.dto.response.StaffInformationDTO;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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


}
