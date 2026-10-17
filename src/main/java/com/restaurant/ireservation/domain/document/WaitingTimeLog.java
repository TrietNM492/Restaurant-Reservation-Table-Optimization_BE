package com.restaurant.ireservation.domain.document;

import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Document(collection = "waiting_time_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaitingTimeLog {

    @org.springframework.data.annotation.Id
    String id;

    @org.springframework.data.mongodb.core.index.Indexed
    @Field(name = "reservation_id", targetType = FieldType.INT64)
    Long reservationId;

    @Field(name = "estimated_wait_minutes")
    int estimatedWaitMinutes;

    @Field(name = "actual_wait_minutes")
    int actualWaitMinutes;

    @CreatedDate
    @Field(name = "create_at")
    Instant createAt;

    @LastModifiedDate
    @Field(name = "update_at")
    Instant updateAt;

}
