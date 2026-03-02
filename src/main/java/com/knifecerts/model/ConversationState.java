package com.knifecerts.model;

import java.util.List;

/**
 * Класс для отслеживания состояния диалога пользователя в процессе подачи заявки.
 * 
 * Хранит информацию о текущем шаге диалога и собранных данных (фото, название модели, описание).
 * Используется ConversationStateManager для управления состояниями в памяти.
 * 
 * Требование: 11.1
 */
public class ConversationState {
    
    /**
     * Telegram ID пользователя, для которого отслеживается состояние диалога.
     */
    private Long userId;
    
    /**
     * Текущий шаг в процессе подачи заявки.
     */
    private ConversationStep currentStep;
    
    /**
     * Telegram file ID фотографии, отправленной пользователем.
     * Используется для скачивания фото при финализации заявки.
     */
    private String photoFileId;
    
    /**
     * Telegram username пользователя.
     * Сохраняется при начале диалога для использования при создании заявки.
     */
    private String username;
    
    /**
     * Название ножа (опциональное).
     * Может быть null, если пользователь пропустил этот шаг.
     */
    private String name;
    
    /**
     * Бренд ножа (опциональное).
     * Может быть null, если пользователь пропустил этот шаг.
     */
    private String brand;
    
    /**
     * Индекс ножа (опциональное).
     * Может быть null, если пользователь пропустил этот шаг.
     */
    private String indexCode;
    
    /**
     * Список альтернативных моделей ножей (опциональное).
     * Может быть null или пустым, если пользователь пропустил этот шаг.
     */
    private List<String> alternativeModels;
    
    /**
     * ID сообщения с формой заявки в Telegram.
     * Используется для редактирования кнопок формы.
     */
    private Integer formMessageId;
    
    /**
     * ID сообщения-запроса ввода поля.
     * Используется для удаления после ответа пользователя.
     */
    private Integer promptMessageId;
    
    /**
     * Конструктор по умолчанию.
     */
    public ConversationState() {
    }
    
    /**
     * Конструктор для создания нового состояния диалога.
     * 
     * @param userId Telegram ID пользователя
     * @param currentStep Начальный шаг диалога
     */
    public ConversationState(Long userId, ConversationStep currentStep) {
        this.userId = userId;
        this.currentStep = currentStep;
    }
    
    // Геттеры и сеттеры
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public ConversationStep getCurrentStep() {
        return currentStep;
    }
    
    public void setCurrentStep(ConversationStep currentStep) {
        this.currentStep = currentStep;
    }
    
    public String getPhotoFileId() {
        return photoFileId;
    }
    
    public void setPhotoFileId(String photoFileId) {
        this.photoFileId = photoFileId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getBrand() {
        return brand;
    }
    
    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    public String getIndexCode() {
        return indexCode;
    }
    
    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public List<String> getAlternativeModels() {
        return alternativeModels;
    }
    
    public void setAlternativeModels(List<String> alternativeModels) {
        this.alternativeModels = alternativeModels;
    }
    
    public Integer getFormMessageId() {
        return formMessageId;
    }
    
    public void setFormMessageId(Integer formMessageId) {
        this.formMessageId = formMessageId;
    }
    
    public Integer getPromptMessageId() {
        return promptMessageId;
    }
    
    public void setPromptMessageId(Integer promptMessageId) {
        this.promptMessageId = promptMessageId;
    }
}
