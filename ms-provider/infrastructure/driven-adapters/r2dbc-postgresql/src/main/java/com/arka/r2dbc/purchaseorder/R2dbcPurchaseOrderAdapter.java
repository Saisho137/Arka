package com.arka.r2dbc.purchaseorder;

import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderItem;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcPurchaseOrderAdapter implements PurchaseOrderRepository {

    private final SpringDataPurchaseOrderRepository repository;
    private final SpringDataPurchaseOrderItemRepository itemRepository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<PurchaseOrder> save(PurchaseOrder purchaseOrder) {
        return repository.save(PurchaseOrderMapper.toEntity(purchaseOrder))
                .map(PurchaseOrderMapper::toDomain);
    }

    @Override
    public Mono<PurchaseOrderItem> saveItem(PurchaseOrderItem item) {
        return itemRepository.save(PurchaseOrderMapper.itemToEntity(item))
                .map(PurchaseOrderMapper::itemToDomain);
    }

    @Override
    public Mono<PurchaseOrder> findById(UUID id) {
        return repository.findById(id)
                .map(PurchaseOrderMapper::toDomain);
    }

    @Override
    public Flux<PurchaseOrderItem> findItemsByPurchaseOrderId(UUID purchaseOrderId) {
        return itemRepository.findByPurchaseOrderId(purchaseOrderId)
                .map(PurchaseOrderMapper::itemToDomain);
    }

    @Override
    public Flux<PurchaseOrder> findAll(PurchaseOrderStatus status, UUID supplierId, String sku,
                                        Instant dateFrom, Instant dateTo, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT po.* FROM purchase_orders po");
        if (sku != null) {
            sql.append(" JOIN purchase_order_items poi ON po.id = poi.purchase_order_id");
        }
        sql.append(" WHERE 1=1");

        if (status != null) sql.append(" AND po.status = :status");
        if (supplierId != null) sql.append(" AND po.supplier_id = :supplierId");
        if (sku != null) sql.append(" AND poi.sku = :sku");
        if (dateFrom != null) sql.append(" AND po.created_at >= :dateFrom");
        if (dateTo != null) sql.append(" AND po.created_at <= :dateTo");

        sql.append(" ORDER BY po.created_at DESC LIMIT :size OFFSET :offset");

        var spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (supplierId != null) spec = spec.bind("supplierId", supplierId);
        if (sku != null) spec = spec.bind("sku", sku);
        if (dateFrom != null) spec = spec.bind("dateFrom", dateFrom);
        if (dateTo != null) spec = spec.bind("dateTo", dateTo);
        spec = spec.bind("size", size).bind("offset", (long) page * size);

        return spec.map((row, metadata) -> PurchaseOrder.builder()
                        .id(row.get("id", UUID.class))
                        .supplierId(row.get("supplier_id", UUID.class))
                        .status(PurchaseOrderStatus.valueOf(row.get("status", String.class)))
                        .totalAmount(row.get("total_amount", BigDecimal.class))
                        .notes(row.get("notes", String.class))
                        .createdAt(row.get("created_at", Instant.class))
                        .updatedAt(row.get("updated_at", Instant.class))
                        .sentAt(row.get("sent_at", Instant.class))
                        .confirmedAt(row.get("confirmed_at", Instant.class))
                        .receivedAt(row.get("received_at", Instant.class))
                        .build())
                .all();
    }

    @Override
    public Mono<Long> count(PurchaseOrderStatus status, UUID supplierId, String sku,
                            Instant dateFrom, Instant dateTo) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT po.id) FROM purchase_orders po");
        if (sku != null) {
            sql.append(" JOIN purchase_order_items poi ON po.id = poi.purchase_order_id");
        }
        sql.append(" WHERE 1=1");

        if (status != null) sql.append(" AND po.status = :status");
        if (supplierId != null) sql.append(" AND po.supplier_id = :supplierId");
        if (sku != null) sql.append(" AND poi.sku = :sku");
        if (dateFrom != null) sql.append(" AND po.created_at >= :dateFrom");
        if (dateTo != null) sql.append(" AND po.created_at <= :dateTo");

        var spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (supplierId != null) spec = spec.bind("supplierId", supplierId);
        if (sku != null) spec = spec.bind("sku", sku);
        if (dateFrom != null) spec = spec.bind("dateFrom", dateFrom);
        if (dateTo != null) spec = spec.bind("dateTo", dateTo);

        return spec.map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> existsBySkuAndStatusIn(String sku, List<PurchaseOrderStatus> statuses) {
        String statusList = String.join(",", statuses.stream().map(s -> "'" + s.name() + "'").toList());
        String sql = "SELECT EXISTS(SELECT 1 FROM purchase_orders po " +
                "JOIN purchase_order_items poi ON po.id = poi.purchase_order_id " +
                "WHERE poi.sku = :sku AND po.status IN (" + statusList + "))";
        return databaseClient.sql(sql)
                .bind("sku", sku)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<PurchaseOrder> updateStatus(PurchaseOrder po) {
        return repository.updateStatus(po.id(), po.status().name(), po.updatedAt(),
                        po.sentAt(), po.confirmedAt(), po.receivedAt())
                .thenReturn(po);
    }
}
