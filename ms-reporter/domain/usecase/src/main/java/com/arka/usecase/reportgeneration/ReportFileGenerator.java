package com.arka.usecase.reportgeneration;

import com.arka.model.report.ReportMetadata;
import com.arka.model.sales.SalesSummary;

import java.util.List;

public interface ReportFileGenerator {

    ReportFileResult generate(String reportType, List<SalesSummary> data, ReportMetadata metadata);
}
