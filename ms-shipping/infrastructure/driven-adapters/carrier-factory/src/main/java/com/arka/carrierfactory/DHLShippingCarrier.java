package com.arka.carrierfactory;

import com.arka.model.shipment.Carrier;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.model.shipment.ShippingResult;
import com.arka.model.shipment.gateways.ShippingCarrier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
public class DHLShippingCarrier implements ShippingCarrier {

    @Override
    public Mono<ShippingResult> generateLabel(UUID orderId, DeliveryAddress address) {
        return Mono.fromCallable(() -> simulateDHLCall(orderId, address))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(java.time.Duration.ofSeconds(30));
    }

    @Override
    public Carrier supportedCarrier() {
        return Carrier.DHL;
    }

    private ShippingResult simulateDHLCall(UUID orderId, DeliveryAddress address) {
        log.info("DHL SDK: Generating label for orderId={}", orderId);
        String trackingNumber = "DHL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        byte[] labelPdf = ("DHL Label for " + orderId).getBytes();
        return ShippingResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .labelPdf(labelPdf)
                .estimatedDeliveryDate(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();
    }
}
