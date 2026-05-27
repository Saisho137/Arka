package com.arka.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookSecretProvider {

    @Value("${shipping.carriers.dhl.webhook-secret:}")
    private String dhlWebhookSecret;

    @Value("${shipping.carriers.fedex.webhook-secret:}")
    private String fedexWebhookSecret;

    @Value("${shipping.carriers.legacy.webhook-secret:}")
    private String legacyWebhookSecret;

    public String getSecret(String carrier) {
        return switch (carrier) {
            case "dhl" -> dhlWebhookSecret;
            case "fedex" -> fedexWebhookSecret;
            case "legacy" -> legacyWebhookSecret;
            default -> null;
        };
    }
}
