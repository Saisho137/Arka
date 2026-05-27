package com.arka.api.controller;

import com.arka.api.dto.EventTraceResponse;
import com.arka.api.handler.EventTraceHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Events")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventTraceController {

    private final EventTraceHandler eventTraceHandler;

    @Operation(summary = "Trace events by correlation ID")
    @ApiResponse(responseCode = "200", description = "Event trace results")
    @GetMapping("/trace/{correlationId}")
    public ResponseEntity<EventTraceResponse> traceByCorrelationId(
            @PathVariable("correlationId") UUID correlationId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return eventTraceHandler.traceByCorrelationId(correlationId, userRole);
    }
}
