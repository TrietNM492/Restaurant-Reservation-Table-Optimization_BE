package com.restaurant.ireservation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VnPayIpnResponseDTO {

    @JsonProperty("RspCode")
    String rspCode;

    @JsonProperty("Message")
    String message;

    public static VnPayIpnResponseDTO success() {
        return new VnPayIpnResponseDTO("00", "Confirm Success");
    }

    public static VnPayIpnResponseDTO orderNotFound() {
        return new VnPayIpnResponseDTO("01", "Order not found");
    }

    public static VnPayIpnResponseDTO orderAlreadyConfirmed() {
        return new VnPayIpnResponseDTO("02", "Order already confirmed");
    }

    public static VnPayIpnResponseDTO invalidAmount() {
        return new VnPayIpnResponseDTO("04", "Invalid Amount");
    }

    public static VnPayIpnResponseDTO invalidChecksum() {
        return new VnPayIpnResponseDTO("97", "Invalid Checksum");
    }

    public static VnPayIpnResponseDTO unknownError() {
        return new VnPayIpnResponseDTO("99", "Unknown error");
    }
}
