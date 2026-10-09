package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.MenuCategory;
import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.enumerations.ItemStatus;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.MenuCategoryRepository;
import com.restaurant.ireservation.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuBrowsingServiceTest {

    @Mock MenuItemRepository itemRepository;
    @Mock MenuCategoryRepository categoryRepository;
    @InjectMocks MenuBrowsingService service;

    @Test
    void fetchCategories_mapsRows() {
        List<Object[]> rows = List.<Object[]>of(new Object[]{1L, "DRINK", "Drinks", "d", null, 4L});
        when(categoryRepository.findVisibleCategoriesWithCount(any())).thenReturn(rows);
        var res = service.fetchCategories();
        assertEquals(1, res.size());
        assertEquals("Drinks", res.getFirst().getName());
        assertEquals(4L, res.getFirst().getItemCount());
    }

    @Test
    void browse_minGreaterThanMax_throws() {
        assertThrows(RequestInvalidException.class,
                () -> service.browse("", null, 100.0, 10.0, null, 1, 10));
    }

    @Test
    void browse_invalidSort_throws() {
        assertThrows(RequestInvalidException.class,
                () -> service.browse("", null, null, null, "hack", 1, 10));
    }

    @Test
    void browse_invalidPage_throws() {
        assertThrows(RequestInvalidException.class,
                () -> service.browse("", null, null, null, null, 0, 10));
    }

    @Test
    void browse_capsSizeAndUsesOneBasedPage() {
        when(itemRepository.browse(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var res = service.browse("", null, null, null, "price_desc", 2, 500);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepository).browse(any(), eq(""), any(), any(), any(), cap.capture());
        assertEquals(1, cap.getValue().getPageNumber());
        assertEquals(50, cap.getValue().getPageSize());
        var meta = ((com.restaurant.ireservation.util.ResultPaginationDTO) res).getMeta();
        assertEquals(2, meta.getPage());
    }

    @Test
    void browse_mapsAvailableFlag() {
        var c = new MenuCategory();
        c.setName("Main");
        var item = new MenuItem();
        item.setCategory(c);
        item.setItemName("Pho");
        item.setStatusCode(ItemStatus.OUT_OF_STOCK);
        when(itemRepository.browse(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        var res = service.browse("", null, null, null, null, 1, 10);
        @SuppressWarnings("unchecked")
        var list = (List<com.restaurant.ireservation.dto.response.MenuItemPublicResponse>) res.getResult();
        assertFalse(list.getFirst().isAvailable());
    }

    @Test
    void fetchById_archivedOrMissing_throws() {
        when(itemRepository.findVisibleById(eq(9L), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.fetchById(9L));
    }
}
