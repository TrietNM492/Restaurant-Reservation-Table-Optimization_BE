package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiTableAllocationResponse {
    private Long reservationId;
    private String reservationNo;
    private PartySize partySize;
    private ReservationStatus reservationStatus;
    private Instant startTime;
    private Instant endTime;
    private Long areaId;
    private int totalCapacity;
    private List<ReservationResponseDTO.TableSummaryDTO> tables;
}
