package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    long countByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndItemNameIgnoreCase(Long categoryId, String itemName);

    boolean existsByCategoryIdAndItemNameIgnoreCaseAndIdNot(Long categoryId, String itemName, Long id);

    @Query(value = "select i from MenuItem i left join fetch i.category c left join fetch i.origin " +
            "where (:q = '' or lower(i.itemName) like lower(concat('%', :q, '%'))) " +
            "and (:categoryId is null or c.id = :categoryId) " +
            "and (:status is null or i.statusCode = :status)",
            countQuery = "select count(i) from MenuItem i left join i.category c " +
                    "where (:q = '' or lower(i.itemName) like lower(concat('%', :q, '%'))) " +
                    "and (:categoryId is null or c.id = :categoryId) " +
                    "and (:status is null or i.statusCode = :status)")
    Page<MenuItem> search(@Param("q") String q,
                          @Param("categoryId") Long categoryId,
                          @Param("status") ItemStatus status,
                          Pageable pageable);
}
