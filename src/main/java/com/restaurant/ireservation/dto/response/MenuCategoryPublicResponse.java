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
public class MenuCategoryPublicResponse {
    Long id;
    String code;
    String name;
    String description;
    String menuPicture;
    long itemCount;
}
