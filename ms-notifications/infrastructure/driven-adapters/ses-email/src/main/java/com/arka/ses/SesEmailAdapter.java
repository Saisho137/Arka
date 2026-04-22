package com.arka.ses;

import com.arka.model.notification.gateways.EmailSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Adapter that sends emails via Amazon SES.
 * Uses the synchronous AWS SDK wrapped in Schedulers.boundedElastic() — the approved
 * pattern for blocking SDK calls in reactive services (per project architecture guidelines).
 * In local/dev, points to LocalStack at http://localhost:4566.
 */
@Slf4j
@Component
public class SesEmailAdapter implements EmailSenderPort {

    private final SesClient sesClient;
    private final String fromAddress;

    public SesEmailAdapter(SesClient sesClient,
                           @Qualifier("sesFromAddress") String fromAddress) {
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public Mono<Void> send(String to, String subject, String body) {
        return Mono.fromCallable(() -> {
                    SendEmailRequest request = SendEmailRequest.builder()
                            .source(fromAddress)
                            .destination(Destination.builder().toAddresses(to).build())
                            .message(Message.builder()
                                    .subject(Content.builder().data(subject).charset("UTF-8").build())
                                    .body(Body.builder()
                                            .text(Content.builder().data(body).charset("UTF-8").build())
                                            .build())
                                    .build())
                            .build();
                    return sesClient.sendEmail(request);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(r -> log.debug("SES email sent to={} messageId={}", to, r.messageId()))
                .then();
    }
}
