package com.arka.s3.config.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record S3ConnectionProperties(
        String endpoint,
        String region,
        String bucket) {

    public String bucketName() {
        return bucket;
    }
}

