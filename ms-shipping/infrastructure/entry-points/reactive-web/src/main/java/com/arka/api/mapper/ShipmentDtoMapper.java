package com.arka.api.mapper;

import com.arka.api.dto.DeliveryAddressDto;
import com.arka.api.dto.ShipmentResponse;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.model.shipment.Shipment;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShipmentDtoMapper {

    public static ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
                shipment.id(),
                shipment.orderId(),
                shipment.trackingNumber(),
                shipment.carrier() != null ? shipment.carrier().name() : null,
                shipment.shippingLabelUrl(),
                shipment.status() != null ? shipment.status().name() : null,
                toDeliveryAddressDto(shipment.deliveryAddress()),
                shipment.estimatedDeliveryDate(),
                shipment.actualDeliveryDate(),
                shipment.failureReason(),
                shipment.createdAt(),
                shipment.updatedAt()
        );
    }

    public static DeliveryAddressDto toDeliveryAddressDto(DeliveryAddress address) {
        if (address == null) {
            return null;
        }
        return new DeliveryAddressDto(
                address.street(),
                address.city(),
                address.state(),
                address.postalCode(),
                address.country()
        );
    }
}
