package com.restaurant.ireservation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {
    Long id;
    Long categoryId;
    String categoryName;
    String itemName;
    String description;
    double price;
    String itemPicture;
    Long estimatedPrepMinutes;
    String status;
    Long originId;
    String originMadeIn;
    Instant createdDate;
    Instant lastModifiedDate;
}
