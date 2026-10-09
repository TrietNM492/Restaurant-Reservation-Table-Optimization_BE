package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;




import java.util.Collection;
import java.util.Optional;

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

    @Query(value = "select i from MenuItem i join fetch i.category c left join fetch i.origin " +
            "where i.statusCode in :statuses " +
            "and (:q = '' or lower(i.itemName) like lower(concat('%', :q, '%'))) " +
            "and (:categoryId is null or c.id = :categoryId) " +
            "and (:minPrice is null or i.price >= :minPrice) " +
            "and (:maxPrice is null or i.price <= :maxPrice)",
            countQuery = "select count(i) from MenuItem i join i.category c " +
                    "where i.statusCode in :statuses " +
                    "and (:q = '' or lower(i.itemName) like lower(concat('%', :q, '%'))) " +
                    "and (:categoryId is null or c.id = :categoryId) " +
                    "and (:minPrice is null or i.price >= :minPrice) " +
                    "and (:maxPrice is null or i.price <= :maxPrice)")
    Page<MenuItem> browse(@Param("statuses") Collection<ItemStatus> statuses,
                          @Param("q") String q,
                          @Param("categoryId") Long categoryId,
                          @Param("minPrice") Double minPrice,
                          @Param("maxPrice") Double maxPrice,
                          Pageable pageable);

    @Query("select i from MenuItem i left join fetch i.category left join fetch i.origin " +
            "where i.id = :id and i.statusCode in :statuses")
    Optional<MenuItem> findVisibleById(@Param("id") Long id, @Param("statuses") Collection<ItemStatus> statuses);

}
