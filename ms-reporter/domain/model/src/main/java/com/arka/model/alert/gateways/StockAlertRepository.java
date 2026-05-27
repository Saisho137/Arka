package com.arka.model.alert.gateways;

import com.arka.model.alert.StockAlert;

import java.util.Optional;

public interface StockAlertRepository {

    void save(StockAlert alert);

    Optional<StockAlert> findActiveAlertBySku(String sku);

    void resolveAlert(String sku);
}
