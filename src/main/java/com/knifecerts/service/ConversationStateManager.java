package com.knifecerts.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.knifecerts.model.ConversationState;
import com.knifecerts.model.ConversationStep;

/**
 * Управляет состояниями диалогов пользователей в памяти.
 * 
 * Использует ConcurrentHashMap для обеспечения потокобезопасности при одновременной
 * обработке сообщений от нескольких пользователей.
 * 
 * Требования: 11.1, 11.2, 11.3
 */
@Component
public class ConversationStateManager {
    
    /**
     * Хранилище состояний диалогов, где ключ - userId, значение - ConversationState.
     * ConcurrentHashMap обеспечивает потокобезопасный доступ без явной синхронизации.
     */
    private final Map<Long, ConversationState> states = new ConcurrentHashMap<>();
    
    /**
     * Начинает новый диалог для пользователя.
     * Создает новое состояние с начальным шагом WAITING_FOR_PHOTO.
     * 
     * Требование: 11.1
     * 
     * @param userId Telegram ID пользователя
     */
    public void startConversation(Long userId) {
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
        states.put(userId, state);
    }
    
    /**
     * Получает текущее состояние диалога для пользователя.
     * 
     * Требование: 11.3
     * 
     * @param userId Telegram ID пользователя
     * @return ConversationState или null, если диалог не активен
     */
    public ConversationState getState(Long userId) {
        return states.get(userId);
    }
    
    /**
     * Обновляет состояние диалога для пользователя.
     * 
     * Требование: 11.3
     * 
     * @param userId Telegram ID пользователя
     * @param state Новое состояние диалога
     */
    public void updateState(Long userId, ConversationState state) {
        states.put(userId, state);
    }
    
    /**
     * Очищает состояние диалога для пользователя.
     * Вызывается после завершения или отмены заявки.
     * 
     * Требование: 11.2
     * 
     * @param userId Telegram ID пользователя
     */
    public void clearState(Long userId) {
        states.remove(userId);
    }
    
    /**
     * Проверяет, есть ли у пользователя активный диалог.
     * 
     * Требование: 11.3
     * 
     * @param userId Telegram ID пользователя
     * @return true, если диалог активен, иначе false
     */
    public boolean hasActiveConversation(Long userId) {
        return states.containsKey(userId);
    }
}
