package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.MenuCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select c from MenuCategory c where :q = '' " +
            "or lower(c.name) like lower(concat('%', :q, '%')) " +
            "or lower(c.code) like lower(concat('%', :q, '%'))")
    Page<MenuCategory> search(@Param("q") String q, Pageable pageable);
}

