package com.arka.config;

import com.arka.model.notification.gateways.EmailSenderPort;
import com.arka.model.notification.gateways.NotificationHistoryRepository;
import com.arka.usecase.notification.ProcessNotificationUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCasesConfig {

    @Bean
    public ProcessNotificationUseCase processNotificationUseCase(
            NotificationHistoryRepository historyRepository,
            EmailSenderPort emailSender,
            @Value("${notification.from-address:noreply@arka.com}") String fromAddress,
            @Value("${notification.admin-email:admin@arka.com}") String adminEmail) {
        return new ProcessNotificationUseCase(historyRepository, emailSender, fromAddress, adminEmail);
    }
}


