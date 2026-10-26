package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;

/**
 * Module: Table Area Management [6] & Table Management [3]
 */
@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
    boolean existsByAreaId(Long areaId);

    Optional<RestaurantTable> findByTableNumber(Integer tableNumber);

    boolean existsByTableNumber(Integer tableNumber);

    boolean existsByTableNumberAndIdNot(Integer tableNumber, Long id);

    @Query(value = "SELECT t FROM RestaurantTable t LEFT JOIN FETCH t.area a WHERE " +
            "(:areaId IS NULL OR (a IS NOT NULL AND a.id = :areaId)) AND " +
            "(:statusCode IS NULL OR t.statusCode = :statusCode) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "CAST(t.tableNumber AS string) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "(a IS NOT NULL AND (LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(CAST(a.name AS string)) LIKE LOWER(CONCAT('%', :q, '%')))))",
            countQuery = "SELECT COUNT(t) FROM RestaurantTable t LEFT JOIN t.area a WHERE " +
            "(:areaId IS NULL OR (a IS NOT NULL AND a.id = :areaId)) AND " +
            "(:statusCode IS NULL OR t.statusCode = :statusCode) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "CAST(t.tableNumber AS string) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "(a IS NOT NULL AND (LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(CAST(a.name AS string)) LIKE LOWER(CONCAT('%', :q, '%')))))")
    Page<RestaurantTable> searchTables(
            @Param("q") String q,
            @Param("areaId") Long areaId,
            @Param("statusCode") TableStatus statusCode,
            Pageable pageable
    );

    long countByStatusCode(TableStatus statusCode);

    java.util.List<RestaurantTable> findByAreaId(Long areaId);

    java.util.List<RestaurantTable> findByStatusCode(TableStatus statusCode);

    java.util.List<RestaurantTable> findAllByOrderByTableNumberAsc();

    java.util.List<RestaurantTable> findByAreaIdOrderByTableNumberAsc(Long areaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RestaurantTable t WHERE t.id IN :ids ORDER BY t.id")
    List<RestaurantTable> findAllByIdForAllocation(@Param("ids") Collection<Long> ids);
}
