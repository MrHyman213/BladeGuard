package com.knifecerts.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "submissions_buffer")
public class SubmissionBuffer {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "buffer_alternatives", joinColumns = @JoinColumn(name = "submission_id"))
    private List<BufferAlternative> alternatives = new ArrayList<>();

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

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

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

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public List<BufferAlternative> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<BufferAlternative> alternatives) {
        this.alternatives = alternatives;
    }

    public void addAlternative(BufferAlternative alternative) {
        this.alternatives.add(alternative);
    }

    public void removeAlternative(BufferAlternative alternative) {
        this.alternatives.remove(alternative);
    }

    public String getDisplayName() {
        StringBuilder display = new StringBuilder();
        if (brandName != null) {
            display.append(brandName);
        }
        if (modelName != null) {
            if (display.length() > 0) display.append(" - ");
            display.append(modelName);
        }
        if (index != null) {
            if (display.length() > 0) display.append(" - ");
            display.append(index);
        }
        return display.length() > 0 ? display.toString() : "Unknown";
    }
}