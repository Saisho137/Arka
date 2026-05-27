package com.arka.api.handler;

import com.arka.api.dto.EventEntryResponse;
import com.arka.api.dto.EventTraceResponse;
import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.model.eventstore.EventStoreEntry;
import com.arka.usecase.eventtrace.EventTraceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventTraceHandler {

    private final EventTraceUseCase eventTraceUseCase;

    public ResponseEntity<EventTraceResponse> traceByCorrelationId(UUID correlationId, String userRole) {
        validateAdminRole(userRole);

        List<EventStoreEntry> events = eventTraceUseCase.traceByCorrelationId(correlationId);

        List<EventEntryResponse> entries = events.stream()
                .map(e -> EventEntryResponse.builder()
                        .eventId(e.eventId())
                        .eventType(e.eventType())
                        .source(e.source())
                        .aggregateId(e.aggregateId())
                        .timestamp(e.timestamp())
                        .payload(e.payload())
                        .build())
                .toList();

        EventTraceResponse response = EventTraceResponse.builder()
                .correlationId(correlationId)
                .events(entries)
                .build();

        return ResponseEntity.ok(response);
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only ADMIN role can access event traces");
        }
    }
}
