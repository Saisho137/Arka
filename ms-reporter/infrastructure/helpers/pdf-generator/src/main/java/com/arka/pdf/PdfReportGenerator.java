package com.arka.pdf;

import com.arka.model.report.ReportMetadata;
import com.arka.model.sales.SalesSummary;
import com.arka.usecase.reportgeneration.ReportFileGenerator;
import com.arka.usecase.reportgeneration.ReportFileResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class PdfReportGenerator implements ReportFileGenerator {

    @Override
    public ReportFileResult generate(String reportType, List<SalesSummary> data, ReportMetadata metadata) {
        if (!"PDF".equalsIgnoreCase(reportType)) {
            return null; // Not handled by this generator
        }

        try {
            Path tempFile = Files.createTempFile("report-" + metadata.id(), ".pdf.gz");

            try (PDDocument document = new PDDocument()) {
                addTitlePage(document, metadata);
                addDataPages(document, data);

                // Write PDF to temp, then GZIP
                Path rawPdf = Files.createTempFile("report-raw-", ".pdf");
                document.save(rawPdf.toFile());

                try (InputStream is = new BufferedInputStream(Files.newInputStream(rawPdf));
                     OutputStream os = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                    is.transferTo(os);
                }
                Files.deleteIfExists(rawPdf);
            }

            log.info("PDF report generated: file={}, rows={}", tempFile, data.size());
            return new ReportFileResult(tempFile, "application/gzip", ".pdf.gz");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF report", e);
        }
    }

    private void addTitlePage(PDDocument document, ReportMetadata metadata) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
            cs.newLineAtOffset(50, 750);
            cs.showText("Sales Report - " + metadata.reportType());
            cs.endText();

            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(50, 720);
            cs.showText("Period: " + metadata.startDate() + " to " + metadata.endDate());
            cs.endText();

            cs.beginText();
            cs.newLineAtOffset(50, 700);
            cs.showText("Generated at: " + metadata.requestedAt());
            cs.endText();
        }
    }

    private void addDataPages(PDDocument document, List<SalesSummary> data) throws IOException {
        int rowsPerPage = 40;
        int currentRow = 0;

        while (currentRow < data.size()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = 780;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8);
                cs.beginText();
                cs.newLineAtOffset(50, y);
                cs.showText(String.format("%-15s %-12s %8s %8s %12s %10s", "SKU", "Week", "Orders", "Qty", "Revenue", "AOV"));
                cs.endText();
                y -= 15;

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                int pageEnd = Math.min(currentRow + rowsPerPage, data.size());
                for (int i = currentRow; i < pageEnd; i++) {
                    SalesSummary row = data.get(i);
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    String sku = row.sku().length() > 14 ? row.sku().substring(0, 14) : row.sku();
                    cs.showText(String.format("%-15s %-12s %8d %8d %12s %10s",
                            sku, row.weekStartDate(), row.totalOrders(), row.totalQuantity(),
                            row.totalRevenue().toPlainString(), row.averageOrderValue().toPlainString()));
                    cs.endText();
                    y -= 12;
                }
            }
            currentRow += rowsPerPage;
        }
    }
}
