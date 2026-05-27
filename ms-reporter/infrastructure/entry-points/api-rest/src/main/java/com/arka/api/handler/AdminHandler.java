package com.arka.api.handler;

import com.arka.api.dto.RebuildReadModelRequest;
import com.arka.api.dto.RebuildResponse;
import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.usecase.readmodelrebuild.ReadModelRebuildUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminHandler {

    private final ReadModelRebuildUseCase readModelRebuildUseCase;

    public ResponseEntity<RebuildResponse> rebuildReadModel(RebuildReadModelRequest request, String userRole) {
        validateAdminRole(userRole);

        UUID jobId = readModelRebuildUseCase.startRebuild(request.readModel());

        // Trigger async rebuild
        readModelRebuildUseCase.executeRebuild(jobId, request.readModel());

        RebuildResponse response = RebuildResponse.builder()
                .jobId(jobId)
                .readModel(request.readModel())
                .status("IN_PROGRESS")
                .build();

        return ResponseEntity.accepted().body(response);
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only ADMIN role can perform admin operations");
        }
    }
}
