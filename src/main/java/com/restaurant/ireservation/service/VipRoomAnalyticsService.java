package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.dto.response.VipRoomAnalyticsDTO;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module: VIP Room Analytics [Dashboard]
 * Theo dõi: VIP Room Utilization | Revenue By VIP Room | Booking Frequency
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VipRoomAnalyticsService {

    RoomRepository roomRepository;
    ReservationRoomRepository reservationRoomRepository;

    // Các trạng thái phòng được coi là "đang bận"
    static final Set<RoomStatus> OCCUPIED_STATUSES = Set.of(
            RoomStatus.OCCUPIED,
            RoomStatus.RESERVED,
            RoomStatus.PREPARING
    );

    // Trạng thái payment được tính doanh thu
    static final List<PaymentStatus> PAID_STATUSES = List.of(
            PaymentStatus.PAID,
            PaymentStatus.PARTIALLY_PAID
    );

    /**
     * FR: VIP Room Analytics Dashboard
     * Tổng hợp: Utilization | Revenue By Room | Booking Frequency
     */
    @Transactional(readOnly = true)
    public VipRoomAnalyticsDTO getVipRoomAnalytics() {
        // --- Lấy tất cả phòng VIP ---
        List<Room> allRooms = roomRepository.findAll();

        // --- Booking Frequency: số lần đặt theo từng phòng ---
        List<Object[]> bookingRows = reservationRoomRepository.countBookingsGroupByRoom();
        Map<Long, Long> bookingCountByRoomId = new HashMap<>();
        for (Object[] row : bookingRows) {
            Long roomId = row[0] instanceof Number ? ((Number) row[0]).longValue() : null;
            Long count  = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            if (roomId != null) {
                bookingCountByRoomId.put(roomId, count);
            }
        }

        // --- Revenue By VIP Room: doanh thu (PAID) theo từng phòng ---
        List<Object[]> revenueRows = reservationRoomRepository.sumRevenueGroupByRoom(PAID_STATUSES);
        Map<Long, Double> revenueByRoomId = new HashMap<>();
        for (Object[] row : revenueRows) {
            Long roomId  = row[0] instanceof Number ? ((Number) row[0]).longValue() : null;
            Double rev   = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            if (roomId != null) {
                revenueByRoomId.put(roomId, rev);
            }
        }

        // --- Tổng hợp theo từng phòng ---
        int totalRooms     = allRooms.size();
        int occupiedRooms  = 0;
        int availableRooms = 0;
        double totalRevenue   = 0.0;
        long totalBookings    = 0L;

        List<VipRoomAnalyticsDTO.VipRoomDetailDTO> roomDetails = new java.util.ArrayList<>();

        for (Room room : allRooms) {
            Long roomId = room.getId();
            boolean isOccupied = OCCUPIED_STATUSES.contains(room.getRoomStatus());
            if (isOccupied) occupiedRooms++;
            if (room.getRoomStatus() == RoomStatus.AVAILABLE) availableRooms++;

            long bookingCount  = bookingCountByRoomId.getOrDefault(roomId, 0L);
            double roomRevenue = revenueByRoomId.getOrDefault(roomId, 0.0);

            totalRevenue  += roomRevenue;
            totalBookings += bookingCount;

            // Utilization per room: số lần đặt / tổng đặt phòng (nếu có) * 100
            // Dùng trạng thái phòng hiện tại để tính utilization tức thì
            double roomUtilization = isOccupied ? 100.0 : 0.0;

            roomDetails.add(VipRoomAnalyticsDTO.VipRoomDetailDTO.builder()
                    .roomId(roomId)
                    .roomName(room.getRoomName())
                    .roomType(room.getRoomType())
                    .capacity(room.getCapacity())
                    .pricePerSession(room.getPrice())
                    .roomStatus(room.getRoomStatus())
                    .description(room.getDescription())
                    .utilizationPercent(roomUtilization)
                    .totalRevenue(roomRevenue)
                    .bookingCount(bookingCount)
                    .isCurrentlyOccupied(isOccupied)
                    .build());
        }

        // --- Overall Utilization ---
        double overallUtilization = totalRooms > 0
                ? Math.min(100.0, (double) occupiedRooms / totalRooms * 100.0)
                : 0.0;

        // --- Average Bookings Per Room ---
        double avgBookingsPerRoom = totalRooms > 0
                ? (double) totalBookings / totalRooms
                : 0.0;

        log.info("[VIP Room Analytics] totalRooms={}, occupied={}, available={}, utilization={}%, totalRevenue={}, totalBookings={}",
                totalRooms, occupiedRooms, availableRooms,
                String.format("%.1f", overallUtilization),
                String.format("%.2f", totalRevenue),
                totalBookings);

        return VipRoomAnalyticsDTO.builder()
                .totalVipRooms(totalRooms)
                .occupiedVipRooms(occupiedRooms)
                .availableVipRooms(availableRooms)
                .overallUtilizationPercent(overallUtilization)
                .totalVipRevenue(totalRevenue)
                .totalVipBookings(totalBookings)
                .averageBookingsPerRoom(avgBookingsPerRoom)
                .rooms(roomDetails)
                .build();
    }
}
