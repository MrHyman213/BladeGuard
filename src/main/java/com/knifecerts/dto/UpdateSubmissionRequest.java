package com.knifecerts.dto;

import java.util.List;

/**
 * DTO для запроса обновления заявки.
 */
public class UpdateSubmissionRequest {
    
    private String name;
    private String brand;
    private String indexCode;
    private List<String> alternativeModels;
    
    public UpdateSubmissionRequest() {
    }
    
    // Геттеры и сеттеры
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBrand() {
        return brand;
    }
    
    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    public String getIndexCode() {
        return indexCode;
    }
    
    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }
    
    public List<String> getAlternativeModels() {
        return alternativeModels;
    }
    
    public void setAlternativeModels(List<String> alternativeModels) {
        this.alternativeModels = alternativeModels;
    }
}
