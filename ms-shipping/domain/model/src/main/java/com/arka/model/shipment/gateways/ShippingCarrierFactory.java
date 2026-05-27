package com.arka.model.shipment.gateways;

import com.arka.model.shipment.Carrier;

public interface ShippingCarrierFactory {

    ShippingCarrier getCarrier(Carrier carrier);
}
