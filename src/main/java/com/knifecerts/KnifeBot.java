package com.knifecerts;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

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

import com.knifecerts.model.Brand;
import com.knifecerts.model.ConversationState;
import com.knifecerts.model.ConversationStep;
import com.knifecerts.model.Submission;

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
    private SubmissionService submissionService;
    
    @Autowired
    private BrandService brandService;
    
    @Autowired
    private BotSettingsService botSettingsService;
    
    @Autowired
    private PendingAlternativeService pendingAlternativeService;

    @Autowired
    private ConversationStateManager conversationStateManager;

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
            
            ConversationState state = conversationStateManager.getState(userId);
            if (state != null && state.getMainMenuMessageId() != null) {
                deleteMessage(chatId, state.getMainMenuMessageId());
            }
            
            Message mainMenu = sendMainMenu(chatId);
            
            state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
            state.setMainMenuMessageId(mainMenu.getMessageId());
            state.setCurrentPage(0);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling start: " + e.getMessage());
        }
    }

    private Message sendMainMenu(Long chatId) throws TelegramApiException {
        return sendMainMenuWithPage(chatId, 0);
    }
    
    private Message sendMainMenuWithPage(Long chatId, int page) throws TelegramApiException {
        List<Brand> allBrands = brandService.getBrandsWithApprovedKnives();
        
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
                .callbackData("brand_" + brand.getId())
                .build());
            
            if (currentRow.size() == 3) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }
        
        if (brandService.hasUnbrandedKnives()) {
            List<InlineKeyboardButton> unbrandedRow = new ArrayList<>();
            unbrandedRow.add(InlineKeyboardButton.builder()
                .text("🔪 Ножи без бренда")
                .callbackData("brand_unbranded")
                .build());
            keyboard.add(unbrandedRow);
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
                Long brandId = Long.parseLong(parts[1]);
                updateKnifeList(userId, chatId, brandId, page);
            } else if (data.equals("knife_list_current_page")) {
                // Ignore
            } else if (data.startsWith("knife_")) {
                Long submissionId = Long.parseLong(data.substring(6));
                showCertificate(userId, chatId, submissionId);
            } else if (data.startsWith("alternatives_")) {
                Long submissionId = Long.parseLong(data.substring(13));
                showAlternativesList(userId, chatId, submissionId, 0);
            } else if (data.startsWith("alt_page_")) {
                String[] parts = data.substring(9).split("_sub_");
                int page = Integer.parseInt(parts[0]);
                Long submissionId = Long.parseLong(parts[1]);
                showAlternativesList(userId, chatId, submissionId, page);
            } else if (data.equals("back_to_brands")) {
                backToBrands(userId, chatId);
            } else if (data.equals("back_to_knives")) {
                backToKnifeList(userId, chatId);
            } else if (data.equals("search_brands")) {
                handleSearchBrands(userId, chatId);
            } else if (data.startsWith("search_knives_")) {
                Long brandId = Long.parseLong(data.substring(14));
                handleSearchKnives(userId, chatId, brandId);
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
            } else if (data.startsWith("form_remove_alt_")) {
                int index = Integer.parseInt(data.substring(16));
                handleFormRemoveAlternative(userId, chatId, index);
            } else if (data.equals("form_submit")) {
                handleFormSubmit(userId, chatId);
            } else if (data.equals("form_close")) {
                handleFormClose(userId, chatId);
            } else if (data.equals("form_close_yes")) {
                handleFormCloseYes(userId, chatId);
            } else if (data.equals("form_close_no")) {
                handleFormCloseNo(userId, chatId);
            } else if (data.equals("form_cancel_input")) {
                handleFormCancelInput(userId, chatId);
            }
            
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = 
                new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            execute(answer);
            
        } catch (Exception e) {
            logger.severe("Error handling callback: " + e.getMessage());
            try {
                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = 
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                answer.setText("❌ Ошибка");
                answer.setShowAlert(true);
                execute(answer);
            } catch (TelegramApiException ex) {
                logger.severe("Failed to answer callback: " + ex.getMessage());
            }
        }
    }

    private void updateMainMenu(Long userId, Long chatId, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getMainMenuMessageId() == null) {
                return;
            }
            
            List<Brand> allBrands = brandService.getBrandsWithApprovedKnives();
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
                    .callbackData("brand_" + brand.getId())
                    .build());
                
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            if (brandService.hasUnbrandedKnives()) {
                List<InlineKeyboardButton> unbrandedRow = new ArrayList<>();
                unbrandedRow.add(InlineKeyboardButton.builder()
                    .text("🔪 Ножи без бренда")
                    .callbackData("brand_unbranded")
                    .build());
                keyboard.add(unbrandedRow);
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
            
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getMainMenuMessageId());
            editMarkup.setReplyMarkup(markup);
            
            execute(editMarkup);
            
            state.setCurrentPage(page);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error updating main menu: " + e.getMessage());
        }
    }

    private void handleBrandSelection(Long userId, Long chatId, String brandIdStr) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            deleteAllExceptMainMenu(userId, chatId);
            
            Long brandId = "unbranded".equals(brandIdStr) ? null : Long.parseLong(brandIdStr);
            Brand brand = brandId != null ? brandService.getAllBrands().stream()
                .filter(b -> b.getId().equals(brandId))
                .findFirst().orElse(null) : null;
            
            if (brand == null && brandId != null) {
                return;
            }
            
            Message listMessage = sendKnifeList(chatId, brand, 0);
            state.setCurrentListMessageId(listMessage.getMessageId());
            state.setCurrentBrand(brand != null ? brand.getName() : "Бренд не указан");
            state.setCurrentPage(0);
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Error handling brand selection: " + e.getMessage());
        }
    }


    private Message sendKnifeList(Long chatId, Brand brand, int page) throws TelegramApiException {
        List<Submission> knives = brand != null ? 
            submissionService.getKnifesByBrand(brand) : 
            brandService.getUnbrandedKnives();
        
        int itemsPerPage = 30;
        int totalPages = (int) Math.ceil((double) knives.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        
        page = ((page % totalPages) + totalPages) % totalPages;
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, knives.size());
        List<Submission> pageKnives = knives.subList(startIndex, endIndex);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🏷️ " + (brand != null ? brand.getName() : "Ножи без бренда") + 
            "\n\nВсего: " + knives.size() + " моделей");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int i = 0; i < pageKnives.size(); i++) {
            Submission knife = pageKnives.get(i);
            String displayName = knife.getName() != null ? knife.getName() : "ID:" + knife.getId();
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
            Long brandId = brand != null ? brand.getId() : -1L;
            
            paginationRow.add(InlineKeyboardButton.builder()
                .text("⬅️")
                .callbackData("knife_list_page_" + prevPage + "_brand_" + brandId)
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text(String.format("%d/%d", page + 1, totalPages))
                .callbackData("knife_list_current_page")
                .build());
            paginationRow.add(InlineKeyboardButton.builder()
                .text("➡️")
                .callbackData("knife_list_page_" + nextPage + "_brand_" + brandId)
                .build());
            keyboard.add(paginationRow);
        }
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(InlineKeyboardButton.builder()
            .text("🔍 Поиск")
            .callbackData("search_knives_" + (brand != null ? brand.getId() : "-1"))
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

    private void updateKnifeList(Long userId, Long chatId, Long brandId, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null || state.getCurrentListMessageId() == null) {
                return;
            }
            
            Brand brand = brandId != -1 ? brandService.getAllBrands().stream()
                .filter(b -> b.getId().equals(brandId))
                .findFirst().orElse(null) : null;
            
            List<Submission> knives = brand != null ? 
                submissionService.getKnifesByBrand(brand) : 
                brandService.getUnbrandedKnives();
            
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) knives.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, knives.size());
            List<Submission> pageKnives = knives.subList(startIndex, endIndex);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageKnives.size(); i++) {
                Submission knife = pageKnives.get(i);
                String displayName = knife.getName() != null ? knife.getName() : "ID:" + knife.getId();
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
                    .callbackData("knife_list_page_" + prevPage + "_brand_" + brandId)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("knife_list_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("knife_list_page_" + nextPage + "_brand_" + brandId)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("🔍 Поиск")
                .callbackData("search_knives_" + brandId)
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

    private void showCertificate(Long userId, Long chatId, Long submissionId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            if (state.getCurrentCertificateMessageId() != null) {
                deleteMessage(chatId, state.getCurrentCertificateMessageId());
            }
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                return;
            }
            
            Submission submission = submissionOpt.get();
            List<Submission> alternatives = submissionService.getAlternatives(submissionId);
            
            StringBuilder caption = new StringBuilder();
            caption.append("🔪 ").append(submission.getDisplayName()).append("\n\n");
            if (submission.getBrand() != null) {
                caption.append("🏷️ ").append(submission.getBrand().getName()).append("\n");
            }
            if (submission.getIndexCode() != null) {
                caption.append("🔢 ").append(submission.getIndexCode()).append("\n");
            }
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            if (!alternatives.isEmpty()) {
                String separator = botSettingsService.getSeparator();
                List<InlineKeyboardButton> separatorRow = new ArrayList<>();
                separatorRow.add(InlineKeyboardButton.builder()
                    .text("──── " + separator + " Альтернативы " + separator + " ────")
                    .callbackData("alt_separator")
                    .build());
                keyboard.add(separatorRow);
                
                int maxShow = Math.min(2, alternatives.size());
                for (int i = 0; i < maxShow; i++) {
                    Submission alt = alternatives.get(i);
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
                        .callbackData("alternatives_" + submissionId)
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
            
            if (submission.getPhotoPath() != null) {
                try (InputStream photoStream = yandexDiskService.downloadPhoto(submission.getPhotoPath())) {
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId.toString());
                    sendPhoto.setPhoto(new InputFile(photoStream, "certificate.jpg"));
                    sendPhoto.setCaption(caption.toString());
                    sendPhoto.setReplyMarkup(markup);
                    
                    Message certMessage = execute(sendPhoto);
                    state.setCurrentCertificateMessageId(certMessage.getMessageId());
                    conversationStateManager.updateState(userId, state);
                }
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(caption.toString() + "\n\n⚠️ Фото отсутствует");
                message.setReplyMarkup(markup);
                
                Message certMessage = execute(message);
                state.setCurrentCertificateMessageId(certMessage.getMessageId());
                conversationStateManager.updateState(userId, state);
            }
            
        } catch (Exception e) {
            logger.severe("Error showing certificate: " + e.getMessage());
        }
    }


    private void showAlternativesList(Long userId, Long chatId, Long submissionId, int page) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            if (state.getCurrentCertificateMessageId() != null) {
                deleteMessage(chatId, state.getCurrentCertificateMessageId());
                state.setCurrentCertificateMessageId(null);
            }
            
            List<Submission> alternatives = submissionService.getAlternatives(submissionId);
            
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) alternatives.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, alternatives.size());
            List<Submission> pageAlts = alternatives.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔄 Альтернативные сертификаты\n\nВсего: " + alternatives.size());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageAlts.size(); i++) {
                Submission alt = pageAlts.get(i);
                String displayName = alt.getDisplayName();
                if (displayName.length() > 15) {
                    displayName = displayName.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("knife_" + alt.getId())
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
                    .callbackData("alt_page_" + prevPage + "_sub_" + submissionId)
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("alt_current_page")
                    .build());
                paginationRow.add(InlineKeyboardButton.builder()
                    .text("➡️")
                    .callbackData("alt_page_" + nextPage + "_sub_" + submissionId)
                    .build());
                keyboard.add(paginationRow);
            }
            
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("🔙 Назад")
                .callbackData("knife_" + submissionId)
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message altMessage = execute(message);
            state.setCurrentCertificateMessageId(altMessage.getMessageId());
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
            
            deleteAllExceptMainMenu(userId, chatId);
            
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
            
            if (state.getCurrentCertificateMessageId() != null) {
                deleteMessage(chatId, state.getCurrentCertificateMessageId());
                state.setCurrentCertificateMessageId(null);
                conversationStateManager.updateState(userId, state);
            }
            
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

    private void handleSearchKnives(Long userId, Long chatId, Long brandId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        state.setCurrentStep(ConversationStep.WAITING_FOR_SEARCH_QUERY);
        conversationStateManager.updateState(userId, state);
        sendMessage(chatId, "🔍 Введите название модели для поиска:");
    }


    private void handleUploadCertificate(Long userId, Long chatId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            if (state == null) {
                return;
            }
            
            deleteAllExceptMainMenu(userId, chatId);
            
            String separator = botSettingsService.getSeparator();
            SendMessage helpMessage = new SendMessage();
            helpMessage.setChatId(chatId.toString());
            helpMessage.setText("📤 Загрузка сертификата\n\n" +
                "Отправьте фото сертификата.\n\n" +
                "Вы можете:\n" +
                "• Отправить только фото\n" +
                "• Отправить фото с подписью в формате:\n" +
                "  Бренд " + separator + " Название " + separator + " Индекс\n\n" +
                "После загрузки вы сможете отредактировать все поля.");
            
            Message sent = execute(helpMessage);
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
        String separator = botSettingsService.getSeparator();
        String[] parts = caption.split(java.util.regex.Pattern.quote(separator));
        
        if (parts.length >= 1) {
            state.setBrand(parts[0].trim());
        }
        if (parts.length >= 2) {
            state.setName(parts[1].trim());
        }
        if (parts.length >= 3) {
            state.setIndexCode(parts[2].trim());
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
        
        List<Brand> brands = brandService.searchBrands(query);
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_PHOTO);
        conversationStateManager.updateState(userId, state);
        
        sendMessage(chatId, "Найдено брендов: " + brands.size());
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
                // Поддержка ввода через запятую
                String[] alternatives = text.split(",");
                for (String alt : alternatives) {
                    String trimmed = alt.trim();
                    if (!trimmed.isEmpty()) {
                        state.getAlternatives().add(trimmed);
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
        
        String separator = botSettingsService.getSeparator();
        state.setCurrentStep(ConversationStep.FORM_WAITING_ALT);
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("➕ Введите альтернативу в формате:\nБренд " + separator + " Название\n\n" +
                "Можно ввести несколько через запятую:\nБренд1 " + separator + " Название1, Бренд2 " + separator + " Название2");
            
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
            
            Submission submission = submissionService.createSubmission(
                userId,
                state.getUsername(),
                photoPath,
                state.getName(),
                state.getBrand(),
                state.getIndexCode(),
                state.getAlternatives()
            );
            
            if (state.getAlternatives() != null && !state.getAlternatives().isEmpty()) {
                for (String alt : state.getAlternatives()) {
                    pendingAlternativeService.savePendingAlternative(
                        submission,
                        state.getBrand(),
                        alt
                    );
                }
            }
            
            // Удаляем форму
            if (state.getFormMessageId() != null) {
                deleteMessage(chatId, state.getFormMessageId());
                state.setFormMessageId(null);
            }
            
            deleteAllExceptMainMenu(userId, chatId);
            
            Message thankYou = sendMessage(chatId, "✅ Спасибо! Ваша заявка отправлена на модерацию.");
            state.setSuccessMessageId(thankYou.getMessageId());
            
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
            
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getFormMessageId());
            editMarkup.setReplyMarkup(buildSubmissionFormKeyboard(state));
            
            execute(editMarkup);
            
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
}