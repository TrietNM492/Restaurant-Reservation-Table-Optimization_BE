package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findOneByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from Role r where upper(r.name) in :names")
    List<Role> findAllByUpperNameIn(@Param("names") Collection<String> names);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Page<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from Role r where r.id = :id")
    Optional<Role> findWithPermissionsById(@Param("id") Long id);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from Role r where r.id in :ids")
    List<Role> findAllWithPermissionsByIdIn(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = "permissions")
    @Query("select distinct r from Role r join r.permissions p where p.id = :permissionId")
    List<Role> findAllWithPermissionsByPermissionId(@Param("permissionId") Long permissionId);
}
