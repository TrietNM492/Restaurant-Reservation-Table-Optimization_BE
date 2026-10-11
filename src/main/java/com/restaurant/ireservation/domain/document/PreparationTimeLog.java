package com.restaurant.ireservation.domain.document;

import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "preparation_time_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreparationTimeLog {

    @org.springframework.data.annotation.Id
    String id;

    @Field(name = "menu_item_id")
    Long menuItemId;

    @Field(name = "estimate_minutes")
    int estimateMinutes;

    @Field(name = "actual_minutes")
    int actualMinutes;

    @CreatedDate
    @Field(name = "create_at")
    Instant createAt;

    @LastModifiedDate
    @Field(name = "update_at")
    Instant updateAt;
}
