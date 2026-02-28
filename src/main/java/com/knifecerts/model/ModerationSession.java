package com.knifecerts.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity для хранения сессий редактирования заявок модераторами.
 * 
 * Каждая сессия имеет уникальный токен и срок действия.
 * Используется для безопасного доступа к Web App редактору.
 */
@Entity
@Table(name = "moderation_sessions")
public class ModerationSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Уникальный токен сессии (UUID).
     * Используется для доступа к Web App.
     */
    @Column(nullable = false, unique = true)
    private String token;
    
    /**
     * ID заявки, которую редактируют.
     */
    @Column(nullable = false)
    private Long submissionId;
    
    /**
     * ID модератора, создавшего сессию.
     */
    @Column(nullable = false)
    private Long moderatorId;
    
    /**
     * Время создания сессии.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Время истечения сессии.
     * По умолчанию createdAt + 1 час.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    /**
     * Флаг использования сессии.
     * После одобрения/отклонения сессия помечается как использованная.
     */
    @Column(nullable = false)
    private boolean used = false;
    
    public ModerationSession() {
    }
    
    // Геттеры и сеттеры
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public Long getSubmissionId() {
        return submissionId;
    }
    
    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }
    
    public Long getModeratorId() {
        return moderatorId;
    }
    
    public void setModeratorId(Long moderatorId) {
        this.moderatorId = moderatorId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public boolean isUsed() {
        return used;
    }
    
    public void setUsed(boolean used) {
        this.used = used;
    }
}
