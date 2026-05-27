package com.arka.model.eventstore.gateways;

import com.arka.model.eventstore.DomainEventEnvelope;

public interface EventPublisherGateway {

    void publish(DomainEventEnvelope envelope, String partitionKey);
}
