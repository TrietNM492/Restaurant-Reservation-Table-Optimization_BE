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
public class MenuCategoryResponse {
    Long id;
    String code;
    String name;
    String description;
    String menuQr;
    String menuPicture;
    long itemCount;
    Instant createdDate;
    Instant lastModifiedDate;
}
