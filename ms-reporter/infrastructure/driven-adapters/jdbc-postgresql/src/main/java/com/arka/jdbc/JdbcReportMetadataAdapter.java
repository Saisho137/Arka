package com.arka.jdbc;

import com.arka.model.report.ReportMetadata;
import com.arka.model.report.ReportStatus;
import com.arka.model.report.gateways.ReportMetadataRepository;
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
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcReportMetadataAdapter implements ReportMetadataRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(ReportMetadata metadata) {
        String sql = """
                INSERT INTO report_metadata (id, report_type, status, start_date, end_date, requested_by, requested_at)
                VALUES (:id, :reportType, :status, :startDate, :endDate, :requestedBy, :requestedAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", metadata.id())
                .addValue("reportType", metadata.reportType())
                .addValue("status", metadata.status().name())
                .addValue("startDate", metadata.startDate())
                .addValue("endDate", metadata.endDate())
                .addValue("requestedBy", metadata.requestedBy())
                .addValue("requestedAt", Timestamp.from(metadata.requestedAt()));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public Optional<ReportMetadata> findById(UUID id) {
        String sql = """
                SELECT id, report_type, status, start_date, end_date, s3_key, file_size_bytes, requested_by, requested_at, completed_at, error_message
                FROM report_metadata WHERE id = :id
                """;
        List<ReportMetadata> results = jdbcTemplate.query(sql, Map.of("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public void updateStatus(UUID id, ReportStatus status, String s3Key, Long fileSizeBytes, String errorMessage) {
        String sql = """
                UPDATE report_metadata
                SET status = :status, s3_key = :s3Key, file_size_bytes = :fileSizeBytes,
                    error_message = :errorMessage, completed_at = :completedAt
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("s3Key", s3Key)
                .addValue("fileSizeBytes", fileSizeBytes)
                .addValue("errorMessage", errorMessage)
                .addValue("completedAt", Timestamp.from(Instant.now()));
        jdbcTemplate.update(sql, params);
    }

    private ReportMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return ReportMetadata.builder()
                .id(UUID.fromString(rs.getString("id")))
                .reportType(rs.getString("report_type"))
                .status(ReportStatus.valueOf(rs.getString("status")))
                .startDate(rs.getDate("start_date").toLocalDate())
                .endDate(rs.getDate("end_date").toLocalDate())
                .s3Key(rs.getString("s3_key"))
                .fileSizeBytes(rs.getObject("file_size_bytes") != null ? rs.getLong("file_size_bytes") : null)
                .requestedBy(rs.getString("requested_by"))
                .requestedAt(rs.getTimestamp("requested_at").toInstant())
                .completedAt(completedAt != null ? completedAt.toInstant() : null)
                .errorMessage(rs.getString("error_message"))
                .build();
    }
}
