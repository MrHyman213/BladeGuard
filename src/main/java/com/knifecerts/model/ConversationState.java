package com.knifecerts.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * ID главного меню.
     * Используется для удаления всех сообщений кроме главного меню.
     */
    private Integer mainMenuMessageId;
    
    /**
     * ID текущего сообщения со списком (брендов или моделей).
     */
    private Integer currentListMessageId;
    
    /**
     * ID текущего сообщения с сертификатом.
     */
    private Integer currentCertificateMessageId;
    
    /**
     * ID сообщения об успешной отправке заявки.
     * Удаляется при следующем действии пользователя.
     */
    private Integer successMessageId;
    
    /**
     * Текущая страница пагинации.
     */
    private Integer currentPage;
    
    /**
     * Текущий выбранный бренд (для навигации).
     */
    private String currentBrand;
    
    /**
     * Навигационный стек: уровень → messageId.
     * Требование: 13.1, 13.2
     */
    private Map<Integer, Integer> navStack = new HashMap<>();
    
    /**
     * ID сообщения-подтверждения (диалог, не входит в стек).
     * Требование: 13.1, 14
     */
    private Integer confirmationMessageId;
    
    /**
     * ID сообщения-подсказки (справка, не входит в стек).
     * Требование: 13.1, 14
     */
    private Integer hintMessageId;
    
    /**
     * Флаг: есть ли несохранённые изменения в форме (для AdminBot).
     * Требование: 13.2
     */
    private boolean hasUnsavedChanges;
    
    /**
     * ID текущей открытой заявки (для AdminBot).
     * Требование: 13.2
     */
    private Long currentSubmissionId;
    
    /**
     * Список сообщений пользователя для удаления.
     * Требование: 14
     */
    private List<Integer> pendingDeleteMessageIds = new ArrayList<>();
    
    /**
     * Поисковый запрос (для сохранения при пагинации).
     */
    private String searchQuery;
    
    /**
     * Результаты поиска (список названий).
     */
    private List<String> searchResults;
    
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
    
    public Integer getMainMenuMessageId() {
        return mainMenuMessageId;
    }
    
    public void setMainMenuMessageId(Integer mainMenuMessageId) {
        this.mainMenuMessageId = mainMenuMessageId;
    }
    
    public Integer getCurrentListMessageId() {
        return currentListMessageId;
    }
    
    public void setCurrentListMessageId(Integer currentListMessageId) {
        this.currentListMessageId = currentListMessageId;
    }
    
    public Integer getCurrentCertificateMessageId() {
        return currentCertificateMessageId;
    }
    
    public void setCurrentCertificateMessageId(Integer currentCertificateMessageId) {
        this.currentCertificateMessageId = currentCertificateMessageId;
    }
    
    public Integer getCurrentPage() {
        return currentPage != null ? currentPage : 0;
    }
    
    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }
    
    public String getCurrentBrand() {
        return currentBrand;
    }
    
    public void setCurrentBrand(String currentBrand) {
        this.currentBrand = currentBrand;
    }
    
    public Map<Integer, Integer> getNavStack() {
        return navStack;
    }
    
    public void setNavStack(Map<Integer, Integer> navStack) {
        this.navStack = navStack;
    }
    
    public Integer getConfirmationMessageId() {
        return confirmationMessageId;
    }
    
    public void setConfirmationMessageId(Integer confirmationMessageId) {
        this.confirmationMessageId = confirmationMessageId;
    }
    
    public Integer getHintMessageId() {
        return hintMessageId;
    }
    
    public void setHintMessageId(Integer hintMessageId) {
        this.hintMessageId = hintMessageId;
    }
    
    public boolean isHasUnsavedChanges() {
        return hasUnsavedChanges;
    }
    
    public void setHasUnsavedChanges(boolean hasUnsavedChanges) {
        this.hasUnsavedChanges = hasUnsavedChanges;
    }
    
    public Long getCurrentSubmissionId() {
        return currentSubmissionId;
    }
    
    public void setCurrentSubmissionId(Long currentSubmissionId) {
        this.currentSubmissionId = currentSubmissionId;
    }
    
    public List<Integer> getPendingDeleteMessageIds() {
        return pendingDeleteMessageIds;
    }
    
    public void setPendingDeleteMessageIds(List<Integer> pendingDeleteMessageIds) {
        this.pendingDeleteMessageIds = pendingDeleteMessageIds;
    }
    
    public Integer getSuccessMessageId() {
        return successMessageId;
    }
    
    public void setSuccessMessageId(Integer successMessageId) {
        this.successMessageId = successMessageId;
    }


    /**
     * Получает список альтернатив (алиас для alternativeModels).
     */
    public List<String> getAlternatives() {
        return alternativeModels;
    }

    /**
     * Устанавливает список альтернатив (алиас для alternativeModels).
     */
    public void setAlternatives(List<String> alternatives) {
        this.alternativeModels = alternatives;
    }

    /**
     * Очищает данные формы.
     */
    public void clearFormData() {
        this.photoFileId = null;
        this.name = null;
        this.brand = null;
        this.indexCode = null;
        this.alternativeModels = null;
        this.formMessageId = null;
        this.promptMessageId = null;
    }
    
    public String getSearchQuery() {
        return searchQuery;
    }
    
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }
    
    public List<String> getSearchResults() {
        return searchResults;
    }
    
    public void setSearchResults(List<String> searchResults) {
        this.searchResults = searchResults;
    }
}