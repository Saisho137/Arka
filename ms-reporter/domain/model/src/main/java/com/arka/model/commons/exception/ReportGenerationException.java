package com.arka.model.commons.exception;

public class ReportGenerationException extends DomainException {

    public ReportGenerationException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 500;
    }

    @Override
    public String getCode() {
        return "REPORT_GENERATION_FAILED";
    }
}
