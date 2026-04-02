package com.knifecerts.bot;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.model.*;
import com.knifecerts.repository.UserMainMenuRepository;
import com.knifecerts.service.*;
import com.knifecerts.util.RowBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;

@Component("knifeBotRedesigned")
public class KnifeBot extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(KnifeBot.class.getName());

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Autowired
    private YandexDiskService yandexDiskService;

    @Autowired
    private SubmissionBufferService submissionBufferService;
    
    @Autowired
    private KnifeService knifeService;

    @Autowired
    private SettingsService settingsService;
    
    @Autowired
    private BrandRepository brandRepository;
    
    @Autowired
    private KnifeModelRepository knifeModelRepository;

    @Autowired
    private UserMainMenuRepository userMainMenuRepository;
    
    @Autowired
    private ConversationStateManager conversationStateManager;
    
    @Autowired
    private NavigationStackService navigationStackService;
    
    @Autowired
    private CaptionParser captionParser;
    
    @Autowired
    private AlternativesParser alternativesParser;
    
    // Кэш для хранения последних разметок сообщений
    private final Map<Integer, InlineKeyboardMarkup> lastMarkupCache = new ConcurrentHashMap<>();

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }


    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
            } else if (update.hasMessage()) {
                Long chatId = update.getMessage().getChatId();
                Long userId = update.getMessage().getFrom().getId();
                
                if (update.getMessage().hasText()) {
                    String messageText = update.getMessage().getText();

                    if (messageText.equals("/start")) {
                        handleStart(userId, chatId, update.getMessage().getMessageId());
                    } else if (conversationStateManager.hasActiveConversation(userId)) {
                        handleConversationFlow(update);
                    } else {
                        sendMessage(chatId, "Используйте /start для главного меню.");
                    }
                } else if (update.getMessage().hasPhoto() && conversationStateManager.hasActiveConversation(userId)) {
                    handlePhotoSubmission(update);
                }
            }
        } catch (Exception e) {
            logger.severe("Error in KnifeBot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleStart(Long userId, Long chatId, Integer userMessageId) {
        try {
            deleteMessage(chatId, userMessageId);
            
            // Очищаем весь стек навигации
            navigationStackService.clearAll(userId);
            
            ConversationState state = conversationStateManager.getState(userId);
            if (state != null && state.getMainMenuMessageId() != null) {
                deleteMessage(chatId, state.getMainMenuMessageId());
            }
            
            Message mainMenu = sendMainMenu(chatId);
            
            state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
            state.setMainMenuMessageId(mainMenu.getMessageId());
            state.setCurrentPage(0);
            
            userMainMenuRepository.save(new UserMainMenu(chatId, mainMenu.getMessageId()));
            navigationStackService.setLevel(userId, 0, mainMenu.getMessageId());
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling start: " + e.getMessage());
        }
    }

    private Message sendMainMenu(Long chatId) throws TelegramApiException {
        return sendMainMenuWithPage(chatId, 0);
    }
    
    private Message sendMainMenuWithPage(Long chatId, int page) throws TelegramApiException {
        List<Brand> allBrands = knifeService.getAllBrandsWithKnives();
        
        int itemsPerPage = 30;
        int totalPages = (int) Math.ceil((double) allBrands.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        
        page = ((page % totalPages) + totalPages) % totalPages;
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allBrands.size());
        List<Brand> pageBrands = allBrands.subList(startIndex, endIndex);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("**Blade Guardian**");
        message.setParseMode("Markdown");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int i = 0; i < pageBrands.size(); i++) {
            Brand brand = pageBrands.get(i);
            String displayName = brand.getName();
            if (displayName.length() > 15) {
                displayName = displayName.substring(0, 12) + "...";
            }
            
            currentRow.add(InlineKeyboardButton.builder()
                .text(displayName)
                .callbackData("brand_" + brand.getName())
                .build());
            
            if (currentRow.size() == 3) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }
        
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();
            int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
            int nextPage = (page + 1) % totalPages;
            
            paginationRow.add(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("main_page_" + prevPage)
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text(String.format("%d/%d", page + 1, totalPages))
                .callbackData("main_current_page")
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text("➡️")
                .callbackData("main_page_" + nextPage)
                .build());
            keyboard.add(paginationRow);
        }
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(InlineKeyboardButton.builder()
            .text("🔍 Поиск")
            .callbackData("search_brands")
            .build());
        actionRow.add(InlineKeyboardButton.builder()
            .text("📤 Загрузить")
            .callbackData("upload_certificate")
            .build());
        keyboard.add(actionRow);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        return execute(message);
    }


    private void handleCallbackQuery(Update update) {
        var callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        String toastMessage = null; // Сообщение для Toast-уведомления
        
        try {
            if (data.startsWith("main_page_")) {
                int page = Integer.parseInt(data.substring(10));
                updateMainMenu(userId, chatId, page);
            } else if (data.equals("main_current_page")) {
                // Ignore
            } else if (data.startsWith("brand_")) {
                handleBrandSelection(userId, chatId, data.substring(6));
            } else if (data.startsWith("knife_list_page_")) {
                String[] parts = data.substring(16).split("_brand_");
                int page = Integer.parseInt(parts[0]);
                String brandName = parts[1];
                updateKnifeList(userId, chatId, brandName, page);
            } else if (data.equals("knife_list_current_page")) {
                // Ignore
            } else if (data.startsWith("knife_")) {
                Long submissionId = Long.parseLong(data.substring(6));
                showCertificate(userId, chatId, submissionId);
            } else if (data.startsWith("alternatives_")) {
                Long submissionId = Long.parseLong(data.substring(13));
                showAlternativesList(userId, chatId, submissionId, 0);
            } else if (data.startsWith("alt_page_")) {
                String[] parts = data.substring(9).split("_knife_");
                int page = Integer.parseInt(parts[0]);
                Long knifeId = Long.parseLong(parts[1]);
                showAlternativesList(userId, chatId, knifeId, page);
            } else if (data.equals("back_to_brands")) {
                backToBrands(userId, chatId);
            } else if (data.equals("back_to_knives")) {
                backToKnifeList(userId, chatId);
            } else if (data.equals("search_brands")) {
                handleSearchBrands(userId, chatId);
            } else if (data.startsWith("search_knives_")) {
                String brandName = data.substring(14);
                handleSearchKnives(userId, chatId, brandName);
            } else if (data.startsWith("search_page_")) {
                String[] parts = data.substring(12).split("_");
                int page = Integer.parseInt(parts[0]);
                String type = parts[1];
                handleSearchPageChange(userId, chatId, page, type);
            } else if (data.equals("search_current_page")) {
                // Ignore
            } else if (data.equals("upload_certificate")) {
                handleUploadCertificate(userId, chatId);
            } else if (data.equals("form_edit_brand")) {
                handleFormEditField(userId, chatId, "brand");
            } else if (data.equals("form_edit_name")) {
                handleFormEditField(userId, chatId, "name");
            } else if (data.equals("form_edit_index")) {
                handleFormEditField(userId, chatId, "index");
            } else if (data.equals("form_add_alt")) {
                handleFormAddAlternative(userId, chatId);
                toastMessage = "➕ Альтернатива добавлена";
            } else if (data.startsWith("form_remove_alt_")) {
                int index = Integer.parseInt(data.substring(16));
                handleFormRemoveAlternative(userId, chatId, index);
                toastMessage = "🗑️ Альтернатива удалена";
            } else if (data.equals("form_submit")) {
                handleFormSubmit(userId, chatId);
                toastMessage = "✅ Заявка отправлена на модерацию";
            } else if (data.equals("form_close")) {
                handleFormClose(userId, chatId);
            } else if (data.equals("form_close_yes")) {
                handleFormCloseYes(userId, chatId);
            } else if (data.equals("form_close_no")) {
                handleFormCloseNo(userId, chatId);
            } else if (data.equals("form_cancel_input")) {
                handleFormCancelInput(userId, chatId);
            }
            
        } catch (Exception e) {
            logger.severe("Error handling callback: " + e.getMessage());
            try {
                sendMessage(chatId, "❌ Произошла ошибка при обработке запроса");
            } catch (Exception ex) {
                logger.severe("Failed to send error message: " + ex.getMessage());
            }
        }
        
        // Отправляем ответ на callback (обязательно в конце)
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = 
                new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            if (toastMessage != null) {
                answer.setText(toastMessage);
                answer.setShowAlert(false); // Toast notification, не popup
            }
            execute(answer);
        } catch (TelegramApiException e) {
            logger.warning("Failed to answer callback query: " + e.getMessage());
        }
    }

    private void updateMainMenu(Long userId, Long chatId, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getMainMenuMessageId() == null) {
                return;
            }
            
            List<Brand> allBrands = knifeService.getAllBrandsWithKnives();
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) allBrands.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allBrands.size());
            List<Brand> pageBrands = allBrands.subList(startIndex, endIndex);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageBrands.size(); i++) {
                Brand brand = pageBrands.get(i);
                String displayName = brand.getName();
                if (displayName.length() > 15) {
                    displayName = displayName.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("brand_" + brand.getName())
                    .build());
                
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            // TODO: Реализовать проверку небрендовых ножей в новой схеме
            // if (knifeService.hasUnbrandedKnives()) {
            //     List<InlineKeyboardButton> unbrandedRow = new ArrayList<>();
            //     unbrandedRow.add(InlineKeyboardButton.builder()
            //         .text("🔪 Ножи без бренда")
            //         .callbackData("brand_unbranded")
            //         .build());
            //     keyboard.add(unbrandedRow);
            // }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("⬅️")
                    .callbackData("main_page_" + prevPage)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("main_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("main_page_" + nextPage)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("🔍 Поиск")
                .callbackData("search_brands")
                .build());
            actionRow.add(InlineKeyboardButton.builder()
                .text("📤 Загрузить")
                .callbackData("upload_certificate")
                .build());
            keyboard.add(actionRow);
            
            markup.setKeyboard(keyboard);
            
            // Проверка, изменилась ли разметка
            if (!isMarkupChanged(state.getMainMenuMessageId(), markup)) {
                logger.fine("Разметка главного меню не изменилась, пропускаем обновление");
                return;
            }
            
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getMainMenuMessageId());
            editMarkup.setReplyMarkup(markup);
            
            execute(editMarkup);
            
            // Сохраняем текущую разметку
            saveLastMarkup(state.getMainMenuMessageId(), markup);
            
            state.setCurrentPage(page);
            conversationStateManager.updateState(userId, state);
            
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                logger.fine("Сообщение не требует обновления: " + e.getMessage());
            } else {
                logger.severe("Error updating main menu: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.severe("Error updating main menu: " + e.getMessage());
        }
    }

    private void handleBrandSelection(Long userId, Long chatId, String brandName) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем все сообщения уровня 1 и выше (обрезаем стек до уровня 0)
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(userId, 1);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(userId, 1);
            
            Message listMessage = sendKnifeList(chatId, brandName, 0);
            state.setCurrentListMessageId(listMessage.getMessageId());
            state.setCurrentBrand(brandName);
            state.setCurrentPage(0);
            
            // Устанавливаем список моделей на уровень 1 стека
            navigationStackService.setLevel(userId, 1, listMessage.getMessageId());
            
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling brand selection: " + e.getMessage());
        }
    }


    private Message sendKnifeList(Long chatId, String brandName, int page) throws TelegramApiException {
        List<Knife> knives = knifeService.getAllKnivesByBrand(brandName);
        
        int itemsPerPage = 30;
        int totalPages = (int) Math.ceil((double) knives.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        
        page = ((page % totalPages) + totalPages) % totalPages;
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, knives.size());
        List<Knife> pageKnives = knives.subList(startIndex, endIndex);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🏷️ " + brandName + "\n\nВсего: " + knives.size() + " моделей");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int i = 0; i < pageKnives.size(); i++) {
            Knife knife = pageKnives.get(i);
            String displayName = knife.getModel().getName();
            if (displayName.length() > 15) {
                displayName = displayName.substring(0, 12) + "...";
            }
            
            currentRow.add(InlineKeyboardButton.builder()
                .text(displayName)
                .callbackData("knife_" + knife.getId())
                .build());
            
            if (currentRow.size() == 3) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }
        
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();
            int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
            int nextPage = (page + 1) % totalPages;
            
            paginationRow.add(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("knife_list_page_" + prevPage + "_brand_" + brandName)
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text(String.format("%d/%d", page + 1, totalPages))
                .callbackData("knife_list_current_page")
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text("➡️")
                .callbackData("knife_list_page_" + nextPage + "_brand_" + brandName)
                .build());
            keyboard.add(paginationRow);
        }
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(InlineKeyboardButton.builder()
            .text("🔍 Поиск")
            .callbackData("search_knives_" + brandName)
            .build());
        keyboard.add(actionRow);
        
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder()
            .text("🔙 К брендам")
            .callbackData("back_to_brands")
            .build());
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        return execute(message);
    }

    private void updateKnifeList(Long userId, Long chatId, String brandName, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getCurrentListMessageId() == null) {
                return;
            }
            
            List<Knife> knives = knifeService.getAllKnivesByBrand(brandName);
            
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) knives.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, knives.size());
            List<Knife> pageKnives = knives.subList(startIndex, endIndex);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageKnives.size(); i++) {
                Knife knife = pageKnives.get(i);
                String displayName = knife.getModel().getName();
                if (displayName.length() > 15) {
                    displayName = displayName.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("knife_" + knife.getId())
                    .build());
                
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("⬅️")
                    .callbackData("knife_list_page_" + prevPage + "_brand_" + brandName)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("knife_list_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("knife_list_page_" + nextPage + "_brand_" + brandName)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("🔍 Поиск")
                .callbackData("search_knives_" + brandName)
                .build());
            keyboard.add(actionRow);
            
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("🔙 К брендам")
                .callbackData("back_to_brands")
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getCurrentListMessageId());
            editMarkup.setReplyMarkup(markup);
            
            execute(editMarkup);
            
            state.setCurrentPage(page);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error updating knife list: " + e.getMessage());
        }
    }

    private void showCertificate(Long userId, Long chatId, Long knifeId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем все сообщения уровня 2 и выше (обрезаем стек до уровня 1)
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(userId, 2);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(userId, 2);
            
            Optional<Knife> knifeOpt = knifeService.getKnifeById(knifeId);
            if (knifeOpt.isEmpty()) {
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            // Проверяем, есть ли сертификат
            if (knife.getPhotoPath() != null) {
                // Показываем сертификат
                showKnifeCertificate(userId, chatId, knife);
            } else {
                // Показываем альтернативы
                showKnifeAlternatives(userId, chatId, knife);
            }
            
        } catch (Exception e) {
            logger.severe("Error showing certificate: " + e.getMessage());
        }
    }

    private void showKnifeCertificate(Long userId, Long chatId, Knife knife) throws Exception {
        ConversationState state = conversationStateManager.getState(userId);
        // Для модели с сертификатом показываем только альтернативы с сертификатом
        List<Knife> alternatives = knifeService.getAlternatives(knife.getId());
        
        StringBuilder caption = new StringBuilder();
        caption.append("🔪 ").append(knife.getDisplayName()).append("\n\n");
        caption.append("🏷️ ").append(knife.getBrand().getName()).append("\n");
        if (knife.getIndex() != null) {
            caption.append("🔢 ").append(knife.getIndex()).append("\n");
        }
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        if (!alternatives.isEmpty()) {
            String separator = settingsService.getAlternativeSeparator();
            List<InlineKeyboardButton> separatorRow = new ArrayList<>();
            separatorRow.add(InlineKeyboardButton.builder()
                .text("──── " + separator + " Альтернативы " + separator + " ────")
                .callbackData("alt_separator")
                .build());
            keyboard.add(separatorRow);
            
            int maxShow = Math.min(2, alternatives.size());
            for (int i = 0; i < maxShow; i++) {
                Knife alt = alternatives.get(i);
                String altName = alt.getDisplayName();
                if (altName.length() > 40) {
                    altName = altName.substring(0, 37) + "...";
                }
                
                List<InlineKeyboardButton> altRow = new ArrayList<>();
                altRow.add(InlineKeyboardButton.builder()
                    .text(altName)
                    .callbackData("knife_" + alt.getId())
                    .build());
                keyboard.add(altRow);
            }
            
            if (alternatives.size() > 2) {
                List<InlineKeyboardButton> moreRow = new ArrayList<>();
                moreRow.add(InlineKeyboardButton.builder()
                    .text("Больше... (" + alternatives.size() + ")")
                    .callbackData("alternatives_" + knife.getId())
                    .build());
                keyboard.add(moreRow);
            }
        }
        
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder()
            .text("🔙 Назад")
            .callbackData("back_to_knives")
            .build());
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        
        try (InputStream photoStream = yandexDiskService.downloadPhoto(knife.getPhotoPath())) {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoStream, "certificate.jpg"));
            sendPhoto.setCaption(caption.toString());
            sendPhoto.setReplyMarkup(markup);
            
            Message certMessage = execute(sendPhoto);
            state.setCurrentCertificateMessageId(certMessage.getMessageId());
            
            // Устанавливаем сертификат на уровень 2 стека
            navigationStackService.setLevel(userId, 2, certMessage.getMessageId());
            
            conversationStateManager.updateState(userId, state);
        }
    }

    private void showKnifeAlternatives(Long userId, Long chatId, Knife knife) throws Exception {
        ConversationState state = conversationStateManager.getState(userId);
        // Для модели без сертификата показываем только альтернативы с сертификатом
        List<Knife> alternatives = knifeService.getAlternatives(knife.getId());
        
        StringBuilder text = new StringBuilder();
        text.append("🔪 ").append(knife.getDisplayName()).append("\n\n");
        text.append("❌ Сертификат на данную модель отсутствует.\n");
        
        if (!alternatives.isEmpty()) {
            text.append("\nАльтернативные варианты с сертификатом:\n");
            int maxShow = Math.min(2, alternatives.size());
            for (int i = 0; i < maxShow; i++) {
                Knife alt = alternatives.get(i);
                text.append("• ").append(alt.getDisplayName()).append("\n");
            }
            if (alternatives.size() > 2) {
                text.append("...и ещё ").append(alternatives.size() - 2).append(" вариант(ов)");
            }
        }
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Показываем до 2 альтернатив
        int maxShow = Math.min(2, alternatives.size());
        for (int i = 0; i < maxShow; i++) {
            Knife alt = alternatives.get(i);
            String altName = alt.getDisplayName();
            if (altName.length() > 40) {
                altName = altName.substring(0, 37) + "...";
            }
            List<InlineKeyboardButton> altRow = new ArrayList<>();
            altRow.add(InlineKeyboardButton.builder()
                .text(altName)
                .callbackData("knife_" + alt.getId())
                .build());
            keyboard.add(altRow);
        }
        
        // Кнопка [Больше...] если альтернатив > 2
        if (alternatives.size() > 2) {
            List<InlineKeyboardButton> moreRow = new ArrayList<>();
            moreRow.add(InlineKeyboardButton.builder()
                .text("Больше... (" + alternatives.size() + ")")
                .callbackData("alternatives_" + knife.getId())
                .build());
            keyboard.add(moreRow);
        }
        
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder()
            .text("🔙 Назад")
            .callbackData("back_to_knives")
            .build());
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text.toString());
        message.setReplyMarkup(markup);
        
        Message certMessage = execute(message);
        state.setCurrentCertificateMessageId(certMessage.getMessageId());
        
        // Устанавливаем сертификат на уровень 2 стека
        navigationStackService.setLevel(userId, 2, certMessage.getMessageId());
        
        conversationStateManager.updateState(userId, state);
    }


    private void showAlternativesList(Long userId, Long chatId, Long knifeId, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем все сообщения уровня 3 (если есть)
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(userId, 3);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(userId, 3);
            
            // Получаем только альтернативы с сертификатом
            List<Knife> alternatives = knifeService.getAlternatives(knifeId);
            
            int itemsPerPage = 10;
            int totalPages = (int) Math.ceil((double) alternatives.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, alternatives.size());
            List<Knife> pageAlts = alternatives.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔄 Альтернативные сертификаты\n\nВсего: " + alternatives.size());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Отображаем альтернативы по одной в строке
            for (Knife alt : pageAlts) {
                String displayName = alt.getDisplayName();
                if (displayName.length() > 40) {
                    displayName = displayName.substring(0, 37) + "...";
                }
                
                List<InlineKeyboardButton> altRow = new ArrayList<>();
                altRow.add(InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("knife_" + alt.getId())
                    .build());
                keyboard.add(altRow);
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("⬅️")
                    .callbackData("alt_page_" + prevPage + "_knife_" + knifeId)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("alt_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("alt_page_" + nextPage + "_knife_" + knifeId)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("🔙 Назад")
                .callbackData("knife_" + knifeId)
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message altMessage = execute(message);
            state.setCurrentCertificateMessageId(altMessage.getMessageId());
            
            // Устанавливаем список альтернатив на уровень 3 стека
            navigationStackService.setLevel(userId, 3, altMessage.getMessageId());
            
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error showing alternatives list: " + e.getMessage());
        }
    }

    private void backToBrands(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем все сообщения уровня 1 и выше (обрезаем стек до уровня 0)
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(userId, 1);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(userId, 1);
            
            if (state.getMainMenuMessageId() != null) {
                updateMainMenu(userId, chatId, state.getCurrentPage());
            }
            
        } catch (Exception e) {
            logger.severe("Error going back to brands: " + e.getMessage());
        }
    }

    private void backToKnifeList(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем все сообщения уровня 2 и выше (обрезаем стек до уровня 1)
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(userId, 2);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(userId, 2);
            
            state.setCurrentCertificateMessageId(null);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error going back to knife list: " + e.getMessage());
        }
    }

    private void deleteAllExceptMainMenu(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        if (state.getSuccessMessageId() != null) {
            deleteMessage(chatId, state.getSuccessMessageId());
            state.setSuccessMessageId(null);
        }
        
        if (state.getCurrentCertificateMessageId() != null) {
            deleteMessage(chatId, state.getCurrentCertificateMessageId());
            state.setCurrentCertificateMessageId(null);
        }
        
        if (state.getCurrentListMessageId() != null) {
            deleteMessage(chatId, state.getCurrentListMessageId());
            state.setCurrentListMessageId(null);
        }
        
        conversationStateManager.updateState(userId, state);
    }

    private void deleteMessage(Long chatId, Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
        } catch (Exception e) {
            logger.warning("Failed to delete message: " + e.getMessage());
        }
    }

    public void notifyUser(Long chatId, String message) {
        sendMessage(chatId, message);
    }

    private void handleSearchBrands(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
        }
        state.setCurrentStep(ConversationStep.WAITING_FOR_SEARCH_QUERY);
        conversationStateManager.updateState(userId, state);
        sendMessage(chatId, "🔍 Введите название бренда для поиска:");
    }

    private void handleSearchKnives(Long userId, Long chatId, String brandName) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        state.setCurrentStep(ConversationStep.WAITING_FOR_SEARCH_QUERY);
        state.setCurrentBrand(brandName);
        conversationStateManager.updateState(userId, state);
        sendMessage(chatId, "🔍 Введите название модели для поиска:");
    }


    private void handleUploadCertificate(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null)
                return;
            deleteAllExceptMainMenu(userId, chatId);
            String separator = settingsService.getAlternativeSeparator();
            Message sent = sendMessage(chatId, "📤 Загрузка сертификата\n\n" +
                    "Отправьте фото сертификата.\n\n" +
                    "Вы можете:\n" +
                    "• Отправить только фото\n" +
                    "• Отправить фото с подписью в формате:\n" +
                    "  Бренд " + separator + " Название " + separator + " Индекс\n\n" +
                    "После загрузки вы сможете отредактировать все поля.");
            assert sent != null;
            state.setPromptMessageId(sent.getMessageId());
            
            state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling upload certificate: " + e.getMessage());
        }
    }

    private void handleConversationFlow(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        switch (state.getCurrentStep()) {
            case WAITING_FOR_PHOTO:
                if (update.getMessage().hasPhoto()) {
                    handlePhotoSubmission(update);
                }
                break;
            case WAITING_FOR_SEARCH_QUERY:
                if (update.getMessage().hasText()) {
                    handleSearchInput(update);
                }
                break;
            case FORM_WAITING_NAME:
            case FORM_WAITING_BRAND:
            case FORM_WAITING_INDEX:
            case FORM_WAITING_ALT:
                if (update.getMessage().hasText()) {
                    handleFormInput(update);
                }
                break;
        }
    }

    private void handlePhotoSubmission(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        Integer userPhotoMessageId = update.getMessage().getMessageId();
        
        if (!update.getMessage().hasPhoto()) {
            return;
        }
        
        String photoFileId = update.getMessage().getPhoto()
                .stream()
                .max((p1, p2) -> Integer.compare(p1.getFileSize(), p2.getFileSize()))
                .get()
                .getFileId();
        
        String username = update.getMessage().getFrom().getUserName();
        if (username == null) {
            username = update.getMessage().getFrom().getFirstName();
        }
        if (username == null) {
            username = "user_" + userId;
        }
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
        }
        state.setPhotoFileId(photoFileId);
        state.setUsername(username);
        
        String caption = update.getMessage().getCaption();
        if (caption != null && !caption.trim().isEmpty()) {
            parseTemplate(state, caption);
        }
        
        conversationStateManager.updateState(userId, state);
        
        // Удаляем фото пользователя перед созданием формы
        deleteMessage(chatId, userPhotoMessageId);
        
        createSubmissionForm(chatId, userId, photoFileId);
    }

    private void parseTemplate(ConversationState state, String caption) {
        // Требование 1.1–1.7: Используем CaptionParser для парсинга caption
        com.knifecerts.dto.ParsedCaption parsed = captionParser.parse(caption);
        
        if (parsed.brand() != null) {
            state.setBrand(parsed.brand());
        }
        if (parsed.name() != null) {
            state.setName(parsed.name());
        }
        if (parsed.index() != null) {
            state.setIndexCode(parsed.index());
        }
    }

    private void createSubmissionForm(Long chatId, Long userId, String photoFileId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            
            // Удаляем сообщение-справку, если оно есть
            if (state.getPromptMessageId() != null) {
                deleteMessage(chatId, state.getPromptMessageId());
                state.setPromptMessageId(null);
            }
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoFileId));
            sendPhoto.setCaption("📋 Заявка на добавление сертификата\n\nЗаполните поля:");
            
            sendPhoto.setReplyMarkup(buildSubmissionFormKeyboard(state));
            
            Message sentMessage = execute(sendPhoto);
            state.setFormMessageId(sentMessage.getMessageId());
            state.setCurrentStep(ConversationStep.FORM_WAITING_NAME);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error creating form: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при создании формы");
        }
    }

    private InlineKeyboardMarkup buildSubmissionFormKeyboard(ConversationState state) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        String brandText = state.getBrand() != null ? 
            "🏷️ " + (state.getBrand().length() > 20 ? state.getBrand().substring(0, 20) + "..." : state.getBrand()) : 
            "🏷️ Бренд";
        row1.add(InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("form_edit_brand")
            .build());
        keyboard.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        String nameText = state.getName() != null ? 
            "📝 " + (state.getName().length() > 20 ? state.getName().substring(0, 20) + "..." : state.getName()) : 
            "📝 Название";
        row2.add(InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("form_edit_name")
            .build());
        keyboard.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        String indexText = state.getIndexCode() != null ? 
            "🔢 " + state.getIndexCode() : 
            "🔢 Индекс";
        row3.add(InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("form_edit_index")
            .build());
        keyboard.add(row3);
        
        if (state.getAlternatives() != null && !state.getAlternatives().isEmpty()) {
            List<InlineKeyboardButton> separatorRow = new ArrayList<>();
            separatorRow.add(InlineKeyboardButton.builder()
                .text("──── Альтернативы ────")
                .callbackData("alt_separator")
                .build());
            keyboard.add(separatorRow);
            
            for (int i = 0; i < state.getAlternatives().size(); i++) {
                String alt = state.getAlternatives().get(i);
                List<InlineKeyboardButton> altRow = new ArrayList<>();
                String altText = alt.length() > 30 ? alt.substring(0, 27) + "..." : alt;
                altRow.add(InlineKeyboardButton.builder()
                    .text("❌ " + altText)
                    .callbackData("form_remove_alt_" + i)
                    .build());
                keyboard.add(altRow);
            }
        }
        
        List<InlineKeyboardButton> addAltRow = new ArrayList<>();
        addAltRow.add(InlineKeyboardButton.builder()
            .text("➕ Добавить альтернативу")
            .callbackData("form_add_alt")
            .build());
        keyboard.add(addAltRow);
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(InlineKeyboardButton.builder()
            .text("✅ Загрузить")
            .callbackData("form_submit")
            .build());
        actionRow.add(InlineKeyboardButton.builder()
            .text("❌ Закрыть")
            .callbackData("form_close")
            .build());
        keyboard.add(actionRow);
        
        markup.setKeyboard(keyboard);
        return markup;
    }

    private void handleSearchInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String query = update.getMessage().getText();
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        deleteMessage(chatId, update.getMessage().getMessageId());
        
        // Determine if searching brands or models based on current context
        if (state.getCurrentBrand() == null) {
            // Searching brands
            List<Brand> brands = brandRepository.searchByName(query);
            state.setSearchQuery(query);
            state.setSearchResults(brands.stream().map(b -> b.getName()).collect(Collectors.toList()));
            state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
            conversationStateManager.updateState(userId, state);
            
            if (brands.isEmpty()) {
                sendMessage(chatId, "❌ Брендов не найдено");
            } else {
                showSearchResults(userId, chatId, brands.stream().map(b -> (Object)b).collect(Collectors.toList()), "brand", 0);
            }
        } else {
            // Searching models within a brand
            List<KnifeModel> models = knifeModelRepository.searchByName(query);
            state.setSearchQuery(query);
            state.setSearchResults(models.stream().map(m -> m.getName()).collect(Collectors.toList()));
            state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
            conversationStateManager.updateState(userId, state);
            
            if (models.isEmpty()) {
                sendMessage(chatId, "❌ Моделей не найдено");
            } else {
                showSearchResults(userId, chatId, models.stream().map(m -> (Object)m).collect(Collectors.toList()), "model", 0);
            }
        }
    }
    
    private void showSearchResults(Long userId, Long chatId, List<Object> results, String type, int page) {
        try {
            int itemsPerPage = 20;
            int totalPages = (int) Math.ceil((double) results.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, results.size());
            List<Object> pageResults = results.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔍 Результаты поиска (" + results.size() + " найдено)");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (Object result : pageResults) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                String name;
                String callbackData;
                
                if (type.equals("brand")) {
                    Brand brand = (Brand) result;
                    name = brand.getName();
                    callbackData = "brand_" + brand.getName();
                } else {
                    KnifeModel model = (KnifeModel) result;
                    name = model.getName();
                    callbackData = "knife_" + model.getId();
                }
                
                if (name.length() > 40) {
                    name = name.substring(0, 37) + "...";
                }
                
                row.add(InlineKeyboardButton.builder()
                    .text(name)
                    .callbackData(callbackData)
                    .build());
                keyboard.add(row);
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("⬅️")
                    .callbackData("search_page_" + prevPage + "_" + type)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("search_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("search_page_" + nextPage + "_" + type)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("🔙 Назад")
                .callbackData("back_to_brands")
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Error showing search results: " + e.getMessage());
        }
    }
    
    private void handleSearchPageChange(Long userId, Long chatId, int page, String type) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getSearchResults() == null) {
                return;
            }
            
            List<Object> results;
            if (type.equals("brand")) {
                results = state.getSearchResults().stream()
                    .map(name -> (Object) new Brand(name))
                    .collect(Collectors.toList());
            } else {
                results = state.getSearchResults().stream()
                    .map(name -> (Object) new KnifeModel(name))
                    .collect(Collectors.toList());
            }
            
            showSearchResults(userId, chatId, results, type, page);
            
        } catch (Exception e) {
            logger.severe("Error handling search page change: " + e.getMessage());
        }
    }

    private void handleFormInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение пользователя
        deleteMessage(chatId, update.getMessage().getMessageId());
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        switch (state.getCurrentStep()) {
            case FORM_WAITING_BRAND:
                state.setBrand(text);
                break;
            case FORM_WAITING_NAME:
                state.setName(text);
                break;
            case FORM_WAITING_INDEX:
                state.setIndexCode(text);
                break;
            case FORM_WAITING_ALT:
                if (state.getAlternatives() == null) {
                    state.setAlternatives(new ArrayList<>());
                }
                // Требование 2.1–2.5: Используем AlternativesParser для парсинга множественных альтернатив
                List<com.knifecerts.dto.AlternativeEntry> parsedAlternatives = alternativesParser.parse(text);
                
                // Требование 2.7: Отображаем подтверждающее сообщение со списком распарсенных альтернатив
                if (!parsedAlternatives.isEmpty()) {
                    StringBuilder confirmationMsg = new StringBuilder("✅ Добавлено альтернатив: " + parsedAlternatives.size() + "\n\n");
                    for (com.knifecerts.dto.AlternativeEntry entry : parsedAlternatives) {
                        // Требование 2.8: Нормализация пробелов - всегда "Бренд / Название"
                        String normalized = entry.brand() != null 
                            ? entry.brand() + " / " + entry.name()
                            : entry.name();
                        confirmationMsg.append("• ").append(normalized).append("\n");
                        state.getAlternatives().add(normalized);
                    }
                    
                    try {
                        SendMessage confirmMessage = new SendMessage();
                        confirmMessage.setChatId(chatId.toString());
                        confirmMessage.setText(confirmationMsg.toString());
                        Message sent = execute(confirmMessage);
                        
                        // Удаляем подтверждающее сообщение через 3 секунды
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                deleteMessage(chatId, sent.getMessageId());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    } catch (Exception e) {
                        logger.warning("Failed to send confirmation message: " + e.getMessage());
                    }
                }
                break;
        }
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
        conversationStateManager.updateState(userId, state);
        
        updateFormKeyboard(chatId, state);
    }

    private void handleFormEditField(Long userId, Long chatId, String field) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем предыдущее сообщение-запрос, если оно есть
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        String currentValue = "";
        switch (field) {
            case "brand":
                state.setCurrentStep(ConversationStep.FORM_WAITING_BRAND);
                currentValue = state.getBrand() != null ? state.getBrand() : "";
                break;
            case "name":
                state.setCurrentStep(ConversationStep.FORM_WAITING_NAME);
                currentValue = state.getName() != null ? state.getName() : "";
                break;
            case "index":
                state.setCurrentStep(ConversationStep.FORM_WAITING_INDEX);
                currentValue = state.getIndexCode() != null ? state.getIndexCode() : "";
                break;
        }
        
        String fieldName = field.equals("brand") ? "бренда" : 
                          field.equals("name") ? "модели" : "индекса";
        String emoji = field.equals("brand") ? "🏷️" : 
                      field.equals("name") ? "📝" : "🔢";
        
        String promptText = emoji + " Введите название " + fieldName + ":";
        if (!currentValue.isEmpty()) {
            promptText += "\n\nТекущее значение:\n`" + currentValue + "`\n(нажмите для копирования)";
        }
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(promptText);
            message.setParseMode("Markdown");
            
            // Добавляем кнопку "Закрыть"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("❌ Закрыть")
                .callbackData("form_cancel_input")
                .build());
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error sending prompt: " + e.getMessage());
        }
        
        conversationStateManager.updateState(userId, state);
    }

    private void handleFormAddAlternative(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем предыдущее сообщение-запрос, если оно есть
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        String separator = settingsService.getAlternativeSeparator();
        state.setCurrentStep(ConversationStep.FORM_WAITING_ALT);
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("➕ Введите альтернативу в формате:\nБренд " + separator + " Название\n\n" +
                "Можно ввести несколько через запятую:\nБренд1 " + separator + " Название1, Бренд2 " + separator + " Название2");
            
            message.setReplyMarkup(new InlineKeyboardMarkup(List.of(RowBuilder.getRow("❌ Закрыть", "form_cancel_input"))));
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error sending alternative prompt: " + e.getMessage());
        }
        
        conversationStateManager.updateState(userId, state);
    }

    private void handleFormRemoveAlternative(Long userId, Long chatId, int index) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null || state.getAlternatives() == null) {
            return;
        }
        
        if (index >= 0 && index < state.getAlternatives().size()) {
            state.getAlternatives().remove(index);
            conversationStateManager.updateState(userId, state);
            updateFormKeyboard(chatId, state);
        }
    }

    private void handleFormSubmit(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getPhotoFileId() == null) {
                return;
            }
            
            GetFile getFile = new GetFile();
            getFile.setFileId(state.getPhotoFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
            
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
            java.net.URL url = new java.net.URL(fileUrl);
            
            String fileName = state.getUsername() + "_" + System.currentTimeMillis() + ".jpg";
            
            String photoPath;
            try (InputStream photoStream = url.openStream()) {
                photoPath = yandexDiskService.uploadToOffers(photoStream, fileName);
            }
            
            // Создаем заявку в буфере
            SubmissionBuffer submission = submissionBufferService.createSubmission(
                userId,
                state.getUsername(),
                state.getName(),
                state.getBrand(),
                state.getIndexCode(),
                photoPath
            );
            
            // Добавляем альтернативы
            if (state.getAlternatives() != null) {
                for (String altStr : state.getAlternatives()) {
                    List<AlternativeEntry> entries = alternativesParser.parse(altStr);
                    for (AlternativeEntry entry : entries) {
                        submissionBufferService.addAlternativeToSubmission(
                                submission.getId(),
                                entry.name(),
                                entry.brand()
                        );
                    }
                }
            }
            
            // Удаляем форму
            if (state.getFormMessageId() != null) {
                deleteMessage(chatId, state.getFormMessageId());
                state.setFormMessageId(null);
            }
            
            deleteAllExceptMainMenu(userId, chatId);
            
            state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
            state.clearFormData();
            conversationStateManager.updateState(userId, state);
            
            updateMainMenu(userId, chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Error submitting form: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отправке заявки");
        }
    }

    private void handleFormClose(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            // Удаляем предыдущее сообщение-подтверждение, если оно есть
            if (state.getPromptMessageId() != null) {
                deleteMessage(chatId, state.getPromptMessageId());
                state.setPromptMessageId(null);
            }
            
            SendMessage confirmMessage = new SendMessage();
            confirmMessage.setChatId(chatId.toString());
            confirmMessage.setText("❓ Вы уверены, что хотите закрыть форму?\nВсе данные будут потеряны.");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✅ Да")
                .callbackData("form_close_yes")
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Нет")
                .callbackData("form_close_no")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            confirmMessage.setReplyMarkup(markup);
            
            Message sent = execute(confirmMessage);
            state.setPromptMessageId(sent.getMessageId());
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling form close: " + e.getMessage());
        }
    }

    private void handleFormCloseYes(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        // Удаляем форму
        if (state.getFormMessageId() != null) {
            deleteMessage(chatId, state.getFormMessageId());
            state.setFormMessageId(null);
        }
        
        deleteAllExceptMainMenu(userId, chatId);
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
        state.clearFormData();
        conversationStateManager.updateState(userId, state);
    }

    private void handleFormCloseNo(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
            conversationStateManager.updateState(userId, state);
        }
    }
    
    private void handleFormCancelInput(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        // Возвращаем состояние к форме
        state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
        conversationStateManager.updateState(userId, state);
    }

    private void updateFormKeyboard(Long chatId, ConversationState state) {
        try {
            if (state.getFormMessageId() == null) {
                return;
            }
            
            InlineKeyboardMarkup newMarkup = buildSubmissionFormKeyboard(state);
            
            // Проверка, изменилась ли разметка
            if (!isMarkupChanged(state.getFormMessageId(), newMarkup)) {
                logger.fine("Разметка формы не изменилась, пропускаем обновление");
                return;
            }
            
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getFormMessageId());
            editMarkup.setReplyMarkup(newMarkup);
            
            execute(editMarkup);
            
            // Сохраняем текущую разметку
            saveLastMarkup(state.getFormMessageId(), newMarkup);
            
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                logger.fine("Сообщение не требует обновления: " + e.getMessage());
            } else {
                logger.severe("Error updating form keyboard: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.severe("Error updating form keyboard: " + e.getMessage());
        }
    }

    private Message sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            return execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending message: " + e.getMessage());
            return null;
        }
    }
    
    // Методы для проверки и кэширования разметок сообщений
    private boolean isMarkupChanged(Integer messageId, InlineKeyboardMarkup newMarkup) {
        InlineKeyboardMarkup lastMarkup = getLastMarkup(messageId);
        return lastMarkup == null || !markupsEqual(lastMarkup, newMarkup);
    }
    
    private boolean markupsEqual(InlineKeyboardMarkup markup1, InlineKeyboardMarkup markup2) {
        if (markup1 == null && markup2 == null) return true;
        if (markup1 == null || markup2 == null) return false;
        
        List<List<InlineKeyboardButton>> keyboard1 = markup1.getKeyboard();
        List<List<InlineKeyboardButton>> keyboard2 = markup2.getKeyboard();
        
        if (keyboard1.size() != keyboard2.size()) return false;
        
        for (int i = 0; i < keyboard1.size(); i++) {
            List<InlineKeyboardButton> row1 = keyboard1.get(i);
            List<InlineKeyboardButton> row2 = keyboard2.get(i);
            
            if (row1.size() != row2.size()) return false;
            
            for (int j = 0; j < row1.size(); j++) {
                InlineKeyboardButton btn1 = row1.get(j);
                InlineKeyboardButton btn2 = row2.get(j);
                
                if (!Objects.equals(btn1.getText(), btn2.getText()) ||
                    !Objects.equals(btn1.getCallbackData(), btn2.getCallbackData())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private void saveLastMarkup(Integer messageId, InlineKeyboardMarkup markup) {
        lastMarkupCache.put(messageId, markup);
    }
    
    private InlineKeyboardMarkup getLastMarkup(Integer messageId) {
        return lastMarkupCache.get(messageId);
    }
}