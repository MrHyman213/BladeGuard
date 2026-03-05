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

@Entity
@Table(name = "pending_alternatives")
public class PendingAlternative {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    @Column(name = "brand_name", nullable = false)
    private String brandName;
    
    @Column(name = "knife_name", nullable = false)
    private String knifeName;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public PendingAlternative() {
        this.createdAt = LocalDateTime.now();
    }
    
    public PendingAlternative(Submission submission, String brandName, String knifeName) {
        this();
        this.submission = submission;
        this.brandName = brandName;
        this.knifeName = knifeName;
    }
    
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
    
    public String getBrandName() {
        return brandName;
    }
    
    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
    
    public String getKnifeName() {
        return knifeName;
    }
    
    public void setKnifeName(String knifeName) {
        this.knifeName = knifeName;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
