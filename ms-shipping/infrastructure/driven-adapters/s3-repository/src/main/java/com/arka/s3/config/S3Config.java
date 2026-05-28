package com.arka.s3.config;

import com.arka.s3.config.model.S3ConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;


import java.net.URI;

@Configuration
public class S3Config {

    @Profile({"dev", "cer", "pdn"})
    @Bean
    public S3AsyncClient s3AsyncClient(S3ConnectionProperties s3Properties, MetricPublisher publisher) {
        return S3AsyncClient.builder()
                .region(Region.of(s3Properties.region()))
                // or any other provider suitable for your environment
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .build();
    }

    @Profile({"local", "docker"})
    @Bean
    public S3AsyncClient localS3AsyncClient(S3ConnectionProperties s3Properties,
                                            MetricPublisher publisher) {
        return S3AsyncClient.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .endpointOverride(URI.create(s3Properties.endpoint()))
                .forcePathStyle(true)
                .build();
    }

}
