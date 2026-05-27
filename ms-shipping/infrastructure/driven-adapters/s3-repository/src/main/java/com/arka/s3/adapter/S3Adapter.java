package com.arka.s3.adapter;

import com.arka.model.shipment.gateways.S3Storage;
import com.arka.s3.config.model.S3ConnectionProperties;
import com.arka.s3.operations.S3Operations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class S3Adapter implements S3Storage {

    private final S3Operations s3Operations;
    private final S3ConnectionProperties s3Properties;

    @Override
    public Mono<String> uploadFile(byte[] content, String key, String contentType) {
        return s3Operations.uploadObject(s3Properties.bucketName(), key, content)
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        String url = String.format("s3://%s/%s", s3Properties.bucketName(), key);
                        log.info("Uploaded label to S3: {}", url);
                        return url;
                    }
                    throw new IllegalStateException("S3 upload failed for key: " + key);
                });
    }
}
