package com.arka.carrierfactory;

import com.arka.model.shipment.Carrier;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.model.shipment.ShippingResult;
import com.arka.model.shipment.gateways.ShippingCarrier;
import com.arka.model.shipment.gateways.ShippingCarrierFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ShippingCarrierFactoryImpl implements ShippingCarrierFactory {

    private final Map<Carrier, ShippingCarrier> carriers;

    public ShippingCarrierFactoryImpl(List<ShippingCarrier> carrierList) {
        this.carriers = carrierList.stream()
                .collect(Collectors.toMap(ShippingCarrier::supportedCarrier, Function.identity()));
    }

    @Override
    public ShippingCarrier getCarrier(Carrier carrier) {
        ShippingCarrier shippingCarrier = carriers.get(carrier);
        if (shippingCarrier == null) {
            throw new IllegalArgumentException("No carrier implementation found for: " + carrier);
        }
        return shippingCarrier;
    }
}
