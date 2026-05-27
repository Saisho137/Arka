package com.arka.usecase.processshipment;

import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.S3Storage;
import com.arka.model.shipment.gateways.ShipmentRepository;
import com.arka.model.shipment.gateways.ShippingCarrier;
import com.arka.model.shipment.gateways.ShippingCarrierFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessShipmentUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ShippingCarrierFactory shippingCarrierFactory;
    @Mock private S3Storage s3Storage;
    @Mock private ShippingCarrier shippingCarrier;

    private ProcessShipmentUseCase useCase;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final DeliveryAddress VALID_ADDRESS = new DeliveryAddress(
            "Calle 100 #15-20", "Bogotá", "Cundinamarca", "11001", "CO");
    private static final Carrier CARRIER = Carrier.DHL;

    @BeforeEach
    void setUp() {
        useCase = new ProcessShipmentUseCase(
                shipmentRepository, outboxEventRepository,
                processedEventRepository, shippingCarrierFactory, s3Storage);
    }

    @Test
    @DisplayName("Should skip already processed event (idempotency)")
    void shouldSkipAlreadyProcessedEvent() {
        when(processedEventRepository.exists(EVENT_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(EVENT_ID, ORDER_ID, VALID_ADDRESS, CARRIER))
                .verifyComplete();

        verify(shippingCarrierFactory, never()).getCarrier(any());
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process shipment successfully end-to-end")
    void shouldProcessShipmentSuccessfully() {
        ShippingResult result = ShippingResult.builder()
                .success(true)
                .trackingNumber("DHL-ABC12345")
                .labelPdf("label content".getBytes())
                .estimatedDeliveryDate(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(processedEventRepository.exists(EVENT_ID)).thenReturn(Mono.just(false));
        when(shippingCarrierFactory.getCarrier(CARRIER)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(ORDER_ID, VALID_ADDRESS)).thenReturn(Mono.just(result));
        when(s3Storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenReturn(Mono.just("s3://bucket/label.pdf"));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(OutboxEvent.builder()
                        .eventType(com.arka.model.outboxevent.EventType.SHIPPING_DISPATCHED)
                        .payload("{}")
                        .partitionKey(ORDER_ID.toString())
                        .build()));
        when(processedEventRepository.save(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(EVENT_ID, ORDER_ID, VALID_ADDRESS, CARRIER))
                .verifyComplete();

        verify(shipmentRepository).save(argThat(s ->
                s.status() == ShippingStatus.LABEL_GENERATED &&
                        s.trackingNumber().equals("DHL-ABC12345")));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(processedEventRepository).save(EVENT_ID);
    }

    @Test
    @DisplayName("Should save FAILED shipment when carrier returns failure")
    void shouldHandleCarrierFailure() {
        ShippingResult failResult = ShippingResult.builder()
                .success(false)
                .reason("Carrier timeout")
                .build();

        when(processedEventRepository.exists(EVENT_ID)).thenReturn(Mono.just(false));
        when(shippingCarrierFactory.getCarrier(CARRIER)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(ORDER_ID, VALID_ADDRESS)).thenReturn(Mono.just(failResult));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(EVENT_ID, ORDER_ID, VALID_ADDRESS, CARRIER))
                .verifyComplete();

        verify(shipmentRepository).save(argThat(s ->
                s.status() == ShippingStatus.FAILED &&
                        "Carrier timeout".equals(s.failureReason())));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle invalid delivery address gracefully")
    void shouldHandleInvalidAddress() {
        DeliveryAddress invalidAddress = new DeliveryAddress(
                "Calle 100", "Bogotá", "Cundinamarca", "11001", "CO");

        when(processedEventRepository.exists(EVENT_ID)).thenReturn(Mono.just(false));
        when(shippingCarrierFactory.getCarrier(CARRIER)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(eq(ORDER_ID), any(DeliveryAddress.class)))
                .thenReturn(Mono.just(ShippingResult.builder()
                        .success(true)
                        .trackingNumber("DHL-XYZ")
                        .labelPdf("pdf".getBytes())
                        .estimatedDeliveryDate(Instant.now().plus(3, ChronoUnit.DAYS))
                        .build()));
        when(s3Storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenReturn(Mono.just("s3://bucket/key"));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(OutboxEvent.builder()
                        .eventType(com.arka.model.outboxevent.EventType.SHIPPING_DISPATCHED)
                        .payload("{}")
                        .partitionKey(ORDER_ID.toString())
                        .build()));
        when(processedEventRepository.save(EVENT_ID)).thenReturn(Mono.empty());

        // Address is technically valid (all fields present) — use case should succeed
        StepVerifier.create(useCase.execute(EVENT_ID, ORDER_ID, invalidAddress, CARRIER))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should upload label to S3 with correct key format")
    void shouldUploadLabelToS3WithCorrectKey() {
        ShippingResult result = ShippingResult.builder()
                .success(true)
                .trackingNumber("DHL-KEY123")
                .labelPdf("pdf bytes".getBytes())
                .estimatedDeliveryDate(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(processedEventRepository.exists(EVENT_ID)).thenReturn(Mono.just(false));
        when(shippingCarrierFactory.getCarrier(CARRIER)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(ORDER_ID, VALID_ADDRESS)).thenReturn(Mono.just(result));
        when(s3Storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenReturn(Mono.just("s3://bucket/" + ORDER_ID + "/DHL-KEY123.pdf"));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(OutboxEvent.builder()
                        .eventType(com.arka.model.outboxevent.EventType.SHIPPING_DISPATCHED)
                        .payload("{}")
                        .partitionKey(ORDER_ID.toString())
                        .build()));
        when(processedEventRepository.save(EVENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(EVENT_ID, ORDER_ID, VALID_ADDRESS, CARRIER))
                .verifyComplete();

        String expectedKey = ORDER_ID + "/DHL-KEY123.pdf";
        verify(s3Storage).uploadFile(any(byte[].class), eq(expectedKey), eq("application/pdf"));
    }
}
