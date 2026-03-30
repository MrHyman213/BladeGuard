package com.knifecerts.service;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.KnifeBot;
import com.knifecerts.model.Brand;
import com.knifecerts.model.UserMainMenu;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.UserMainMenuRepository;

/**
 * Сервис для автообновления главного меню у всех пользователей.
 * Используется при добавлении новых брендов для обновления меню KnifeBot и AdminBot.
 */
@Service
public class MainMenuUpdateService {
    
    private static final Logger logger = Logger.getLogger(MainMenuUpdateService.class.getName());
    
    private final UserMainMenuRepository userMainMenuRepository;
    private final BrandRepository brandRepository;
    private final KnifeBot knifeBot;
    private final AdminBot adminBot;
    
    @Autowired
    public MainMenuUpdateService(
            UserMainMenuRepository userMainMenuRepository,
            BrandRepository brandRepository,
            @Lazy KnifeBot knifeBot,
            @Lazy AdminBot adminBot) {
        this.userMainMenuRepository = userMainMenuRepository;
        this.brandRepository = brandRepository;
        this.knifeBot = knifeBot;
        this.adminBot = adminBot;
    }
    
    /**
     * Обновляет главное меню у всех пользователей KnifeBot.
     * Читает chatId и messageId из таблицы user_main_menu,
     * использует editMessage для обновления, пропускает пользователей при ошибке.
     */
    public void updateAllUserMenus() {
        List<UserMainMenu> userMenus = userMainMenuRepository.findAll();
        logger.info("Обновление главного меню для " + userMenus.size() + " пользователей");
        
        for (UserMainMenu userMenu : userMenus) {
            try {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(userMenu.getChatId().toString());
                editMessage.setMessageId(userMenu.getMessageId());
                editMessage.setText("**Blade Guardian**\n\nВыберите бренд:");
                editMessage.setReplyMarkup(buildMainMenuKeyboard(0));
                
                knifeBot.execute(editMessage);
                logger.info("Главное меню обновлено для chatId=" + userMenu.getChatId());
            } catch (TelegramApiException e) {
                logger.warning("Не удалось обновить меню для chatId=" + userMenu.getChatId() + 
                              ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Строит клавиатуру главного меню с брендами.
     * 
     * @param page номер страницы (для пагинации)
     * @return InlineKeyboardMarkup с кнопками брендов
     */
    private InlineKeyboardMarkup buildMainMenuKeyboard(int page) {
        List<Brand> brands = brandRepository.findAll();
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new java.util.ArrayList<>();
        
        // Пагинация: 10 брендов на странице
        int pageSize = 10;
        int start = page * pageSize;
        int end = Math.min(start + pageSize, brands.size());
        
        for (int i = start; i < end; i++) {
            Brand brand = brands.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(brand.getName());
            button.setCallbackData("brand_" + brand.getName());
            keyboard.add(List.of(button));
        }
        
        // Кнопки навигации
        final var navRow = getInlineKeyboardButtons(page, end, brands);
        if (!navRow.isEmpty()) {
            keyboard.add(navRow);
        }
        
        // Кнопки поиска и загрузки
        List<InlineKeyboardButton> actionRow = new java.util.ArrayList<>();
        InlineKeyboardButton searchButton = new InlineKeyboardButton();
        searchButton.setText("🔍 Поиск");
        searchButton.setCallbackData("search_brands");
        actionRow.add(searchButton);
        
        InlineKeyboardButton uploadButton = new InlineKeyboardButton();
        uploadButton.setText("📤 Загрузить");
        uploadButton.setCallbackData("upload_certificate");
        actionRow.add(uploadButton);
        keyboard.add(actionRow);
        
        markup.setKeyboard(keyboard);
        return markup;
    }

    private static List<InlineKeyboardButton> getInlineKeyboardButtons(int page, int end, List<Brand> brands) {
        List<InlineKeyboardButton> navRow = new java.util.ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️");
            prevButton.setCallbackData("main_page_" + (page - 1));
            navRow.add(prevButton);
        }
        if (end < brands.size()) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("➡️");
            nextButton.setCallbackData("main_page_" + (page + 1));
            navRow.add(nextButton);
        }
        return navRow;
    }

    /**
     * Обновляет главное меню модератора (AdminBot).
     * Используется при добавлении новых брендов.
     *
     *  @param adminChatId chatId администратора
     * @param messageId ID главного меню администратора
     */
    public void updateAdminMenu(Long adminChatId, Integer messageId) {
        if (messageId == null) {
            logger.warning("Не удалось обновить меню AdminBot: messageId не установлен");
            return;
        }
        
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(adminChatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText("**Blade Guardian Admin**\n\nВыберите действие:");
            editMessage.setReplyMarkup(buildAdminMainMenuKeyboard());
            
            adminBot.execute(editMessage);
            logger.info("Главное меню AdminBot обновлено для chatId=" + adminChatId);
        } catch (TelegramApiException e) {
            // Требование 6.3: Пропускаем при ошибке
            logger.warning("Не удалось обновить меню AdminBot для chatId=" + adminChatId + 
                          ": " + e.getMessage());
        }
    }
    
    /**
     * Строит клавиатуру главного меню AdminBot.
     * 
     * @return InlineKeyboardMarkup с кнопками действий
     */
    private InlineKeyboardMarkup buildAdminMainMenuKeyboard() {
        List<Brand> brands = brandRepository.findAll();
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new java.util.ArrayList<>();
        
        // Кнопки брендов (первые 10)
        int limit = Math.min(10, brands.size());
        for (int i = 0; i < limit; i++) {
            Brand brand = brands.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(brand.getName());
            button.setCallbackData("admin_brand_" + brand.getName());
            keyboard.add(List.of(button));
        }
        
        // Кнопки действий
        List<InlineKeyboardButton> actionRow1 = new java.util.ArrayList<>();
        InlineKeyboardButton pendingButton = new InlineKeyboardButton();
        pendingButton.setText("📋 Ожидающие");
        pendingButton.setCallbackData("menu_pending");
        actionRow1.add(pendingButton);
        
        InlineKeyboardButton uploadButton = new InlineKeyboardButton();
        uploadButton.setText("📤 Загрузить");
        uploadButton.setCallbackData("menu_upload");
        actionRow1.add(uploadButton);
        keyboard.add(actionRow1);
        
        List<InlineKeyboardButton> actionRow2 = new java.util.ArrayList<>();
        InlineKeyboardButton searchButton = new InlineKeyboardButton();
        searchButton.setText("🔍 Поиск");
        searchButton.setCallbackData("menu_search");
        actionRow2.add(searchButton);
        
        InlineKeyboardButton settingsButton = new InlineKeyboardButton();
        settingsButton.setText("⚙️ Настройки");
        settingsButton.setCallbackData("menu_settings");
        actionRow2.add(settingsButton);
        keyboard.add(actionRow2);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}
