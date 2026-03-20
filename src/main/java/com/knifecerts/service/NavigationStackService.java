package com.knifecerts.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.knifecerts.model.ConversationState;

/**
 * Управляет навигационным стеком для каждого пользователя/модератора.
 * 
 * Навигационный стек хранит messageId для каждого уровня навигации:
 * - KnifeBot: уровни 0-3 (главное меню, список моделей, сертификат, все альтернативы)
 * - AdminBot: уровни 0-2 (главное меню, список моделей/заявок, форма модерации)
 * 
 * Требования: 13.1–13.9
 */
@Service
public class NavigationStackService {
    
    @Autowired
    private ConversationStateManager conversationStateManager;
    
    /**
     * Получить все messageId с уровнем >= level (включая сам level).
     * Используется для удаления сообщений при переходе на новый уровень.
     * 
     * Требование: 13.3
     * 
     * @param chatId Telegram ID пользователя/модератора
     * @param level Уровень навигации
     * @return Список messageId для удаления
     */
    public List<Integer> getMessagesAtOrBelow(Long chatId, int level) {
        ConversationState state = conversationStateManager.getState(chatId);
        if (state == null) {
            return new ArrayList<>();
        }
        
        List<Integer> messagesToDelete = new ArrayList<>();
        for (int i = level; i <= 3; i++) {
            Integer messageId = state.getNavStack().get(i);
            if (messageId != null) {
                messagesToDelete.add(messageId);
            }
        }
        return messagesToDelete;
    }
    
    /**
     * Установить messageId для уровня навигации.
     * 
     * Требование: 13.3
     * 
     * @param chatId Telegram ID пользователя/модератора
     * @param level Уровень навигации
     * @param messageId ID сообщения (null для очистки уровня)
     */
    public void setLevel(Long chatId, int level, Integer messageId) {
        ConversationState state = conversationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (messageId == null) {
            state.getNavStack().remove(level);
        } else {
            state.getNavStack().put(level, messageId);
        }
        conversationStateManager.updateState(chatId, state);
    }
    
    /**
     * Удалить уровни >= level из стека.
     * Используется при переходе на новый уровень.
     * 
     * Требование: 13.3
     * 
     * @param chatId Telegram ID пользователя/модератора
     * @param level Уровень, начиная с которого удалять
     */
    public void clearFrom(Long chatId, int level) {
        ConversationState state = conversationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        for (int i = level; i <= 3; i++) {
            state.getNavStack().remove(i);
        }
        conversationStateManager.updateState(chatId, state);
    }
    
    /**
     * Очистить весь стек.
     * Используется при /start команде.
     * 
     * Требование: 13.7
     * 
     * @param chatId Telegram ID пользователя/модератора
     */
    public void clearAll(Long chatId) {
        ConversationState state = conversationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        state.getNavStack().clear();
        conversationStateManager.updateState(chatId, state);
    }
    
    /**
     * Получить messageId главного меню (уровень 0).
     * 
     * Требование: 13.1
     * 
     * @param chatId Telegram ID пользователя/модератора
     * @return Optional с messageId главного меню
     */
    public Optional<Integer> getMainMenuMessageId(Long chatId) {
        ConversationState state = conversationStateManager.getState(chatId);
        if (state == null) {
            return Optional.empty();
        }
        
        Integer messageId = state.getNavStack().get(0);
        return messageId != null ? Optional.of(messageId) : Optional.empty();
    }
}
