package com.arka.s3.config;

import com.arka.s3.config.model.S3ConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Profile({"dev", "cer", "pdn"})
    @Bean
    public S3Client s3Client(S3ConnectionProperties s3Properties, MetricPublisher publisher) {
        return S3Client.builder()
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .region(Region.of(s3Properties.region()))
                .build();
    }

    @Profile({"dev", "cer", "pdn"})
    @Bean
    public S3Presigner s3Presigner(S3ConnectionProperties s3Properties) {
        return S3Presigner.builder()
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .region(Region.of(s3Properties.region()))
                .build();
    }

    @Profile({"local", "docker"})
    @Bean
    public S3Client localS3Client(S3ConnectionProperties s3Properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(s3Properties.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.of(s3Properties.region()))
                .forcePathStyle(true)
                .build();
    }

    @Profile({"local", "docker"})
    @Bean
    public S3Presigner localS3Presigner(S3ConnectionProperties s3Properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(s3Properties.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.of(s3Properties.region()))
                .build();
    }
}

