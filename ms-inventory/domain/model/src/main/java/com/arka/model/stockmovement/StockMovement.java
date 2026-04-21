package com.arka.model.stockmovement;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockMovement(
        UUID id,
        String sku,
        MovementType movementType,
        int quantityChange,
        int previousQuantity,
        int newQuantity,
        UUID orderId,
        String reason,
        Instant createdAt
) {
    public StockMovement {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(movementType, "movementType is required");
        if (previousQuantity < 0) throw new IllegalArgumentException("previousQuantity must be >= 0");
        if (newQuantity < 0) throw new IllegalArgumentException("newQuantity must be >= 0");
        if (newQuantity != previousQuantity + quantityChange) {
            throw new IllegalArgumentException(
                    "Inconsistent movement: previousQuantity(" + previousQuantity
                            + ") + quantityChange(" + quantityChange
                            + ") != newQuantity(" + newQuantity + ")");
        }
        // id is nullable — DB generates UUID via DEFAULT gen_random_uuid()
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public boolean isStockIncrease() {
        return quantityChange > 0;
    }

    public boolean isStockDecrease() {
        return quantityChange < 0;
    }

    public static StockMovement restock(String sku, int previousQuantity, int newQuantity, String reason) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.RESTOCK)
                .quantityChange(newQuantity - previousQuantity)
                .previousQuantity(previousQuantity)
                .newQuantity(newQuantity)
                .reason(reason)
                .build();
    }

    public static StockMovement shrinkage(String sku, int previousQuantity, int newQuantity, String reason) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.SHRINKAGE)
                .quantityChange(newQuantity - previousQuantity)
                .previousQuantity(previousQuantity)
                .newQuantity(newQuantity)
                .reason(reason)
                .build();
    }

    public static StockMovement orderReserve(String sku, int quantity, int previousAvailable, UUID orderId) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.ORDER_RESERVE)
                .quantityChange(-quantity)
                .previousQuantity(previousAvailable)
                .newQuantity(previousAvailable - quantity)
                .orderId(orderId)
                .reason("Stock reserved for order")
                .build();
    }

    public static StockMovement reservationRelease(String sku, int quantity, int previousAvailable, UUID orderId, String reason) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.RESERVATION_RELEASE)
                .quantityChange(quantity)
                .previousQuantity(previousAvailable)
                .newQuantity(previousAvailable + quantity)
                .orderId(orderId)
                .reason(reason)
                .build();
    }

    public static StockMovement productCreation(String sku, int initialStock) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.PRODUCT_CREATION)
                .quantityChange(initialStock)
                .previousQuantity(0)
                .newQuantity(initialStock)
                .reason("Initial stock from product creation")
                .build();
    }

    public static StockMovement orderConfirm(String sku, int confirmedQuantity, UUID orderId) {
        return StockMovement.builder()
                .sku(sku)
                .movementType(MovementType.ORDER_CONFIRM)
                .quantityChange(0)
                .previousQuantity(confirmedQuantity)
                .newQuantity(confirmedQuantity)
                .orderId(orderId)
                .reason("Reservation confirmed for order")
                .build();
    }
}
