package com.arka.model.commons.exception;

public class ReportNotFoundException extends DomainException {

    public ReportNotFoundException(String reportId) {
        super("Report not found: " + reportId);
    }

    @Override
    public int getHttpStatus() {
        return 404;
    }

    @Override
    public String getCode() {
        return "REPORT_NOT_FOUND";
    }
}
