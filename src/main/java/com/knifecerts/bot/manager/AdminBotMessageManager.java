package com.knifecerts.bot.manager;

import java.util.Map;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.bot.state.ChatMessages;

/**
 * Менеджер для управления сообщениями в AdminBot.
 * Отвечает за удаление сообщений, очистку окон и управление историей сообщений.
 */
@Component
public class AdminBotMessageManager {

    private static final Logger logger = Logger.getLogger(AdminBotMessageManager.class.getName());

    /**
     * Удаляет все сообщения кроме главного меню.
     * 
     * @param chatId ID чата
     * @param chatMessages карта сообщений чата
     * @param moderationStateManager менеджер состояний модерации
     * @param searchStates карта состояний поиска
     * @param bot экземпляр бота для выполнения команд
     */
    public void deleteAllMessagesExceptMenu(Long chatId, 
                                           Map<Long, ChatMessages> chatMessages,
                                           ModerationStateManager moderationStateManager,
                                           Map<Long, String> searchStates,
                                           TelegramLongPollingBot bot) {
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null) {
            // Если нет записей, просто очищаем состояния
            moderationStateManager.removeState(chatId);
            searchStates.remove(chatId);
            return;
        }
        
        // Удаляем сообщения из ModerationState
        ModerationStateManager.ModerationState modState = moderationStateManager.getState(chatId);
        if (modState != null) {
            if (modState.getFormMessageId() != null) {
                deleteMessage(chatId, modState.getFormMessageId(), bot);
            }
            if (modState.getPromptMessageId() != null) {
                deleteMessage(chatId, modState.getPromptMessageId(), bot);
            }
        }
        
        // Удаляем все отслеживаемые сообщения КРОМЕ главного меню
        for (Integer messageId : messages.getOtherMessageIds()) {
            deleteMessage(chatId, messageId, bot);
        }
        
        // Очищаем список и состояния
        messages.clearOtherMessages();
        moderationStateManager.removeState(chatId);
        searchStates.remove(chatId);
    }

    /**
     * Закрывает текущее окно, удаляя все связанные сообщения.
     * 
     * @param chatId ID чата
     * @param chatMessages карта сообщений чата
     * @param moderationStateManager менеджер состояний модерации
     * @param searchStates карта состояний поиска
     * @param currentWindow карта текущих окон
     * @param bot экземпляр бота для выполнения команд
     */
    public void closeCurrentWindow(Long chatId,
                                   Map<Long, ChatMessages> chatMessages,
                                   ModerationStateManager moderationStateManager,
                                   Map<Long, String> searchStates,
                                   Map<Long, String> currentWindow,
                                   TelegramLongPollingBot bot) {
        logger.info("DEBUG: closeCurrentWindow called for chat " + chatId);
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null) {
            logger.info("DEBUG: No messages found for chat " + chatId);
            return;
        }
        
        // Удаляем последнее сообщение окна
        if (messages.getLastWindowMessageId() != null) {
            logger.info("DEBUG: Deleting lastWindowMessageId: " + messages.getLastWindowMessageId());
            deleteMessage(chatId, messages.getLastWindowMessageId(), bot);
            messages.setLastWindowMessageId(null);
        } else {
            logger.info("DEBUG: No lastWindowMessageId to delete");
        }
        
        // Удаляем сообщения из ModerationState
        ModerationStateManager.ModerationState modState = moderationStateManager.getState(chatId);
        if (modState != null) {
            if (modState.getFormMessageId() != null) {
                logger.info("DEBUG: Deleting formMessageId: " + modState.getFormMessageId());
                deleteMessage(chatId, modState.getFormMessageId(), bot);
            }
            if (modState.getPromptMessageId() != null) {
                logger.info("DEBUG: Deleting promptMessageId: " + modState.getPromptMessageId());
                deleteMessage(chatId, modState.getPromptMessageId(), bot);
            }
        }
        
        // Удаляем все отслеживаемые сообщения
        for (Integer messageId : messages.getOtherMessageIds()) {
            logger.info("DEBUG: Deleting otherMessageId: " + messageId);
            deleteMessage(chatId, messageId, bot);
        }
        
        // Очищаем список и состояния
        messages.clearOtherMessages();
        moderationStateManager.removeState(chatId);
        searchStates.remove(chatId);
        currentWindow.remove(chatId);
        logger.info("DEBUG: closeCurrentWindow completed");
    }

    /**
     * Удаляет недавние сообщения окон.
     * 
     * @param chatId ID чата
     * @param menuMessageId ID сообщения меню (не используется, оставлен для совместимости)
     * @param chatMessages карта сообщений чата
     * @param moderationStateManager менеджер состояний модерации
     * @param searchStates карта состояний поиска
     * @param currentWindow карта текущих окон
     * @param bot экземпляр бота для выполнения команд
     */
    public void deleteRecentMessages(Long chatId, 
                                     Integer menuMessageId,
                                     Map<Long, ChatMessages> chatMessages,
                                     ModerationStateManager moderationStateManager,
                                     Map<Long, String> searchStates,
                                     Map<Long, String> currentWindow,
                                     TelegramLongPollingBot bot) {
        logger.info("DEBUG: deleteRecentMessages called for chat " + chatId);
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null) {
            logger.info("DEBUG: No messages object found");
            return;
        }
        
        java.util.List<Integer> toDelete = messages.getRecentWindowMessages();
        logger.info("DEBUG: Found " + toDelete.size() + " messages to delete");
        
        // Удаляем все недавние сообщения окон
        int deletedCount = 0;
        for (Integer msgId : toDelete) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(msgId);
                bot.execute(deleteMsg);
                deletedCount++;
                logger.info("DEBUG: Deleted message " + msgId);
            } catch (Exception e) {
                logger.info("DEBUG: Failed to delete message " + msgId + ": " + e.getMessage());
            }
        }
        
        // Очищаем список ПОСЛЕ удаления
        messages.clearRecentWindowMessages();
        logger.info("DEBUG: Deleted " + deletedCount + " messages, cleared list");
        
        // Очищаем состояния
        moderationStateManager.removeState(chatId);
        searchStates.remove(chatId);
        currentWindow.remove(chatId);
    }

    /**
     * Удаляет одно сообщение по ID.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param bot экземпляр бота для выполнения команд
     */
    public void deleteMessage(Long chatId, Integer messageId, TelegramLongPollingBot bot) {
        if (messageId == null) return;
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            bot.execute(deleteMessage);
        } catch (Exception e) {
            // Игнорируем ошибки удаления (сообщение может быть уже удалено)
        }
    }

    /**
     * Удаляет сообщение пользователя из чата.
     * Используется для очистки чата от введенных данных после их обработки.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     * @param bot экземпляр бота для выполнения команд
     */
    public void deleteUserMessage(Long chatId, Integer messageId, TelegramLongPollingBot bot) {
        if (messageId == null) {
            logger.warning("Попытка удалить сообщение с null ID");
            return;
        }
        
        try {
            DeleteMessage deleteMsg = new DeleteMessage();
            deleteMsg.setChatId(chatId.toString());
            deleteMsg.setMessageId(messageId);
            bot.execute(deleteMsg);
            logger.info("Удалено сообщение пользователя: " + messageId + " в чате " + chatId);
        } catch (TelegramApiException e) {
            // Логируем, но не прерываем выполнение
            logger.warning("Не удалось удалить сообщение пользователя " + messageId + ": " + e.getMessage());
        }
    }
}
