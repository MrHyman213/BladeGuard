package com.knifecerts.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity для связи между заявками и моделями ножей (junction table).
 * Реализует отношение многие-ко-многим между Submission и KnifeModel.
 */
@Entity
@Table(name = "submission_models")
public class SubmissionModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    @ManyToOne
    @JoinColumn(name = "knife_model_id", nullable = false)
    private KnifeModel knifeModel;
    
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public SubmissionModel() {
        this.createdAt = LocalDateTime.now();
    }
    
    public SubmissionModel(Submission submission, KnifeModel knifeModel, boolean isPrimary) {
        this();
        this.submission = submission;
        this.knifeModel = knifeModel;
        this.isPrimary = isPrimary;
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Submission getSubmission() {
        return submission;
    }
    
    public void setSubmission(Submission submission) {
        this.submission = submission;
    }
    
    public KnifeModel getKnifeModel() {
        return knifeModel;
    }
    
    public void setKnifeModel(KnifeModel knifeModel) {
        this.knifeModel = knifeModel;
    }
    
    public boolean isPrimary() {
        return isPrimary;
    }
    
    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
