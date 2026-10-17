package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.dto.request.MenuCategoryRequest;
import com.restaurant.ireservation.dto.response.MenuCategoryResponse;
import com.restaurant.ireservation.error.ConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
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

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class MenuCategoryService {
    final MenuCategoryRepository menuCategoryRepository;
    final MenuItemRepository menuItemRepository;

    private MenuCategory findOrThrow(Long id) {
        return menuCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found!"));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void apply(MenuCategory category, MenuCategoryRequest request, String code, String name) {
        category.setCode(code);
        category.setName(name);
        category.setDescription(blankToNull(request.getDescription()));
        category.setMenuQr(blankToNull(request.getMenuQr()));
        category.setMenuPicture(blankToNull(request.getMenuPicture()));
    }

    private MenuCategoryResponse toResponse(MenuCategory c) {
        return MenuCategoryResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .menuQr(c.getMenuQr())
                .menuPicture(c.getMenuPicture())
                .itemCount(menuItemRepository.countByCategoryId(c.getId()))
                .createdDate(c.getCreatedDate())
                .lastModifiedDate(c.getLastModifiedDate())
                .build();
    }

    @Transactional
    public ResultPaginationDTO fetchCategories(String q, Pageable pageable){
        Page<MenuCategory> page = menuCategoryRepository.search(q, pageable);
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
    public MenuCategoryResponse fetchById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public MenuCategoryResponse create(MenuCategoryRequest request) {
        String code = normalizeCode(request.getCode());
        String name = request.getName().trim();
        if (menuCategoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Category code '" + code + "' already exists");
        }
        if (menuCategoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name '" + name + "' already exists");
        }
        MenuCategory category = MenuCategory.builder().build();
        apply(category, request, code, name);
        return toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public MenuCategoryResponse update(Long id, MenuCategoryRequest request) {
        MenuCategory category = findOrThrow(id);
        String code = normalizeCode(request.getCode());
        String name = request.getName().trim();
        if (menuCategoryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("Category code '" + code + "' already exists");
        }
        if (menuCategoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category name '" + name + "' already exists");
        }
        apply(category, request, code, name);
        return toResponse(menuCategoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        MenuCategory category = findOrThrow(id);
        long items = menuItemRepository.countByCategoryId(id);
        if (items > 0) {
            throw new ConflictException(
                    "Cannot delete category '" + category.getName() + "': it still has " + items + " menu item(s)");
        }
        menuCategoryRepository.delete(category);
    }
}
