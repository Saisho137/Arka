package com.arka.usecase.transitionpurchaseorder;

import com.arka.model.commons.exception.InvalidStateTransitionException;
import com.arka.model.commons.exception.PurchaseOrderNotFoundException;
import com.arka.model.gateways.OutboxEventRepository;
import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.outbox.EventType;
import com.arka.model.outbox.OutboxEvent;
import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import com.arka.usecase.commons.JsonSerializer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class TransitionPurchaseOrderUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonSerializer jsonSerializer;

    public Mono<PurchaseOrder> execute(UUID purchaseOrderId, PurchaseOrderStatus targetStatus) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .switchIfEmpty(Mono.error(new PurchaseOrderNotFoundException(purchaseOrderId.toString())))
                .flatMap(po -> {
                    if (!po.status().canTransitionTo(targetStatus)) {
                        return Mono.error(new InvalidStateTransitionException(
                                po.status().name(), targetStatus.name()));
                    }
                    PurchaseOrder updated = applyTransition(po, targetStatus);
                    return purchaseOrderRepository.updateStatus(updated)
                            .flatMap(savedPo -> {
                                if (targetStatus == PurchaseOrderStatus.SENT) {
                                    return saveOutboxEvent(savedPo).thenReturn(savedPo);
                                }
                                return Mono.just(savedPo);
                            });
                });
    }

    private PurchaseOrder applyTransition(PurchaseOrder po, PurchaseOrderStatus target) {
        var builder = po.toBuilder().status(target).updatedAt(Instant.now());
        switch (target) {
            case SENT -> builder.sentAt(Instant.now());
            case CONFIRMED -> builder.confirmedAt(Instant.now());
            case RECEIVED, PARTIALLY_RECEIVED -> builder.receivedAt(Instant.now());
            default -> { /* no timestamp update */ }
        }
        return builder.build();
    }

    private Mono<OutboxEvent> saveOutboxEvent(PurchaseOrder po) {
        String payload = buildPayloadJson(po);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.PURCHASE_ORDER_SENT)
                .partitionKey(po.id().toString())
                .payload(payload)
                .build();
        return outboxEventRepository.save(outboxEvent);
    }

    private String buildPayloadJson(PurchaseOrder po) {
        return jsonSerializer.serialize(Map.of(
                "purchaseOrderId", po.id().toString(),
                "supplierId", po.supplierId().toString(),
                "status", po.status().name(),
                "totalAmount", po.totalAmount().toPlainString()
        ));
    }
}
