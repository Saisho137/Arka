package com.arka.mongo.mapper;

import com.arka.mongo.document.IdempotencyDocument;

import java.time.Instant;
import java.util.UUID;

public final class IdempotencyDocumentMapper {

    private IdempotencyDocumentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static IdempotencyDocument toDocument(UUID idempotencyKey) {
        return IdempotencyDocument.builder()
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .build();
    }
}
