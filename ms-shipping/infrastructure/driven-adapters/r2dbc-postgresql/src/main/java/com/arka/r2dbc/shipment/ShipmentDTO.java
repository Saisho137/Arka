package com.arka.r2dbc.shipment;

import io.r2dbc.postgresql.codec.Json;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("shipments")
@Builder(toBuilder = true)
public record ShipmentDTO(
        @Id UUID id,
        @Column("order_id") UUID orderId,
        @Column("tracking_number") String trackingNumber,
        String carrier,
        @Column("shipping_label_url") String shippingLabelUrl,
        String status,
        @Column("delivery_address") Json deliveryAddress,
        @Column("estimated_delivery_date") Instant estimatedDeliveryDate,
        @Column("actual_delivery_date") Instant actualDeliveryDate,
        @Column("failure_reason") String failureReason,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
