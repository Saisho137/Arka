package com.arka.r2dbc.config;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxStatus;
import com.arka.model.stockmovement.MovementType;
import com.arka.model.stockreservation.ReservationStatus;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.spi.Option;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.EnumWriteSupport;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.util.List;

@Configuration
public class R2dbcEnumConfig {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer enumCodecCustomizer() {
        return builder -> builder.option(
                Option.valueOf("extensions"),
                List.of(EnumCodec.builder()
                        .withEnum("movement_type", MovementType.class)
                        .withEnum("reservation_status", ReservationStatus.class)
                        .withEnum("outbox_status", OutboxStatus.class)
                        .withEnum("event_type", EventType.class)
                        .build()));
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, List.of(
                new MovementTypeWritingConverter(),
                new ReservationStatusWritingConverter(),
                new OutboxStatusWritingConverter(),
                new EventTypeWritingConverter()));
    }

    @WritingConverter
    static class MovementTypeWritingConverter extends EnumWriteSupport<MovementType> {}

    @WritingConverter
    static class ReservationStatusWritingConverter extends EnumWriteSupport<ReservationStatus> {}

    @WritingConverter
    static class OutboxStatusWritingConverter extends EnumWriteSupport<OutboxStatus> {}

    @WritingConverter
    static class EventTypeWritingConverter extends EnumWriteSupport<EventType> {}
}
