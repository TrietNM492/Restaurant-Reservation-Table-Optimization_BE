package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Permission;
import com.restaurant.ireservation.domain.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Role> getRolesByNameIgnoreCase(String name, Pageable pageable);

    @Query(value = "select p from Permission p where p.name like concat('%', :name, '%')")
    Page<Permission> fetchListPermissionByName(@Param("name") String name, Pageable pageable);

    Optional<Permission> findByNameIgnoreCase(String name);

}
