package com.arka.model.cart.gateways;

import com.arka.model.event.CartAbandonedEvent;
import reactor.core.publisher.Mono;

public interface CartEventPublisher {

    Mono<Void> publishCartAbandoned(CartAbandonedEvent event);
}
