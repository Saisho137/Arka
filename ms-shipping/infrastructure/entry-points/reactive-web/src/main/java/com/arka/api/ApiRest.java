package com.arka.api;

import com.arka.api.dto.PageResponse;
import com.arka.api.dto.ShipmentResponse;
import com.arka.api.dto.UpdateStatusRequest;
import com.arka.api.mapper.ShipmentDtoMapper;
import com.arka.usecase.getshipment.GetShipmentUseCase;
import com.arka.usecase.listshipments.ListShipmentsUseCase;
import com.arka.usecase.retryshipment.RetryShipmentUseCase;
import com.arka.usecase.updateshipmentstatus.UpdateShipmentStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Shipment management and tracking")
public class ApiRest {

    private static final String ROLE_ADMIN = "ADMIN";

    private final GetShipmentUseCase getShipmentUseCase;
    private final ListShipmentsUseCase listShipmentsUseCase;
    private final UpdateShipmentStatusUseCase updateShipmentStatusUseCase;
    private final RetryShipmentUseCase retryShipmentUseCase;

    @GetMapping("/{orderId}")
    @Operation(summary = "Get shipment by order ID")
    @ApiResponse(responseCode = "200", description = "Shipment found")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    public Mono<ShipmentResponse> getShipment(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return getShipmentUseCase.execute(orderId)
                .map(ShipmentDtoMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List shipments with filters (ADMIN only)")
    @ApiResponse(responseCode = "200", description = "Paginated shipment list")
    @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    public Mono<PageResponse<ShipmentResponse>> listShipments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "carrier", required = false) String carrier,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        requireAdmin(userRole);
        return listShipmentsUseCase.execute(status, carrier, page, size)
                .map(ShipmentDtoMapper::toResponse)
                .collectList()
                .map(list -> new PageResponse<>(list, page, size, list.size()));
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Update shipment status (ADMIN only)")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    public Mono<ShipmentResponse> updateStatus(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        requireAdmin(userRole);
        return updateShipmentStatusUseCase.execute(orderId, request.status(), request.actualDeliveryDate())
                .map(ShipmentDtoMapper::toResponse);
    }

    @PostMapping("/retry/{orderId}")
    @Operation(summary = "Retry failed shipment (ADMIN only)")
    @ApiResponse(responseCode = "200", description = "Shipment retried")
    @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    @ApiResponse(responseCode = "409", description = "Shipment not in FAILED status")
    public Mono<ShipmentResponse> retryShipment(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        requireAdmin(userRole);
        return retryShipmentUseCase.execute(orderId)
                .map(ShipmentDtoMapper::toResponse);
    }

    private void requireAdmin(String userRole) {
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires ADMIN role");
        }
    }
}

