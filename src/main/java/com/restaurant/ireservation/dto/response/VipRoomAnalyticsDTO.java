package com.restaurant.ireservation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Module: VIP Room Analytics [Dashboard]
 * Theo dõi: VIP Room Utilization | Revenue By VIP Room | Booking Frequency
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VipRoomAnalyticsDTO {

    // ─── Tổng quan VIP Room Utilization ───────────────────────────────────────

    // Tổng số phòng VIP
    int totalVipRooms;

    // Số phòng hiện đang được sử dụng (OCCUPIED / RESERVED)
    int occupiedVipRooms;

    // Số phòng khả dụng (AVAILABLE)
    int availableVipRooms;

    // Tỷ lệ sử dụng tổng thể (%) = occupiedVipRooms / totalVipRooms * 100
    double overallUtilizationPercent;

    // Tổng doanh thu từ tất cả phòng VIP (PAID payments)
    double totalVipRevenue;

    // Tổng số lượt đặt phòng VIP
    long totalVipBookings;

    // Tần suất đặt phòng trung bình mỗi phòng
    double averageBookingsPerRoom;

    // Chi tiết analytics theo từng phòng VIP
    List<VipRoomDetailDTO> rooms;

    // ─── Inner DTO: Chi tiết từng phòng ───────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class VipRoomDetailDTO {

        Long roomId;
        String roomName;
        RoomType roomType;
        int capacity;
        double pricePerSession;
        RoomStatus roomStatus;
        String description;

        // VIP Room Utilization — tỷ lệ sử dụng phòng (%)
        double utilizationPercent;

        // Revenue By VIP Room — doanh thu của phòng này
        double totalRevenue;

        // Booking Frequency — số lần đặt phòng
        long bookingCount;

        // Trạng thái phòng hiện tại (đang trống / đang bận)
        @JsonProperty("isCurrentlyOccupied")
        boolean isCurrentlyOccupied;
    }
}
