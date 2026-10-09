package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.FoodOrigin;
import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.dto.request.MenuItemRequest;
import com.restaurant.ireservation.dto.request.MenuItemStatusRequest;
import com.restaurant.ireservation.dto.response.MenuItemResponse;
import com.restaurant.ireservation.error.ConflictException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.FoodOriginRepository;
import com.restaurant.ireservation.repository.MenuCategoryRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MenuItemService {

    final MenuItemRepository menuItemRepository;
    final MenuCategoryRepository menuCategoryRepository;
    final FoodOriginRepository foodOriginRepository;

    /** Soft delete: orders may reference the item, so it is archived instead of removed. */
    @Transactional
    public void archive(Long id) {
        MenuItem item = findOrThrow(id);
        item.setStatusCode(ItemStatus.ARCHIVED);
        menuItemRepository.save(item);
    }

    private MenuItem findOrThrow(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found!"));
    }

    private MenuCategory findCategory(Long categoryId) {
        return menuCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found!"));
    }

    private ItemStatus parseStatus(String status) {
        try {
            return ItemStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RequestInvalidException("Invalid status '" + status + "'. Allowed: AVAILABLE, OUT_OF_STOCK, ARCHIVED");
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void apply(MenuItem item, MenuItemRequest request, MenuCategory category, String name) {
        item.setCategory(category);
        item.setItemName(name);
        item.setDescription(blankToNull(request.getDescription()));
        item.setPrice(request.getPrice());
        item.setItemPicture(blankToNull(request.getItemPicture()));
        item.setEstimatedPrepTime(request.getEstimatedPrepMinutes() == null
                ? null : Duration.ofMinutes(request.getEstimatedPrepMinutes()));
        if (request.getOriginId() == null) {
            item.setOrigin(null);
        } else {
            FoodOrigin origin = foodOriginRepository.findById(request.getOriginId())
                    .orElseThrow(() -> new ResourceNotFoundException("Food origin not found!"));
            item.setOrigin(origin);
        }
    }

    private MenuItemResponse toResponse(MenuItem i) {
        MenuCategory c = i.getCategory();
        FoodOrigin o = i.getOrigin();
        return MenuItemResponse.builder()
                .id(i.getId())
                .categoryId(c == null ? null : c.getId())
                .categoryName(c == null ? null : c.getName())
                .itemName(i.getItemName())
                .description(i.getDescription())
                .price(i.getPrice())
                .itemPicture(i.getItemPicture())
                .estimatedPrepMinutes(i.getEstimatedPrepTime() == null ? null : i.getEstimatedPrepTime().toMinutes())
                .status(i.getStatusCode() == null ? null : i.getStatusCode().name())
                .originId(o == null ? null : o.getId())
                .originMadeIn(o == null ? null : o.getMadeIn())
                .createdDate(i.getCreatedDate())
                .lastModifiedDate(i.getLastModifiedDate())
                .build();
    }

    @Transactional(readOnly = true)
    public ResultPaginationDTO fetchItems(String q, Long categoryId, String status, Pageable pageable) {
        ItemStatus itemStatus = status == null || status.isBlank() ? null : parseStatus(status);
        Page<MenuItem> page = menuItemRepository.search(q, categoryId, itemStatus, pageable);
        var res = new ResultPaginationDTO();
        res.setResult(page.stream().map(this::toResponse).toList());
        var meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        meta.setHasNext(page.hasNext());
        meta.setHasPrevious(page.hasPrevious());
        res.setMeta(meta);
        return res;
    }

    @Transactional(readOnly = true)
    public MenuItemResponse fetchById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public MenuItemResponse create(MenuItemRequest request) {
        String name = request.getItemName().trim();
        MenuCategory category = findCategory(request.getCategoryId());
        if (menuItemRepository.existsByCategoryIdAndItemNameIgnoreCase(category.getId(), name)) {
            throw new ConflictException("Item '" + name + "' already exists in category '" + category.getName() + "'");
        }
        MenuItem item = new MenuItem();
        item.setStatusCode(request.getStatus() == null || request.getStatus().isBlank()
                ? ItemStatus.AVAILABLE : parseStatus(request.getStatus()));
        apply(item, request, category, name);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse update(Long id, MenuItemRequest request) {
        MenuItem item = findOrThrow(id);
        String name = request.getItemName().trim();
        MenuCategory category = findCategory(request.getCategoryId());
        if (menuItemRepository.existsByCategoryIdAndItemNameIgnoreCaseAndIdNot(category.getId(), name, id)) {
            throw new ConflictException("Item '" + name + "' already exists in category '" + category.getName() + "'");
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            item.setStatusCode(parseStatus(request.getStatus()));
        }
        apply(item, request, category, name);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse changeStatus(Long id, MenuItemStatusRequest request) {
        MenuItem item = findOrThrow(id);
        item.setStatusCode(parseStatus(request.getStatus()));
        return toResponse(menuItemRepository.save(item));
    }
}
