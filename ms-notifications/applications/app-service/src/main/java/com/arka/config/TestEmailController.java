package com.arka.config;

import com.arka.model.notification.gateways.EmailSenderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestEmailController {

    private final EmailSenderPort emailSender;

    @PostMapping("/send-email")
    public Mono<String> sendTestEmail(  
            @RequestParam(value = "to") String to,
            @RequestParam(value = "subject", defaultValue = "Test Email — Arka") String subject,
            @RequestParam(value = "body", defaultValue = "Este es un correo de prueba desde ms-notifications.") String body) {
        return emailSender.send(to, subject, body)
                .thenReturn("Email sent successfully to " + to);
    }
}
