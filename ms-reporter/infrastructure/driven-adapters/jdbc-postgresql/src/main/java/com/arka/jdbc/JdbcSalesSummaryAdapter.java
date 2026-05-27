package com.arka.jdbc;

import com.arka.model.sales.SalesSummary;
import com.arka.model.sales.gateways.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class JdbcSalesSummaryAdapter implements SalesSummaryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void upsert(SalesSummary summary) {
        String sql = """
                INSERT INTO sales_summary (sku, week_start_date, total_orders, total_quantity, total_revenue, average_order_value, product_name, last_updated_at)
                VALUES (:sku, :weekStartDate, :totalOrders, :totalQuantity, :totalRevenue, :averageOrderValue, :productName, :lastUpdatedAt)
                ON CONFLICT (sku, week_start_date) DO UPDATE SET
                    total_orders = sales_summary.total_orders + EXCLUDED.total_orders,
                    total_quantity = sales_summary.total_quantity + EXCLUDED.total_quantity,
                    total_revenue = sales_summary.total_revenue + EXCLUDED.total_revenue,
                    average_order_value = (sales_summary.total_revenue + EXCLUDED.total_revenue) / NULLIF(sales_summary.total_orders + EXCLUDED.total_orders, 0),
                    product_name = COALESCE(EXCLUDED.product_name, sales_summary.product_name),
                    last_updated_at = EXCLUDED.last_updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sku", summary.sku())
                .addValue("weekStartDate", summary.weekStartDate())
                .addValue("totalOrders", summary.totalOrders())
                .addValue("totalQuantity", summary.totalQuantity())
                .addValue("totalRevenue", summary.totalRevenue())
                .addValue("averageOrderValue", summary.averageOrderValue())
                .addValue("productName", summary.productName())
                .addValue("lastUpdatedAt", Timestamp.from(summary.lastUpdatedAt()));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void decrementSales(String sku, LocalDate weekStartDate, int quantity, BigDecimal amount) {
        String sql = """
                UPDATE sales_summary SET
                    total_orders = GREATEST(total_orders - 1, 0),
                    total_quantity = GREATEST(total_quantity - :quantity, 0),
                    total_revenue = GREATEST(total_revenue - :amount, 0),
                    average_order_value = CASE WHEN (total_orders - 1) > 0
                        THEN (total_revenue - :amount) / (total_orders - 1)
                        ELSE 0 END,
                    last_updated_at = :now
                WHERE sku = :sku AND week_start_date = :weekStartDate
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sku", sku)
                .addValue("weekStartDate", weekStartDate)
                .addValue("quantity", quantity)
                .addValue("amount", amount)
                .addValue("now", Timestamp.from(Instant.now()));
        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<SalesSummary> findByWeekRange(LocalDate from, LocalDate to) {
        String sql = """
                SELECT sku, week_start_date, total_orders, total_quantity, total_revenue, average_order_value, product_name, last_updated_at
                FROM sales_summary
                WHERE week_start_date >= :from AND week_start_date <= :to
                ORDER BY week_start_date, sku
                """;
        return jdbcTemplate.query(sql, Map.of("from", from, "to", to), this::mapRow);
    }

    @Override
    public List<SalesSummary> findTopSellingByWeekRange(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT sku, MIN(week_start_date) as week_start_date,
                       SUM(total_orders) as total_orders, SUM(total_quantity) as total_quantity,
                       SUM(total_revenue) as total_revenue,
                       CASE WHEN SUM(total_orders) > 0 THEN SUM(total_revenue) / SUM(total_orders) ELSE 0 END as average_order_value,
                       MAX(product_name) as product_name, MAX(last_updated_at) as last_updated_at
                FROM sales_summary
                WHERE week_start_date >= :from AND week_start_date <= :to
                GROUP BY sku
                ORDER BY SUM(total_quantity) DESC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("limit", limit);
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    @Override
    public void truncate() {
        jdbcTemplate.getJdbcTemplate().execute("TRUNCATE TABLE sales_summary");
    }

    private SalesSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SalesSummary.builder()
                .sku(rs.getString("sku"))
                .weekStartDate(rs.getDate("week_start_date").toLocalDate())
                .totalOrders(rs.getInt("total_orders"))
                .totalQuantity(rs.getInt("total_quantity"))
                .totalRevenue(rs.getBigDecimal("total_revenue"))
                .averageOrderValue(rs.getBigDecimal("average_order_value"))
                .productName(rs.getString("product_name"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at").toInstant())
                .build();
    }
}
