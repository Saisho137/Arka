package com.arka.jdbc;

import com.arka.model.alert.AlertStatus;
import com.arka.model.alert.StockAlert;
import com.arka.model.alert.gateways.StockAlertRepository;
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
public class JdbcStockAlertAdapter implements StockAlertRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(StockAlert alert) {
        String sql = """
                INSERT INTO stock_alerts (id, sku, product_name, current_stock, daily_rate, days_until_out, alert_status, created_at)
                VALUES (:id, :sku, :productName, :currentStock, :dailyRate, :daysUntilOut, :alertStatus, :createdAt)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", alert.id())
                .addValue("sku", alert.sku())
                .addValue("productName", alert.productName())
                .addValue("currentStock", alert.currentStock())
                .addValue("dailyRate", alert.dailyRate())
                .addValue("daysUntilOut", alert.daysUntilOut())
                .addValue("alertStatus", alert.alertStatus().name())
                .addValue("createdAt", Timestamp.from(alert.createdAt()));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public Optional<StockAlert> findActiveAlertBySku(String sku) {
        String sql = """
                SELECT id, sku, product_name, current_stock, daily_rate, days_until_out, alert_status, created_at, resolved_at
                FROM stock_alerts
                WHERE sku = :sku AND alert_status = 'ACTIVE'
                ORDER BY created_at DESC LIMIT 1
                """;
        List<StockAlert> results = jdbcTemplate.query(sql, Map.of("sku", sku), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public void resolveAlert(String sku) {
        String sql = """
                UPDATE stock_alerts SET alert_status = 'RESOLVED', resolved_at = :now
                WHERE sku = :sku AND alert_status = 'ACTIVE'
                """;
        jdbcTemplate.update(sql, Map.of("sku", sku, "now", Timestamp.from(Instant.now())));
    }

    private StockAlert mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return StockAlert.builder()
                .id(UUID.fromString(rs.getString("id")))
                .sku(rs.getString("sku"))
                .productName(rs.getString("product_name"))
                .currentStock(rs.getInt("current_stock"))
                .dailyRate(rs.getBigDecimal("daily_rate"))
                .daysUntilOut(rs.getInt("days_until_out"))
                .alertStatus(AlertStatus.valueOf(rs.getString("alert_status")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .resolvedAt(resolvedAt != null ? resolvedAt.toInstant() : null)
                .build();
    }
}
