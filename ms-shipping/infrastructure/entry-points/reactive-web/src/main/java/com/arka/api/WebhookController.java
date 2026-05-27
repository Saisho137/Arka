package com.arka.api;

import com.arka.api.dto.ShipmentResponse;
import com.arka.api.dto.WebhookPayload;
import com.arka.api.mapper.ShipmentDtoMapper;
import com.arka.usecase.processwebhook.ProcessWebhookUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Carrier tracking webhook endpoints")
public class WebhookController {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ProcessWebhookUseCase processWebhookUseCase;
    private final ObjectMapper objectMapper;
    private final WebhookSecretProvider webhookSecretProvider;

    @PostMapping(value = "/{carrier}/tracking", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive carrier tracking webhook")
    @ApiResponse(responseCode = "200", description = "Webhook processed")
    @ApiResponse(responseCode = "401", description = "Invalid webhook signature")
    public Mono<ShipmentResponse> trackingWebhook(
            @PathVariable("carrier") String carrier,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        validateSignature(carrier, rawBody, signature);
        WebhookPayload payload = deserialize(rawBody);
        return processWebhookUseCase.execute(payload.trackingNumber(), payload.status(), payload.deliveryDate())
                .map(ShipmentDtoMapper::toResponse);
    }

    private WebhookPayload deserialize(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook payload");
        }
    }

    private void validateSignature(String carrier, String rawBody, String signature) {
        String secret = webhookSecretProvider.getSecret(carrier.toLowerCase());
        if (secret == null || secret.isBlank()) {
            log.warn("No webhook secret configured for carrier={}", carrier);
            return;
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Webhook-Signature header");
        }
        String computed = computeHmac(secret, rawBody);
        if (!computed.equalsIgnoreCase(signature)) {
            log.warn("Invalid webhook signature for carrier={}", carrier);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
    }

    private String computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }
}
