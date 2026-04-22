package com.arka.model.notification.gateways;

import reactor.core.publisher.Mono;

public interface EmailSenderPort {

    Mono<Void> send(String to, String subject, String body);
}
