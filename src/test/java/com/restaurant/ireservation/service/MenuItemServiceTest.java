package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.dto.request.MenuItemRequest;
import com.restaurant.ireservation.dto.request.MenuItemStatusRequest;
import com.restaurant.ireservation.error.ConflictException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.FoodOriginRepository;
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
class MenuItemServiceTest {

    @Mock MenuItemRepository itemRepository;
    @Mock MenuCategoryRepository categoryRepository;
    @Mock FoodOriginRepository originRepository;
    @InjectMocks MenuItemService service;

    private MenuItemRequest req(String name, String status) {
        return new MenuItemRequest(1L, name, " desc ", 45.5, null, 15, status, null);
    }

    private MenuCategory category() {
        var c = new MenuCategory();
        c.setName("Main");
        return c;
    }

    @Test
    void create_defaultsToAvailable_andMapsPrepTime() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(itemRepository.save(any(MenuItem.class))).thenAnswer(i -> i.getArgument(0));
        var res = service.create(req(" Pho ", null));
        assertEquals("Pho", res.getItemName());
        assertEquals("AVAILABLE", res.getStatus());
        assertEquals(15L, res.getEstimatedPrepMinutes());
        assertEquals("desc", res.getDescription());
    }

    @Test
    void create_categoryNotFound_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(req("Pho", null)));
    }

    @Test
    void create_duplicateNameInCategory_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(itemRepository.existsByCategoryIdAndItemNameIgnoreCase(any(), eq("Pho"))).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(req("Pho", null)));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void create_invalidStatus_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        assertThrows(RequestInvalidException.class, () -> service.create(req("Pho", "WRONG")));
    }

    @Test
    void changeStatus_updates() {
        var item = new MenuItem();
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
        var res = service.changeStatus(5L, new MenuItemStatusRequest("out_of_stock"));
        assertEquals("OUT_OF_STOCK", res.getStatus());
    }

    @Test
    void archive_setsArchived() {
        var item = new MenuItem();
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        service.archive(5L);
        assertEquals(ItemStatus.ARCHIVED, item.getStatusCode());
        verify(itemRepository).save(item);
    }

    @Test
    void archive_notFound_throws() {
        when(itemRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.archive(5L));
    }
}
