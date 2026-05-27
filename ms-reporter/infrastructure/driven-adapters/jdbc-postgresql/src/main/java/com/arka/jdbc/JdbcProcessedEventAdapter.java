package com.arka.jdbc;

import com.arka.model.eventstore.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcProcessedEventAdapter implements ProcessedEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public boolean exists(UUID eventId) {
        String sql = "SELECT COUNT(*) FROM processed_events WHERE event_id = :eventId";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("eventId", eventId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void save(UUID eventId) {
        String sql = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, :processedAt) ON CONFLICT DO NOTHING";
        jdbcTemplate.update(sql, Map.of("eventId", eventId, "processedAt", Timestamp.from(Instant.now())));
    }
}
