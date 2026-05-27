package com.arka.r2dbc.shipment;

import com.arka.model.shipment.*;
import io.r2dbc.postgresql.codec.Json;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ShipmentDTOMapper {

    static Shipment toDomain(ShipmentDTO dto) {
        return Shipment.builder()
                .id(dto.id())
                .orderId(dto.orderId())
                .trackingNumber(dto.trackingNumber())
                .carrier(Carrier.fromValue(dto.carrier()))
                .shippingLabelUrl(dto.shippingLabelUrl())
                .status(ShippingStatus.fromValue(dto.status()))
                .deliveryAddress(parseAddress(dto.deliveryAddress()))
                .estimatedDeliveryDate(dto.estimatedDeliveryDate())
                .actualDeliveryDate(dto.actualDeliveryDate())
                .failureReason(dto.failureReason())
                .createdAt(dto.createdAt())
                .updatedAt(dto.updatedAt())
                .build();
    }

    static ShipmentDTO toDTO(Shipment domain) {
        return ShipmentDTO.builder()
                .id(domain.id())
                .orderId(domain.orderId())
                .trackingNumber(domain.trackingNumber())
                .carrier(domain.carrier().name())
                .shippingLabelUrl(domain.shippingLabelUrl())
                .status(domain.status().name())
                .deliveryAddress(serializeAddress(domain.deliveryAddress()))
                .estimatedDeliveryDate(domain.estimatedDeliveryDate())
                .actualDeliveryDate(domain.actualDeliveryDate())
                .failureReason(domain.failureReason())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }

    private static DeliveryAddress parseAddress(Json json) {
        if (json == null) return null;
        String raw = json.asString();
        // Simple JSON parsing without external dependency in this layer
        String street = extractField(raw, "street");
        String city = extractField(raw, "city");
        String state = extractField(raw, "state");
        String postalCode = extractField(raw, "postalCode");
        String country = extractField(raw, "country");
        return new DeliveryAddress(street, city, state, postalCode, country);
    }

    private static Json serializeAddress(DeliveryAddress address) {
        if (address == null) return null;
        String json = "{\"street\":\"" + escapeJson(address.street()) + "\","
                + "\"city\":\"" + escapeJson(address.city()) + "\","
                + "\"state\":\"" + escapeJson(address.state()) + "\","
                + "\"postalCode\":\"" + escapeJson(address.postalCode()) + "\","
                + "\"country\":\"" + escapeJson(address.country()) + "\"}";
        return Json.of(json);
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : "";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
