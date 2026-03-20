package com.knifecerts.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.knifecerts.model.UserMainMenu;
import com.knifecerts.repository.UserMainMenuRepository;

/**
 * Сервис для автообновления главного меню у всех пользователей.
 * Используется при добавлении новых брендов для обновления меню KnifeBot и AdminBot.
 * 
 * ВАЖНО: Реализация требует доступа к Telegram API через bot instances.
 * Для полной реализации нужно инжектить KnifeBot и AdminBot или создать общий TelegramService.
 */
@Service
public class MainMenuUpdateService {
    
    private final UserMainMenuRepository userMainMenuRepository;
    
    @Autowired
    public MainMenuUpdateService(UserMainMenuRepository userMainMenuRepository) {
        this.userMainMenuRepository = userMainMenuRepository;
    }
    
    /**
     * Обновляет главное меню у всех пользователей KnifeBot.
     * Читает chatId и messageId из таблицы user_main_menu,
     * использует editMessage для обновления, пропускает пользователей при ошибке.
     * 
     * ВАЖНО: Требует инжекта KnifeBot для вызова execute().
     * Пока реализована как заглушка с логированием.
     */
    public void updateAllUserMenus() {
        List<UserMainMenu> userMenus = userMainMenuRepository.findAll();
        
        for (UserMainMenu userMenu : userMenus) {
            try {
                // TODO: Реализовать вызов editMessage через KnifeBot
                // EditMessageText editMessage = new EditMessageText();
                // editMessage.setChatId(userMenu.getChatId().toString());
                // editMessage.setMessageId(userMenu.getMessageId());
                // editMessage.setText("**Blade Guardian**");
                // knifeBot.execute(editMessage);
                System.out.println("MainMenuUpdateService: updateAllUserMenus - chatId=" + userMenu.getChatId() + 
                                   ", messageId=" + userMenu.getMessageId());
            } catch (Exception e) {
                // Пропускаем пользователей при ошибке
                System.err.println("MainMenuUpdateService: failed to update menu for chatId=" + 
                                   userMenu.getChatId() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Обновляет главное меню модератора (AdminBot).
     * Используется при добавлении новых брендов.
     * 
     * @param adminChatId chatId администратора
     * 
     * ВАЖНО: Требует инжекта AdminBot для вызова execute().
     * Пока реализована как заглушка с логированием.
     */
    public void updateAdminMenu(Long adminChatId) {
        try {
            // TODO: Реализовать вызов editMessage через AdminBot
            // EditMessageText editMessage = new EditMessageText();
            // editMessage.setChatId(adminChatId.toString());
            // editMessage.setMessageId(adminMenuMessageId);
            // editMessage.setText("Меню модератора");
            // adminBot.execute(editMessage);
            System.out.println("MainMenuUpdateService: updateAdminMenu - adminChatId=" + adminChatId);
        } catch (Exception e) {
            System.err.println("MainMenuUpdateService: failed to update admin menu for chatId=" + 
                               adminChatId + ": " + e.getMessage());
        }
    }
}
