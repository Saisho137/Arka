package com.arka.api.controller;

import com.arka.api.dto.RebuildReadModelRequest;
import com.arka.api.dto.RebuildResponse;
import com.arka.api.handler.AdminHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminHandler adminHandler;

    @Operation(summary = "Rebuild a read model from Event Store")
    @ApiResponse(responseCode = "202", description = "Rebuild started")
    @PostMapping("/rebuild-read-models")
    public ResponseEntity<RebuildResponse> rebuildReadModel(
            @Valid @RequestBody RebuildReadModelRequest request,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return adminHandler.rebuildReadModel(request, userRole);
    }
}
