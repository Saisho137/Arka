package com.arka.api;

import com.arka.api.purchaseorder.PurchaseOrderHandler;
import com.arka.api.supplier.SupplierHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> supplierRoutes(SupplierHandler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/suppliers", handler::createSupplier)
                .GET("/api/v1/suppliers", handler::listSuppliers)
                .GET("/api/v1/suppliers/{supplierId}", handler::getSupplier)
                .PUT("/api/v1/suppliers/{supplierId}", handler::updateSupplier)
                .DELETE("/api/v1/suppliers/{supplierId}", handler::deactivateSupplier)
                .POST("/api/v1/suppliers/{supplierId}/products", handler::assignProduct)
                .DELETE("/api/v1/suppliers/{supplierId}/products/{sku}", handler::removeProduct)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> purchaseOrderRoutes(PurchaseOrderHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/purchase-orders", handler::listPurchaseOrders)
                .GET("/api/v1/purchase-orders/{id}", handler::getPurchaseOrder)
                .POST("/api/v1/purchase-orders", handler::createPurchaseOrder)
                .PUT("/api/v1/purchase-orders/{id}/send", handler::sendPurchaseOrder)
                .PUT("/api/v1/purchase-orders/{id}/confirm", handler::confirmPurchaseOrder)
                .PUT("/api/v1/purchase-orders/{id}/receive", handler::receivePurchaseOrder)
                .PUT("/api/v1/purchase-orders/{id}/cancel", handler::cancelPurchaseOrder)
                .build();
    }
}
