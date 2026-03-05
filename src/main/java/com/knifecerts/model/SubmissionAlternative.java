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
@Table(name = "submission_alternatives")
public class SubmissionAlternative {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    @ManyToOne
    @JoinColumn(name = "alternative_id", nullable = false)
    private Submission alternative;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    public SubmissionAlternative() {
        this.createdAt = LocalDateTime.now();
    }
    
    public SubmissionAlternative(Submission submission, Submission alternative) {
        this();
        this.submission = submission;
        this.alternative = alternative;
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
    
    public Submission getAlternative() {
        return alternative;
    }
    
    public void setAlternative(Submission alternative) {
        this.alternative = alternative;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
