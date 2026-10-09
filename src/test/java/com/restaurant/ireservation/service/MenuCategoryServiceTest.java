package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.dto.request.MenuCategoryRequest;
import com.restaurant.ireservation.error.ConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.MenuCategoryRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuCategoryServiceTest {

    @Mock MenuCategoryRepository categoryRepository;
    @Mock MenuItemRepository itemRepository;
    @InjectMocks MenuCategoryService service;

    private MenuCategoryRequest req(String code, String name) {
        return new MenuCategoryRequest(code, name, "  desc ", null, "");
    }

    @Test
    void create_normalizesCodeAndTrims() {
        when(categoryRepository.save(any(MenuCategory.class))).thenAnswer(i -> i.getArgument(0));
        var res = service.create(req(" main-dish ", " Main Dish "));
        assertEquals("MAIN-DISH", res.getCode());
        assertEquals("Main Dish", res.getName());
        assertEquals("desc", res.getDescription());
        assertNull(res.getMenuPicture());
    }

    @Test
    void create_duplicateCode_throws() {
        when(categoryRepository.existsByCodeIgnoreCase("DRINK")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(req("drink", "Drinks")));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_duplicateName_throws() {
        when(categoryRepository.existsByNameIgnoreCase("Drinks")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(req("drink", "Drinks")));
    }

    @Test
    void update_notFound_throws() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, req("A", "B")));
    }

    @Test
    void update_duplicateCodeOfOther_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new MenuCategory()));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("A", 1L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(1L, req("a", "B")));
    }

    @Test
    void delete_withItems_throws() {
        var c = new MenuCategory();
        c.setName("Drinks");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(c));
        when(itemRepository.countByCategoryId(1L)).thenReturn(3L);
        assertThrows(ConflictException.class, () -> service.delete(1L));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_empty_deletes() {
        var c = new MenuCategory();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(c));
        when(itemRepository.countByCategoryId(1L)).thenReturn(0L);
        service.delete(1L);
        verify(categoryRepository).delete(c);
    }
}
