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
@Table(name = "photo_history")
public class PhotoHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    @Column(name = "old_path")
    private String oldPath;
    
    @Column(name = "new_path", nullable = false)
    private String newPath;
    
    @Column(name = "replaced_by", nullable = false)
    private Long replacedBy;
    
    @Column(name = "replaced_at", nullable = false)
    private LocalDateTime replacedAt;
    
    @Column(name = "reason")
    private String reason;
    
    public PhotoHistory() {
        this.replacedAt = LocalDateTime.now();
    }
    
    public PhotoHistory(Submission submission, String oldPath, String newPath, Long replacedBy, String reason) {
        this();
        this.submission = submission;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.replacedBy = replacedBy;
        this.reason = reason;
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
    
    public String getOldPath() {
        return oldPath;
    }
    
    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }
    
    public String getNewPath() {
        return newPath;
    }
    
    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }
    
    public Long getReplacedBy() {
        return replacedBy;
    }
    
    public void setReplacedBy(Long replacedBy) {
        this.replacedBy = replacedBy;
    }
    
    public LocalDateTime getReplacedAt() {
        return replacedAt;
    }
    
    public void setReplacedAt(LocalDateTime replacedAt) {
        this.replacedAt = replacedAt;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
