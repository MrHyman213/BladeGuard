package com.knifecerts.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для передачи данных заявки в Web App.
 */
public class SubmissionDto {
    
    private Long id;
    private String modelName;
    private String description;
    private List<String> alternativeModels;
    private String photoUrl;
    private String username;
    private LocalDateTime createdAt;
    
    public SubmissionDto() {
    }
    
    // Геттеры и сеттеры
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public String getPhotoUrl() {
        return photoUrl;
    }
    
    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
