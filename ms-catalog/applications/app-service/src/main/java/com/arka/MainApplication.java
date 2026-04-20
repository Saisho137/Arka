package com.arka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MainApplication {

    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

    @Bean
    public CommandLineRunner initLog(Environment env) {
        return args -> {
            String appName = env.getProperty("spring.application.name");
            String port = env.getProperty("server.port");
            String profile = env.getProperty("spring.profiles.active");

            log.info("=".repeat(80));
            log.info("Application '{}' started successfully", appName);
            log.info("Server running on port: {}", port);
            log.info("Active profile: {}", profile);
            log.info("Swagger UI: http://localhost:{}/swagger-ui.html", port);
            log.info("API Docs: http://localhost:{}/api-docs", port);
            log.info("=".repeat(80));
        };
    }
}
