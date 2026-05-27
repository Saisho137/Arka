package com.arka.model.shipment.gateways;

import reactor.core.publisher.Mono;

public interface S3Storage {

    Mono<String> uploadFile(byte[] content, String key, String contentType);
}
