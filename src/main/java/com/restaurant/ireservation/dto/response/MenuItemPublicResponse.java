package com.restaurant.ireservation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemPublicResponse {
    Long id;
    Long categoryId;
    String categoryName;
    String itemName;
    String description;
    double price;
    String itemPicture;
    Long estimatedPrepMinutes;
    /** false when the item is currently OUT_OF_STOCK. */
    boolean available;
    String originMadeIn;
}
