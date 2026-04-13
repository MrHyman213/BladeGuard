package com.knifecerts.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "submissions_buffer")
public class SubmissionBuffer implements Moderatable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "idx", length = 100)
    private String index;

    @Column(name = "photo_path", nullable = false, length = 512)
    private String photoPath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "alternatives", columnDefinition = "TEXT")
    private String alternatives;

    public SubmissionBuffer() {
        this.createdAt = LocalDateTime.now();
    }

    public SubmissionBuffer(Long userId, String username, String modelName, String brandName, String photoPath) {
        this();
        this.userId = userId;
        this.username = username;
        this.modelName = modelName;
        this.brandName = brandName;
        this.photoPath = photoPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(String alternatives) {
        this.alternatives = alternatives;
    }

    public String getDisplayName() {
        StringBuilder display = new StringBuilder();
        if (brandName != null) {
            display.append(brandName);
        }
        if (modelName != null) {
            if (!display.isEmpty()) display.append(" - ");
            display.append(modelName);
        }
        if (index != null) {
            if (!display.isEmpty()) display.append(" - ");
            display.append(index);
        }
        return !display.isEmpty() ? display.toString() : "Unknown";
    }

    @Override
    public String getIndexValue() {
        return getIndex();
    }

    @Override
    public String getAlternativesText(String separator) {
        return getAlternatives() != null ? getAlternatives() : "";
    }
}