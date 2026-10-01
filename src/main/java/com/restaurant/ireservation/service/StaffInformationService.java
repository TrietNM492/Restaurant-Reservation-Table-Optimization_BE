package com.restaurant.ireservation.service;

import com.restaurant.ireservation.dto.response.StaffInformationDTO;
import com.restaurant.ireservation.dto.response.UserSecurityRes;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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

    public ResultPaginationDTO fetchStaff(Pageable pageable) {
        var listStaff = staffInformationRepository.getStaffsAndAccountInformation(pageable);
        List<StaffInformationDTO> staffInformationDTOS = listStaff.stream().map(staff -> {
            StaffInformationDTO dto = new StaffInformationDTO();
            dto.setSalary(staff.getSalary());
            dto.setTenure(staff.tenureStaff());
            dto.setDepartment(staff.getDepartment());
            dto.setJoinDate(staff.getJoinDate());
            dto.setResignationDate(staff.getResignationDate());
            var userRes = new StaffInformationDTO.InformationStaff();
            userRes.setPhone(staff.getUser().getPhone());
            userRes.setGender(dto.getUser().getGender());
            userRes.setFullname(dto.getUser().getFullname());
            userRes.setEmail(dto.getUser().getEmail());
            dto.setUser(userRes);
            return dto;
        }).toList();

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


}
