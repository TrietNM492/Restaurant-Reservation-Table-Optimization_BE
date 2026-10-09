package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.FoodOrigin;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.dto.response.MenuCategoryPublicResponse;
import com.restaurant.ireservation.dto.response.MenuItemPublicResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.MenuCategoryRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MenuBrowsingService {

    /** Clients see available and sold-out items; archived items are hidden. */
    static final List<ItemStatus> VISIBLE = List.of(ItemStatus.AVAILABLE, ItemStatus.OUT_OF_STOCK);
    static final int MAX_PAGE_SIZE = 50;

    final MenuItemRepository menuItemRepository;
    final MenuCategoryRepository menuCategoryRepository;

    @Transactional(readOnly = true)
    public MenuItemPublicResponse fetchById(Long id) {
        MenuItem item = menuItemRepository.findVisibleById(id, VISIBLE)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found!"));
        return toResponse(item);
    }

    private Sort toSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("name")) {
            return Sort.by("itemName").ascending().and(Sort.by("id"));
        }
        return switch (sort.trim().toLowerCase()) {
            case "price_asc" -> Sort.by("price").ascending().and(Sort.by("id"));
            case "price_desc" -> Sort.by("price").descending().and(Sort.by("id"));
            case "newest" -> Sort.by("id").descending();
            default -> throw new RequestInvalidException(
                    "Invalid sort '" + sort + "'. Allowed: name, price_asc, price_desc, newest");
        };
    }

    private MenuItemPublicResponse toResponse(MenuItem i) {
        FoodOrigin o = i.getOrigin();
        return MenuItemPublicResponse.builder()
                .id(i.getId())
                .categoryId(i.getCategory() == null ? null : i.getCategory().getId())
                .categoryName(i.getCategory() == null ? null : i.getCategory().getName())
                .itemName(i.getItemName())
                .description(i.getDescription())
                .price(i.getPrice())
                .itemPicture(i.getItemPicture())
                .estimatedPrepMinutes(i.getEstimatedPrepTime() == null ? null : i.getEstimatedPrepTime().toMinutes())
                .available(i.getStatusCode() == ItemStatus.AVAILABLE)
                .originMadeIn(o == null ? null : o.getMadeIn())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MenuCategoryPublicResponse> fetchCategories() {
        return menuCategoryRepository.findVisibleCategoriesWithCount(VISIBLE).stream()
                .map(r -> MenuCategoryPublicResponse.builder()
                        .id((Long) r[0])
                        .code((String) r[1])
                        .name((String) r[2])
                        .description((String) r[3])
                        .menuPicture((String) r[4])
                        .itemCount((Long) r[5])
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public ResultPaginationDTO browse(String q, Long categoryId, Double minPrice, Double maxPrice,
                                      String sort, int page, int size) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new RequestInvalidException("minPrice must not be greater than maxPrice");
        }
        if (page < 1) {
            throw new RequestInvalidException("page must be greater than or equal to 1");
        }
        if (size < 1) {
            throw new RequestInvalidException("size must be greater than or equal to 1");
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page - 1, size, toSort(sort));

        Page<MenuItem> result = menuItemRepository.browse(VISIBLE, q, categoryId, minPrice, maxPrice, pageable);

        var res = new ResultPaginationDTO();
        res.setResult(result.stream().map(this::toResponse).toList());
        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(page);
        meta.setPageSize(size);
        meta.setPages(result.getTotalPages());
        meta.setTotal(result.getTotalElements());
        meta.setHasNext(result.hasNext());
        meta.setHasPrevious(result.hasPrevious());
        res.setMeta(meta);
        return res;
    }

}
