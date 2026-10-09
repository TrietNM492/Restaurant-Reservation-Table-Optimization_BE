package com.restaurant.ireservation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Module: Table Status Tracking [2]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AreaFloorPlanResponse {

    Long areaId;
    String areaCode;
    String areaName;
    String areaDescription;
    List<TableResponse> tables;
}
