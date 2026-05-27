package com.arka.s3.adapter;

import com.arka.model.report.gateways.FileStorageGateway;
import com.arka.s3.config.model.S3ConnectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Repository
@RequiredArgsConstructor
public class S3Adapter implements FileStorageGateway {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3ConnectionProperties properties;

    @Override
    public String upload(String key, Path filePath, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(filePath));
        log.info("File uploaded to S3: bucket={}, key={}", properties.bucketName(), key);
        return key;
    }

    @Override
    public String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}

