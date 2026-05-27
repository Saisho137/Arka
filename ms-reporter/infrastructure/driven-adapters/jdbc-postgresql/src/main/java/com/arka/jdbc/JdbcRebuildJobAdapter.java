package com.arka.jdbc;

import com.arka.model.rebuild.RebuildJob;
import com.arka.model.rebuild.RebuildStatus;
import com.arka.model.rebuild.gateways.RebuildJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcRebuildJobAdapter implements RebuildJobRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(RebuildJob job) {
        String sql = """
                INSERT INTO rebuild_jobs (id, read_model, status, events_processed, started_at)
                VALUES (:id, :readModel, :status, :eventsProcessed, :startedAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", job.id())
                .addValue("readModel", job.readModel())
                .addValue("status", job.status().name())
                .addValue("eventsProcessed", job.eventsProcessed())
                .addValue("startedAt", Timestamp.from(job.startedAt()));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(UUID id, RebuildStatus status, long eventsProcessed, String errorMessage) {
        String sql = """
                UPDATE rebuild_jobs SET status = :status, events_processed = :eventsProcessed,
                    error_message = :errorMessage, completed_at = :completedAt
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("eventsProcessed", eventsProcessed)
                .addValue("errorMessage", errorMessage)
                .addValue("completedAt", Timestamp.from(Instant.now()));
        jdbcTemplate.update(sql, params);
    }
}
