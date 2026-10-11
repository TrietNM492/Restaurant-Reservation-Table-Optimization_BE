package com.restaurant.ireservation.domain.document;

import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "occupancy_statistics")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class OccupancyStatistic {

    @org.springframework.data.annotation.Id
    String id;

    @Field(name = "business_date")
    Instant businessDate;

    @Field(name = "hour_slot")
    Integer hourSlot;

    @Field(name = "occupied_table")
    int occupiedTables;

    @Field(name = "available_tables")
    int availableTables;

    @Field(name = "occupancy_rate")
    double occupancyRate;

    @CreatedDate
    @Field(name = "create_at")
    Instant createAt;

    @Field(name = "update_at")
    @LastModifiedDate
    Instant updateAt;

}
