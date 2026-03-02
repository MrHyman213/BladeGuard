package com.knifecerts.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entity для хранения уникальных названий моделей ножей.
 * Используется для нормализации данных и поиска по альтернативным моделям.
 */
@Entity
@Table(name = "knife_models")
public class KnifeModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "knifeModel")
    private Set<SubmissionModel> submissionModels = new HashSet<>();
    
    public KnifeModel() {
        this.createdAt = LocalDateTime.now();
    }
    
    public KnifeModel(String name) {
        this();
        this.name = name;
        this.normalizedName = normalizeName(name);
    }
    
    /**
     * Нормализует название для поиска: приводит к нижнему регистру,
     * убирает лишние пробелы.
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.normalizedName = normalizeName(name);
    }
    
    public String getNormalizedName() {
        return normalizedName;
    }
    
    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Set<SubmissionModel> getSubmissionModels() {
        return submissionModels;
    }
    
    public void setSubmissionModels(Set<SubmissionModel> submissionModels) {
        this.submissionModels = submissionModels;
    }
}
