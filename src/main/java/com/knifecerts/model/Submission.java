package com.knifecerts.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * JPA-сущность, представляющая запись заявки на сертификат в базе данных.
 * 
 * Хранит информацию о заявке пользователя, включая метаданные, путь к фото,
 * статус модерации и временные метки.
 * 
 * Требования: 5.1, 5.3, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6
 */
@Entity
@Table(name = "submissions")
public class Submission {
    
    /**
     * Уникальный идентификатор заявки.
     * Автоинкрементный BIGINT первичный ключ.
     * Требование: 12.1
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Telegram ID пользователя, подавшего заявку.
     * Обязательное поле.
     * Требование: 12.2
     */
    @Column(nullable = false)
    private Long userId;
    
    /**
     * Telegram username пользователя.
     * Обязательное поле.
     * Требование: 5.3
     */
    @Column(nullable = false)
    private String username;
    
    /**
     * Название модели ножа (опциональное).
     * Может быть null, если пользователь пропустил этот шаг.
     * Требование: 12.6
     */
    @Column
    private String modelName;
    
    /**
     * Описание сертификата (опциональное).
     * Может быть null, если пользователь пропустил этот шаг.
     * Требование: 12.6
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * Путь к фото на Yandex.Disk.
     * Обязательное поле.
     * Требование: 12.4
     */
    @Column(nullable = false)
    private String photoPath;
    
    /**
     * Текущий статус заявки (PENDING, APPROVED, REJECTED).
     * Обязательное поле.
     * Требование: 12.3
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;
    
    /**
     * Временная метка создания заявки.
     * Автоматически устанавливается при создании записи.
     * Требование: 12.5
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Временная метка модерации заявки.
     * Устанавливается при одобрении или отклонении.
     * Может быть null для заявок со статусом PENDING.
     * Требование: 12.6
     */
    @Column
    private LocalDateTime moderatedAt;
    
    /**
     * Telegram ID модератора, проверившего заявку.
     * Устанавливается при одобрении или отклонении.
     * Может быть null для заявок со статусом PENDING.
     * Требование: 12.6
     */
    @Column
    private Long moderatedBy;
    
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    
    /**
     * Альтернативные модели ножей, к которым подходит сертификат.
     * Хранится как JSON массив строк.
     * Может быть null если пользователь не указал альтернативные модели.
     */
    @Column(columnDefinition = "TEXT")
    private String alternativeModels;
    
    @Transient
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    /**
     * Конструктор по умолчанию для JPA.
     */
    public Submission() {
    }
    
    /**
     * Конструктор для создания новой заявки.
     * 
     * @param userId Telegram ID пользователя
     * @param username Telegram username пользователя
     * @param modelName Название модели (может быть null)
     * @param description Описание (может быть null)
     * @param photoPath Путь к фото на Yandex.Disk
     */
    public Submission(Long userId, String username, String modelName, 
                     String description, String photoPath) {
        this.userId = userId;
        this.username = username;
        this.modelName = modelName;
        this.description = description;
        this.photoPath = photoPath;
        this.status = SubmissionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }
    
    // Геттеры и сеттеры
    
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
    
    /**
     * Получить список альтернативных моделей.
     * Парсит JSON строку в список.
     * 
     * @return список альтернативных моделей или пустой список
     */
    public List<String> getAlternativeModelsList() {
        if (alternativeModels == null || alternativeModels.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON_MAPPER.readValue(alternativeModels, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Установить список альтернативных моделей.
     * Сериализует список в JSON строку.
     * 
     * @param models список альтернативных моделей
     */
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
    
    /**
     * Получить сырую JSON строку альтернативных моделей.
     * 
     * @return JSON строка или null
     */
    public String getAlternativeModels() {
        return alternativeModels;
    }
    
    /**
     * Установить сырую JSON строку альтернативных моделей.
     * 
     * @param alternativeModels JSON строка
     */
    public void setAlternativeModels(String alternativeModels) {
        this.alternativeModels = alternativeModels;
    }
}
