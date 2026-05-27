package com.arka.model.shipment.gateways;

import com.arka.model.shipment.Carrier;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.model.shipment.ShippingResult;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ShippingCarrier {

    Mono<ShippingResult> generateLabel(UUID orderId, DeliveryAddress address);

    Carrier supportedCarrier();
}
