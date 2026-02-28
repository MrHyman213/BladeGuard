package com.knifecerts.dto;

import java.util.List;

/**
 * DTO для запроса обновления заявки.
 */
public class UpdateSubmissionRequest {
    
    private String modelName;
    private String description;
    private List<String> alternativeModels;
    
    public UpdateSubmissionRequest() {
    }
    
    // Геттеры и сеттеры
    
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getAlternativeModels() {
        return alternativeModels;
    }
    
    public void setAlternativeModels(List<String> alternativeModels) {
        this.alternativeModels = alternativeModels;
    }
}
