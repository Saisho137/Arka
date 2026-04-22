package com.arka.usecase.notification;

import com.arka.model.notification.DomainEventEnvelope;
import com.arka.model.notification.NotificationHistory;
import com.arka.model.notification.NotificationStatus;
import com.arka.model.notification.gateways.EmailSenderPort;
import com.arka.model.notification.gateways.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Core use case: processes an inbound Kafka domain event and sends an email notification.
 *
 * Flow:
 * 1. Check idempotency via notification_history.eventId
 * 2. Resolve recipient + content from eventType / payload
 * 3. Send email via SES
 * 4. Save notification history (SENT or FAILED)
 */
@RequiredArgsConstructor
public class ProcessNotificationUseCase {

    private final NotificationHistoryRepository historyRepository;
    private final EmailSenderPort emailSender;
    private final String fromAddress;
    private final String adminEmail;

    public Mono<Void> process(DomainEventEnvelope event) {
        return historyRepository.existsByEventId(event.eventId())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.empty();
                    }
                    return resolveAndSend(event);
                });
    }

    private Mono<Void> resolveAndSend(DomainEventEnvelope event) {
        return Mono.defer(() -> {
            EmailContent content = buildEmailContent(event);
            if (content == null) {
                return Mono.empty();
            }

            return emailSender.send(content.to(), content.subject(), content.body())
                    .then(saveHistory(event, content.to(), content.subject(), NotificationStatus.SENT, null))
                    .onErrorResume(ex ->
                        saveHistory(event, content.to(), content.subject(),
                                NotificationStatus.FAILED, ex.getMessage()).then()
                    );
        });
    }

    private Mono<Void> saveHistory(DomainEventEnvelope event, String recipient, String subject,
                                    NotificationStatus status, String errorMessage) {
        NotificationHistory history = NotificationHistory.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .recipientEmail(recipient)
                .subject(subject)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        return historyRepository.save(history).then();
    }

    private EmailContent buildEmailContent(DomainEventEnvelope event) {
        return switch (event.eventType()) {
            case "OrderConfirmed" -> {
                String orderId = event.payloadString("orderId");
                String email = event.payloadString("customerEmail");
                String amount = event.payloadString("totalAmount");
                yield new EmailContent(
                        email,
                        "Tu pedido ha sido confirmado — Arka",
                        "¡Hola!\n\nTu pedido #" + orderId + " ha sido confirmado exitosamente.\n"
                                + "Total: $" + amount + " COP\n\n"
                                + "Recibirás un correo cuando tu pedido sea despachado.\n\n"
                                + "Gracias por comprar en Arka.");
            }
            case "OrderCancelled" -> {
                String orderId = event.payloadString("orderId");
                String email = event.payloadString("customerEmail");
                String reason = event.payloadString("reason");
                if (reason.isEmpty()) reason = "Sin motivo especificado";
                yield new EmailContent(
                        email,
                        "Tu pedido ha sido cancelado — Arka",
                        "Hola,\n\nLamentamos informarte que tu pedido #" + orderId + " ha sido cancelado.\n"
                                + "Motivo: " + reason + "\n\nSi tienes preguntas, contáctanos.\n\nEquipo Arka");
            }
            case "OrderStatusChanged" -> {
                String orderId = event.payloadString("orderId");
                String email = event.payloadString("customerEmail");
                String newStatus = event.payloadString("newStatus");
                yield new EmailContent(
                        email,
                        "Actualización de tu pedido — Arka",
                        "Hola,\n\nEl estado de tu pedido #" + orderId + " ha sido actualizado a: "
                                + newStatus + "\n\nGracias por comprar en Arka.");
            }
            case "StockDepleted" -> {
                String sku = event.payloadString("sku");
                int qty = event.payloadInt("currentQuantity");
                int threshold = event.payloadInt("threshold");
                yield new EmailContent(
                        adminEmail,
                        "[Alerta] Stock bajo: " + sku + " — Arka",
                        "Alerta de inventario:\n\nSKU: " + sku + "\nStock actual: " + qty
                                + " unidades\nUmbral configurado: " + threshold
                                + " unidades\n\nPor favor, gestiona el reabastecimiento.\n\nSistema Arka");
            }
            default -> null;
        };
    }

    private record EmailContent(String to, String subject, String body) {}
}
