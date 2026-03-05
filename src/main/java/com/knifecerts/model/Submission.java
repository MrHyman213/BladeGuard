package com.knifecerts.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "submissions")
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String username;
    
    @Column
    private String name;
    
    @ManyToOne
    @JoinColumn(name = "brand_id")
    private Brand brand;
    
    @Column(name = "index_code", length = 100)
    private String indexCode;
    
    @Column(name = "photo_path", nullable = false)
    private String photoPath;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;
    
    @Column(name = "moderated_by")
    private Long moderatedBy;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
    
    @Column(name = "alternative_models", columnDefinition = "TEXT")
    private String alternativeModels;
    
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SubmissionAlternative> submissionAlternatives = new HashSet<>();
    
    @ManyToMany
    @JoinTable(
        name = "certificate_alternatives",
        joinColumns = @JoinColumn(name = "certificate_id"),
        inverseJoinColumns = @JoinColumn(name = "alternative_certificate_id")
    )
    private Set<Submission> alternatives = new HashSet<>();
    
    @Transient
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    public Submission() {
    }
    
    public Submission(Long userId, String username, String name, Brand brand, String photoPath) {
        this.userId = userId;
        this.username = username;
        this.name = name;
        this.brand = brand;
        this.photoPath = photoPath;
        this.status = SubmissionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Brand getBrand() {
        return brand;
    }
    
    public void setBrand(Brand brand) {
        this.brand = brand;
    }
    
    public String getIndexCode() {
        return indexCode;
    }
    
    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }
    
    public String getPhotoPath() {
        return photoPath;
    }
    
    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }
    
    public SubmissionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getModeratedAt() {
        return moderatedAt;
    }
    
    public void setModeratedAt(LocalDateTime moderatedAt) {
        this.moderatedAt = moderatedAt;
    }
    
    public Long getModeratedBy() {
        return moderatedBy;
    }
    
    public void setModeratedBy(Long moderatedBy) {
        this.moderatedBy = moderatedBy;
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }
    
    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
    
    public List<String> getAlternativeModelsList() {
        if (alternativeModels == null || alternativeModels.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON_MAPPER.readValue(alternativeModels, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void setAlternativeModelsList(List<String> models) {
        if (models == null || models.isEmpty()) {
            this.alternativeModels = null;
            return;
        }
        try {
            this.alternativeModels = JSON_MAPPER.writeValueAsString(models);
        } catch (Exception e) {
            this.alternativeModels = null;
        }
    }
    
    public String getAlternativeModels() {
        return alternativeModels;
    }
    
    public void setAlternativeModels(String alternativeModels) {
        this.alternativeModels = alternativeModels;
    }
    
    public String getDisplayName() {
        StringBuilder display = new StringBuilder();
        if (brand != null && brand.getName() != null) {
            display.append(brand.getName());
        }
        if (name != null) {
            if (display.length() > 0) display.append(" - ");
            display.append(name);
        }
        if (indexCode != null) {
            if (display.length() > 0) display.append(" - ");
            display.append(indexCode);
        }
        return display.length() > 0 ? display.toString() : "Unknown";
    }
    
    public Set<SubmissionAlternative> getSubmissionAlternatives() {
        return submissionAlternatives;
    }
    
    public void setSubmissionAlternatives(Set<SubmissionAlternative> submissionAlternatives) {
        this.submissionAlternatives = submissionAlternatives;
    }
    
    public Set<Submission> getAlternatives() {
        return alternatives;
    }
    
    public void setAlternatives(Set<Submission> alternatives) {
        this.alternatives = alternatives;
    }
    
    public void addAlternative(Submission alternative) {
        this.alternatives.add(alternative);
        alternative.getAlternatives().add(this);
    }
    
    public void removeAlternative(Submission alternative) {
        this.alternatives.remove(alternative);
        alternative.getAlternatives().remove(this);
    }
}
