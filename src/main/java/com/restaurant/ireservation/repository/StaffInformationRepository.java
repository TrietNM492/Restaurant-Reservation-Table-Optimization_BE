package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.StaffInformation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffInformationRepository extends JpaRepository<StaffInformation, Long> {

    Optional<StaffInformation> findByUserId(Long userId);

    @Query(value = "select s from StaffInformation s left join fetch s.user u left join fetch u.role r left join fetch r.permissions p where u.fullname like concat('%', :q, '%') and u.isActive = true",
    countQuery = "select count(s) from StaffInformation s left join s.user u where u.fullname like concat('%', :q, '%') and u.isActive = true")
    Page<StaffInformation> getStaffsAndAccountInformation(Pageable pageable, @Param("q") String q);

    @Query(value = "select s.employeeCode from StaffInformation s where s.employeeCode like concat(:baseCode, '%')")
    List<String> findStaffInformationByPrefix(String baseCode);
}
