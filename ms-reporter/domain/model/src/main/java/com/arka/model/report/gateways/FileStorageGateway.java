package com.arka.model.report.gateways;

import java.nio.file.Path;

public interface FileStorageGateway {

    String upload(String key, Path filePath, String contentType);

    String generatePresignedUrl(String key);
}
