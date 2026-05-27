package com.arka.usecase.reportgeneration;

import java.nio.file.Path;

public record ReportFileResult(
        Path filePath,
        String contentType,
        String extension
) {
}
