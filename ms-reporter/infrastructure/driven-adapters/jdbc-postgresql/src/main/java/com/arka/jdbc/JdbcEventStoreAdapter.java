package com.arka.jdbc;

import com.arka.model.eventstore.EventStoreEntry;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcEventStoreAdapter implements EventStoreRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JsonConverter jsonConverter;

    @Override
    public void save(EventStoreEntry entry) {
        String sql = """
                INSERT INTO event_store (id, event_id, event_type, source, aggregate_id, correlation_id, payload, timestamp)
                VALUES (:id, :eventId, :eventType, :source, :aggregateId, :correlationId, CAST(:payload AS jsonb), :timestamp)
                ON CONFLICT (event_id, timestamp) DO NOTHING
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("eventId", entry.eventId())
                .addValue("eventType", entry.eventType())
                .addValue("source", entry.source())
                .addValue("aggregateId", entry.aggregateId())
                .addValue("correlationId", entry.correlationId())
                .addValue("payload", jsonConverter.toJson(entry.payload()))
                .addValue("timestamp", Timestamp.from(entry.timestamp()));

        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<EventStoreEntry> findByCorrelationId(UUID correlationId) {
        String sql = """
                SELECT id, event_id, event_type, source, aggregate_id, correlation_id, payload, timestamp
                FROM event_store
                WHERE correlation_id = :correlationId
                ORDER BY timestamp ASC
                """;
        return jdbcTemplate.query(sql, Map.of("correlationId", correlationId), this::mapRow);
    }

    @Override
    public List<EventStoreEntry> findByEventTypeAndTimestampRange(String eventType, Instant from, Instant to) {
        String sql = """
                SELECT id, event_id, event_type, source, aggregate_id, correlation_id, payload, timestamp
                FROM event_store
                WHERE event_type = :eventType AND timestamp >= :from AND timestamp < :to
                ORDER BY timestamp ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventType", eventType)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    @Override
    public long countByEventTypeAndTimestampRange(String eventType, Instant from, Instant to) {
        String sql = """
                SELECT COUNT(*) FROM event_store
                WHERE event_type = :eventType AND timestamp >= :from AND timestamp < :to
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventType", eventType)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public List<EventStoreEntry> findAllOrderedByTimestamp(int offset, int limit) {
        String sql = """
                SELECT id, event_id, event_type, source, aggregate_id, correlation_id, payload, timestamp
                FROM event_store
                ORDER BY timestamp ASC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    private EventStoreEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID correlationId = rs.getObject("correlation_id") != null
                ? UUID.fromString(rs.getString("correlation_id"))
                : null;
        return EventStoreEntry.builder()
                .id(UUID.fromString(rs.getString("id")))
                .eventId(UUID.fromString(rs.getString("event_id")))
                .eventType(rs.getString("event_type"))
                .source(rs.getString("source"))
                .aggregateId(rs.getString("aggregate_id"))
                .correlationId(correlationId)
                .payload(jsonConverter.fromJson(rs.getString("payload")))
                .timestamp(rs.getTimestamp("timestamp").toInstant())
                .build();
    }
}
