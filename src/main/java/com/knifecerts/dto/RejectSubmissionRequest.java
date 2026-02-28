package com.knifecerts.dto;

/**
 * DTO для запроса отклонения заявки с причиной.
 */
public class RejectSubmissionRequest {
    
    private String reason;
    
    public RejectSubmissionRequest() {
    }
    
    // Геттеры и сеттеры
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
