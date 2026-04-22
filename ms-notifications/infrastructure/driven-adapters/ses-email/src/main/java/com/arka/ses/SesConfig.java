package com.arka.ses;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.URI;

@Configuration
public class SesConfig {

    @Bean
    public SesClient sesClient(
            @Value("${aws.ses.endpoint:http://localhost:4566}") String sesEndpoint,
            @Value("${aws.ses.region:us-east-1}") String awsRegion,
            @Value("${aws.ses.access-key:test}") String accessKey,
            @Value("${aws.ses.secret-key:test}") String secretKey) {
        return SesClient.builder()
                .endpointOverride(URI.create(sesEndpoint))
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean(name = "sesFromAddress")
    public String sesFromAddress(
            @Value("${notification.from-address:noreply@arka.com}") String fromAddress) {
        return fromAddress;
    }
}

