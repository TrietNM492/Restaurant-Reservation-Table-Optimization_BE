package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.TableArea;
import com.restaurant.ireservation.domain.enumerations.AreaName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Module: Table Area Management [6]
 * Thực hiện lúc: 2026-10-08 13:50:00 (GMT+7)
 */
@Repository
public interface TableAreaRepository extends JpaRepository<TableArea, Long> {

    Optional<TableArea> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query(value = "SELECT a FROM TableArea a WHERE " +
            "(:name IS NULL OR a.name = :name) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "(:searchAreaName IS NOT NULL AND a.name = :searchAreaName))",
            countQuery = "SELECT COUNT(a) FROM TableArea a WHERE " +
            "(:name IS NULL OR a.name = :name) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "(:searchAreaName IS NOT NULL AND a.name = :searchAreaName))")
    Page<TableArea> searchTableAreas(
            @Param("q") String q,
            @Param("name") AreaName name,
            @Param("searchAreaName") AreaName searchAreaName,
            Pageable pageable
    );
}
