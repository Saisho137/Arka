package com.arka.csv;

import com.arka.model.report.ReportMetadata;
import com.arka.model.sales.SalesSummary;
import com.arka.usecase.reportgeneration.ReportFileGenerator;
import com.arka.usecase.reportgeneration.ReportFileResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class CsvReportGenerator implements ReportFileGenerator {

    private static final String CSV_HEADER = "sku,week_start_date,total_orders,total_quantity,total_revenue,average_order_value,product_name";

    @Override
    public ReportFileResult generate(String reportType, List<SalesSummary> data, ReportMetadata metadata) {
        if (!"CSV".equalsIgnoreCase(reportType)) {
            return null; // Not handled by this generator
        }

        try {
            Path tempFile = Files.createTempFile("report-" + metadata.id(), ".csv.gz");
            try (OutputStream os = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)));
                 Writer writer = new OutputStreamWriter(os)) {

                writer.write(CSV_HEADER);
                writer.write("\n");

                for (SalesSummary row : data) {
                    writer.write(formatRow(row));
                    writer.write("\n");
                }
            }

            log.info("CSV report generated: file={}, rows={}", tempFile, data.size());
            return new ReportFileResult(tempFile, "application/gzip", ".csv.gz");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate CSV report", e);
        }
    }

    private String formatRow(SalesSummary row) {
        return String.join(",",
                escapeCsv(row.sku()),
                row.weekStartDate().toString(),
                String.valueOf(row.totalOrders()),
                String.valueOf(row.totalQuantity()),
                row.totalRevenue().toPlainString(),
                row.averageOrderValue().toPlainString(),
                escapeCsv(row.productName() != null ? row.productName() : ""));
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
