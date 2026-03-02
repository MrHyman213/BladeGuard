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
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.knifecerts.model.ConversationState;
import com.knifecerts.model.ConversationStep;
import com.knifecerts.model.Submission;

@Component
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
                
                if (conversationStateManager.hasActiveConversation(userId)) {
                    handleConversationFlow(update);
                } else if (update.getMessage().hasText()) {
                    String messageText = update.getMessage().getText();

                    if (messageText.equals("/start")) {
                        sendMainMenu(chatId);
                    } else if (messageText.equals("/submit")) {
                        startSubmission(userId, chatId);
                    } else if (messageText.equals("/mystatus")) {
                        handleMyStatus(userId, chatId);
                    } else if (messageText.equals("/list")) {
                        handleListPhotos(chatId);
                    } else if (messageText.startsWith("/get ")) {
                        String fileName = messageText.substring(5).trim();
                        handleGetPhoto(chatId, fileName);
                    } else {
                        sendMessage(chatId, "Неизвестная команда. Используйте /start для главного меню.");
                    }
                } else if (update.getMessage().hasPhoto()) {
                    handlePhotoSubmission(update);
                }
            }
        } catch (Exception e) {
            logger.severe("Unexpected error in KnifeBot: " + e.getClass().getName() + " - " + e.getMessage());
            
            // Игнорируем сетевые ошибки Telegram API
            if (e instanceof java.net.UnknownHostException || 
                e instanceof java.net.SocketTimeoutException ||
                e.getCause() instanceof java.net.UnknownHostException ||
                e.getCause() instanceof java.net.SocketTimeoutException) {
                logger.warning("Сетевая ошибка Telegram API (игнорируется): " + e.getMessage());
                return;
            }
            
            e.printStackTrace();
            
            try {
                if (update.hasMessage()) {
                    Long chatId = update.getMessage().getChatId();
                    sendMessage(chatId, "Произошла ошибка. Попробуйте позже.");
                } else if (update.hasCallbackQuery()) {
                    Long chatId = update.getCallbackQuery().getMessage().getChatId();
                    sendMessage(chatId, "Произошла ошибка. Попробуйте позже.");
                }
            } catch (Exception notificationError) {
                logger.severe("Failed to send error message: " + notificationError.getMessage());
            }
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending message: " + e.getMessage());
        }
    }
    
    private void handleCallbackQuery(Update update) {
        var callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();
        
        try {
            if (data.equals("menu_submit")) {
                startSubmission(userId, chatId);
            } else if (data.equals("menu_mystatus")) {
                handleMyStatus(userId, chatId);
            } else if (data.equals("menu_list")) {
                handleListApprovedCertificates(chatId);
            } else if (data.equals("back_to_menu")) {
                sendMainMenu(chatId);
            } else if (data.startsWith("view_submission_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                showMySubmissionDetails(chatId, userId, submissionId);
            } else if (data.equals("form_submit")) {
                handleFormSubmit(userId, chatId);
            } else if (data.equals("form_close")) {
                handleFormClose(userId, chatId);
            } else if (data.equals("form_edit_name")) {
                handleFormEditField(userId, chatId, "name");
            } else if (data.equals("form_edit_brand")) {
                handleFormEditField(userId, chatId, "brand");
            } else if (data.equals("form_edit_index")) {
                handleFormEditField(userId, chatId, "index");
            } else if (data.equals("form_edit_alt")) {
                handleFormEditField(userId, chatId, "alt");
            } else if (data.startsWith("view_cert_")) {
                Long certId = Long.parseLong(data.substring(10));
                handleViewCertificate(chatId, certId);
            } else if (data.startsWith("search_knife_")) {
                String knifeName = data.substring(13);
                handleSearchKnifeByName(chatId, knifeName);
            } else if (data.startsWith("brand_")) {
                String brand = data.substring(6);
                if (brand.startsWith("models_")) {
                    // brand_models_BrandName_page_N
                    String[] parts = brand.split("_page_");
                    String brandName = parts[0].substring(7); // убираем "models_"
                    int page = Integer.parseInt(parts[1]);
                    handleListApprovedCertificates(chatId, page, null, brandName);
                } else {
                    // brand_BrandName
                    handleListApprovedCertificates(chatId, 0, null, brand);
                }
            } else if (data.startsWith("brands_page_")) {
                int page = Integer.parseInt(data.substring(12));
                handleListApprovedCertificates(chatId, page, null, null);
            } else if (data.startsWith("list_page_")) {
                // Парсим: list_page_N или list_page_N_search_query
                String[] parts = data.split("_search_", 2);
                int page = Integer.parseInt(parts[0].substring(10));
                String searchQuery = parts.length > 1 ? parts[1] : null;
                handleListApprovedCertificates(chatId, page, searchQuery);
            } else if (data.equals("list_search")) {
                handleListSearchRequest(userId, chatId);
            } else if (data.equals("list_reset_search")) {
                handleListApprovedCertificates(chatId, 0, null);
            } else if (data.equals("list_current_page")) {
                // Ничего не делаем, это просто индикатор страницы
            }
            
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = 
                new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            execute(answer);
            
        } catch (Exception e) {
            logger.severe("Ошибка при обработке callback: " + e.getMessage());
            try {
                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = 
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                answer.setText("❌ Ошибка");
                answer.setShowAlert(true);
                execute(answer);
            } catch (TelegramApiException ex) {
                logger.severe("Не удалось ответить на callback: " + ex.getMessage());
            }
        }
    }
    
    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔪 Добро пожаловать в систему сертификации ножей!\n\nВыберите действие:");
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new ArrayList<>();
        
        // Первая строка
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("📝 Подать заявку")
            .callbackData("menu_submit")
            .build());
        keyboard.add(row1);
        
        // Вторая строка
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("📊 Мои заявки")
            .callbackData("menu_mystatus")
            .build());
        keyboard.add(row2);
        
        // Третья строка
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("📋 Список моделей")
            .callbackData("menu_list")
            .build());
        keyboard.add(row3);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending menu: " + e.getMessage());
        }
    }
    
    private void startSubmission(Long userId, Long chatId) {
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
        conversationStateManager.updateState(userId, state);
        sendMessage(chatId, "📸 Отправьте фотографию сертификата.");
    }

    private void handleConversationFlow(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (update.getMessage().hasText() && update.getMessage().getText().equals("/cancel")) {
            handleCancelCommand(userId, chatId);
            return;
        }
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        switch (state.getCurrentStep()) {
            case WAITING_FOR_PHOTO:
                handlePhotoSubmission(update);
                break;
            case WAITING_FOR_NAME:
                handleNameInput(update);
                break;
            case WAITING_FOR_BRAND:
                handleBrandInput(update);
                break;
            case WAITING_FOR_INDEX:
                handleIndexInput(update);
                break;
            case WAITING_FOR_ALTERNATIVE_MODELS:
                handleAlternativeModelsInput(update);
                break;
            case WAITING_FOR_SEARCH_QUERY:
                if (update.getMessage().hasText()) {
                    String searchQuery = update.getMessage().getText().trim();
                    conversationStateManager.clearState(userId);
                    
                    // Используем расширенный поиск по моделям ножей
                    var searchResult = submissionService.searchCertificatesByKnifeNameDetailed(searchQuery);
                    
                    if (searchResult.isEmpty()) {
                        // Если ничего не найдено, показываем сообщение
                        SendMessage message = new SendMessage();
                        message.setChatId(chatId.toString());
                        message.setText("🔍 По запросу \"" + searchQuery + "\" ничего не найдено.\n\n" +
                                "Попробуйте изменить запрос или просмотрите полный список.");
                        
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                            new ArrayList<>();
                        
                        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
                        row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("🔍 Новый поиск")
                            .callbackData("list_search")
                            .build());
                        keyboard.add(row1);
                        
                        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
                        row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("📋 Полный список")
                            .callbackData("menu_list")
                            .build());
                        keyboard.add(row2);
                        
                        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row3 = new ArrayList<>();
                        row3.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                            .text("🔙 Главное меню")
                            .callbackData("back_to_menu")
                            .build());
                        keyboard.add(row3);
                        
                        markup.setKeyboard(keyboard);
                        message.setReplyMarkup(markup);
                        
                        try {
                            execute(message);
                        } catch (Exception e) {
                            logger.severe("Ошибка при отправке результатов поиска: " + e.getMessage());
                        }
                    } else {
                        // Показываем результаты поиска с учетом типа совпадения
                        showSearchResults(chatId, searchQuery, searchResult.getSubmissions(), searchResult.isPrimaryMatch());
                    }
                }
                break;
            case FORM_WAITING_NAME:
            case FORM_WAITING_BRAND:
            case FORM_WAITING_INDEX:
            case FORM_WAITING_ALT:
                // Обрабатываем ввод через форму
                if (update.getMessage().hasText()) {
                    handleFormInput(update);
                }
                break;
        }
    }

    private void handlePhotoSubmission(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasPhoto()) {
            sendMessage(chatId, "Пожалуйста, отправьте фотографию сертификата.");
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
        conversationStateManager.updateState(userId, state);
        
        // Создаем форму с кнопками
        createSubmissionForm(chatId, userId, photoFileId);
    }
    
    private void createSubmissionForm(Long chatId, Long userId, String photoFileId) {
        try {
            ConversationState state = conversationStateManager.getState(userId);
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoFileId));
            sendPhoto.setCaption("📋 Заявка на добавление сертификата\n\nЗаполните поля ниже:");
            
            sendPhoto.setReplyMarkup(buildSubmissionFormKeyboard(state));
            
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = execute(sendPhoto);
            state.setFormMessageId(sentMessage.getMessageId());
            state.setCurrentStep(ConversationStep.FORM_WAITING_NAME); // Устанавливаем статус формы
            conversationStateManager.updateState(userId, state);
            
        } catch (Exception e) {
            logger.severe("Ошибка при создании формы: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при создании формы");
        }
    }
    
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup buildSubmissionFormKeyboard(ConversationState state) {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new ArrayList<>();
        
        // Кнопка "Название"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
        String nameText = state.getName() != null ? "🔪 " + state.getName() : "🔪 Название (не указано)";
        row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("form_edit_name")
            .build());
        keyboard.add(row1);
        
        // Кнопка "Бренд"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
        String brandText = state.getBrand() != null 
            ? "🏷️ " + (state.getBrand().length() > 20 ? state.getBrand().substring(0, 20) + "..." : state.getBrand()) 
            : "🏷️ Бренд (не указано)";
        row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("form_edit_brand")
            .build());
        keyboard.add(row2);
        
        // Кнопка "Индекс"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row3 = new ArrayList<>();
        String indexText = state.getIndexCode() != null 
            ? "🔢 " + state.getIndexCode() 
            : "🔢 Индекс (не указано)";
        row3.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("form_edit_index")
            .build());
        keyboard.add(row3);
        
        // Кнопка "Альтернативные модели"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row4 = new ArrayList<>();
        String altText = (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) 
            ? "🔄 " + String.join(", ", state.getAlternativeModels()) 
            : "🔄 Альтернативные модели (не указано)";
        row4.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(altText)
            .callbackData("form_edit_alt")
            .build());
        keyboard.add(row4);
        
        // Кнопки "Отправить" и "Закрыть"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("✅ Отправить заявку")
            .callbackData("form_submit")
            .build());
        row5.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("❌ Закрыть форму")
            .callbackData("form_close")
            .build());
        keyboard.add(row5);
        
        markup.setKeyboard(keyboard);
        return markup;
    }

    private void handleNameInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите название или отправьте /skip");
            return;
        }

        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);

        if (text.equals("/skip")) {
            state.setName(null);
        } else {
            state.setName(text);
        }

        state.setCurrentStep(ConversationStep.WAITING_FOR_BRAND);
        conversationStateManager.updateState(userId, state);

        sendMessage(chatId, "Введите бренд ножа или отправьте /skip, чтобы пропустить:");
    }

    private void handleBrandInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите бренд или отправьте /skip");
            return;
        }
        
        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);
        
        if (text.equals("/skip")) {
            state.setBrand(null);
        } else {
            state.setBrand(text);
        }
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_INDEX);
        conversationStateManager.updateState(userId, state);
        
        sendMessage(chatId, "Введите индекс ножа или отправьте /skip, чтобы пропустить:");
    }

    private void handleIndexInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите индекс или отправьте /skip");
            return;
        }
        
        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);
        
        if (text.equals("/skip")) {
            state.setIndexCode(null);
        } else {
            state.setIndexCode(text);
        }
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_ALTERNATIVE_MODELS);
        conversationStateManager.updateState(userId, state);
        
        sendMessage(chatId, "К каким моделям ножей мог бы также подойти данный сертификат?\n\n" +
                "Укажите через запятую или отправьте /skip");
    }

    private void handleAlternativeModelsInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите модели через запятую или отправьте /skip");
            return;
        }
        
        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);
        
        if (text.equals("/skip")) {
            state.setAlternativeModels(null);
        } else {
            // Парсинг моделей: разделить по запятой, убрать пробелы
            List<String> models = java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
            state.setAlternativeModels(models);
        }
        
        conversationStateManager.updateState(userId, state);
        finalizeSubmission(userId, chatId);
    }

    private void handleCancelCommand(Long userId, Long chatId) {
        conversationStateManager.clearState(userId);
        sendMessage(chatId, "Заявка отменена.");
    }

    private void finalizeSubmission(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            sendMessage(chatId, "Ошибка: состояние диалога не найдено");
            return;
        }
        
        try {
            sendMessage(chatId, "Обрабатываю заявку...");
            
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(state.getPhotoFileId());
            org.telegram.telegrambots.meta.api.objects.File tgFile = execute(getFileMethod);
            java.io.File photoFile = downloadFile(tgFile);
            
            // Используем название для имени файла
            String name = state.getName() != null ? state.getName() : "unknown";
            // Очищаем название от недопустимых символов
            String sanitizedName = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_-]", "_");
            String fileName = sanitizedName + "_" + state.getUsername() + "_" + System.currentTimeMillis() + ".jpg";
            String yandexPath;
            
            try (InputStream photoStream = new java.io.FileInputStream(photoFile)) {
                yandexPath = yandexDiskService.uploadToOffers(photoStream, fileName);
            }
            
            Submission submission = submissionService.createSubmission(
                userId,
                state.getUsername(),
                yandexPath,
                state.getName(),
                state.getBrand(),
                state.getAlternativeModels()
            );
            
            conversationStateManager.clearState(userId);
            photoFile.delete();
            
            SendMessage successMessage = new SendMessage();
            successMessage.setChatId(chatId.toString());
            successMessage.setText("✅ Заявка успешно отправлена!\n\n" +
                    "📋 ID заявки: " + submission.getId() + "\n" +
                    "📊 Статус: ⏳ На модерации\n\n" +
                    "Вы получите уведомление, когда заявка будет рассмотрена.");
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("📊 Мои заявки")
                .callbackData("menu_mystatus")
                .build());
            keyboard.add(row1);
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row2);
            
            markup.setKeyboard(keyboard);
            successMessage.setReplyMarkup(markup);
            
            execute(successMessage);
            
        } catch (Exception e) {
            logger.severe("Error finalizing submission: " + e.getMessage());
            sendMessage(chatId, "Произошла ошибка при обработке заявки. Попробуйте позже.");
            conversationStateManager.clearState(userId);
        }
    }

    public void notifyUser(Long userId, String message) {
        sendMessage(userId, message);
    }

    private void handleMyStatus(Long userId, Long chatId) {
        try {
            List<Submission> submissions = submissionService.getSubmissionsByUserId(userId);
            
            if (submissions.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("📭 У вас пока нет заявок.\n\nИспользуйте кнопку ниже для подачи заявки.");
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                    new ArrayList<>();
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("📝 Подать заявку")
                    .callbackData("menu_submit")
                    .build());
                keyboard.add(row1);
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row2);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                execute(message);
                return;
            }
            
            sendMessage(chatId, "📊 Ваши заявки:");
            
            for (Submission submission : submissions) {
                StringBuilder text = new StringBuilder();
                text.append("📋 Заявка #").append(submission.getId()).append("\n");
                text.append("🔪 Модель: ").append(submission.getDisplayName()).append("\n");
                text.append("📅 Дата: ").append(submission.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
                text.append("📊 Статус: ");
                
                switch (submission.getStatus()) {
                    case PENDING:
                        text.append("⏳ На модерации");
                        break;
                    case APPROVED:
                        text.append("✅ Одобрена");
                        break;
                    case REJECTED:
                        text.append("❌ Отклонена");
                        if (submission.getRejectionReason() != null) {
                            text.append("\n❗ Причина: ").append(submission.getRejectionReason());
                        }
                        break;
                }
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(text.toString());
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                    new ArrayList<>();
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("👁️ Подробнее")
                    .callbackData("view_submission_" + submission.getId())
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                execute(message);
            }
            
            // Кнопка возврата в меню
            SendMessage menuButton = new SendMessage();
            menuButton.setChatId(chatId.toString());
            menuButton.setText("—");
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
            row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            menuButton.setReplyMarkup(markup);
            
            execute(menuButton);
            
        } catch (Exception e) {
            logger.severe("Error getting user status: " + e.getMessage());
            sendMessage(chatId, "Ошибка при получении статуса заявок");
        }
    }
    
    private void showMySubmissionDetails(Long chatId, Long userId, Long submissionId) {
        try {
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty() || !submissionOpt.get().getUserId().equals(userId)) {
                sendMessage(chatId, "❌ Заявка не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            StringBuilder text = new StringBuilder();
            text.append("📋 Детали заявки #").append(submission.getId()).append("\n\n");
            text.append("🔪 Название: ").append(submission.getName() != null ? submission.getName() : "не указано").append("\n");
            text.append("🏷️ Бренд: ").append(submission.getBrand() != null ? submission.getBrand() : "не указан").append("\n");
            text.append("🔢 Индекс: ").append(submission.getIndexCode() != null ? submission.getIndexCode() : "не указан").append("\n");
            
            text.append("\n📅 Дата подачи: ").append(submission.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
            text.append("📊 Статус: ");
            
            switch (submission.getStatus()) {
                case PENDING:
                    text.append("⏳ На модерации");
                    break;
                case APPROVED:
                    text.append("✅ Одобрена");
                    if (submission.getModeratedAt() != null) {
                        text.append("\n✅ Одобрена: ").append(submission.getModeratedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                    }
                    break;
                case REJECTED:
                    text.append("❌ Отклонена");
                    if (submission.getRejectionReason() != null) {
                        text.append("\n❗ Причина: ").append(submission.getRejectionReason());
                    }
                    if (submission.getModeratedAt() != null) {
                        text.append("\n📅 Отклонена: ").append(submission.getModeratedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                    }
                    break;
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
            row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 К списку заявок")
                .callbackData("menu_mystatus")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }

    private void handleListPhotos(Long chatId) {
        try {
            sendMessage(chatId, "Получаю список фотографий...");

            JsonNode response = yandexDiskService.listPhotos();
            JsonNode items = response.get("_embedded").get("items");

            if (items == null || items.size() == 0) {
                sendMessage(chatId, "Папка пуста");
                return;
            }

            StringBuilder message = new StringBuilder("Фотографии на Яндекс.Диске:\n\n");
            int count = 0;

            for (JsonNode item : items) {
                String name = item.get("name").asText();
                message.append(name).append("\n");
                count++;

                if (count >= 20) {
                    message.append("\n... и еще ").append(items.size() - 20).append(" файлов");
                    break;
                }
            }

            message.append("\n\nИспользуйте /get <имя_файла> для получения фото");
            sendMessage(chatId, message.toString());

        } catch (Exception e) {
            logger.severe("Error listing photos: " + e.getMessage());
            sendMessage(chatId, "Ошибка при получении списка фотографий");
        }
    }

    private void handleGetPhoto(Long chatId, String fileName) {
        try {
            sendMessage(chatId, "Загружаю фотографию...");

            String filePath = yandexDiskService.getFolder() + "/" + fileName;

            try (InputStream photoStream = yandexDiskService.downloadPhoto(filePath)) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new InputFile(photoStream, fileName));
                sendPhoto.setCaption(fileName);

                execute(sendPhoto);
            }

        } catch (Exception e) {
            logger.severe("Error getting photo: " + e.getMessage());
            sendMessage(chatId, "Ошибка при получении фотографии. Проверьте имя файла.");
        }
    }
    
    private void handleFormInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null || state.getFormMessageId() == null) {
            return;
        }
        
        try {
            // Обрабатываем ввод в зависимости от текущего шага
            switch (state.getCurrentStep()) {
                case FORM_WAITING_NAME:
                    state.setName(text.trim().isEmpty() ? null : text.trim());
                    break;
                case FORM_WAITING_BRAND:
                    state.setBrand(text.trim().isEmpty() ? null : text.trim());
                    break;
                case FORM_WAITING_INDEX:
                    state.setIndexCode(text.trim().isEmpty() ? null : text.trim());
                    break;
                case FORM_WAITING_ALT:
                    if (text.trim().isEmpty()) {
                        state.setAlternativeModels(null);
                    } else {
                        List<String> models = java.util.Arrays.stream(text.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                        state.setAlternativeModels(models.isEmpty() ? null : models);
                    }
                    break;
                default:
                    return;
            }
            
            // Возвращаем статус обратно в режим формы
            state.setCurrentStep(ConversationStep.FORM_WAITING_NAME);
            conversationStateManager.updateState(userId, state);
            
            // Удаляем сообщение пользователя
            try {
                org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage deleteMessage = 
                    new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
                deleteMessage.setChatId(chatId.toString());
                deleteMessage.setMessageId(update.getMessage().getMessageId());
                execute(deleteMessage);
            } catch (Exception e) {
                logger.warning("Не удалось удалить сообщение пользователя: " + e.getMessage());
            }
            
            // Удаляем сообщение-запрос и скрываем клавиатуру
            if (state.getPromptMessageId() != null) {
                try {
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage deletePrompt = 
                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
                    deletePrompt.setChatId(chatId.toString());
                    deletePrompt.setMessageId(state.getPromptMessageId());
                    execute(deletePrompt);
                    state.setPromptMessageId(null);
                    conversationStateManager.updateState(userId, state);
                } catch (Exception e) {
                    logger.warning("Не удалось удалить сообщение-запрос: " + e.getMessage());
                }
            }
            
            // Скрываем клавиатуру
            try {
                SendMessage hideKeyboard = new SendMessage();
                hideKeyboard.setChatId(chatId.toString());
                hideKeyboard.setText("✅");
                org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove remove = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove();
                remove.setRemoveKeyboard(true);
                remove.setSelective(true);
                hideKeyboard.setReplyMarkup(remove);
                org.telegram.telegrambots.meta.api.objects.Message msg = execute(hideKeyboard);
                
                // Сразу удаляем это сообщение
                org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage deleteMsg = 
                    new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(msg.getMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Не удалось скрыть клавиатуру: " + e.getMessage());
            }
            
            // Обновляем кнопки формы
            updateSubmissionForm(chatId, state);
            
        } catch (Exception e) {
            logger.severe("Ошибка при обработке ввода формы: " + e.getMessage());
        }
    }
    
    private void handleFormEditField(Long userId, Long chatId, String field) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            return;
        }
        
        String prompt = "";
        String currentValue = "";
        ConversationStep newStep = null;
        
        switch (field) {
            case "name":
                prompt = "🔪 Введите название модели ножа:";
                currentValue = state.getName() != null ? state.getName() : "";
                newStep = ConversationStep.FORM_WAITING_NAME;
                break;
            case "brand":
                prompt = "🏷️ Введите бренд ножа:";
                currentValue = state.getBrand() != null ? state.getBrand() : "";
                newStep = ConversationStep.FORM_WAITING_BRAND;
                break;
            case "index":
                prompt = "🔢 Введите индекс ножа:";
                currentValue = state.getIndexCode() != null ? state.getIndexCode() : "";
                newStep = ConversationStep.FORM_WAITING_INDEX;
                break;
            case "alt":
                prompt = "🔄 Введите альтернативные модели через запятую:";
                currentValue = (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) 
                    ? String.join(", ", state.getAlternativeModels()) 
                    : "";
                newStep = ConversationStep.FORM_WAITING_ALT;
                break;
        }
        
        if (newStep != null) {
            state.setCurrentStep(newStep);
            conversationStateManager.updateState(userId, state);
            
            try {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                
                if (!currentValue.isEmpty()) {
                    message.setText(prompt + "\n\n📋 Текущее значение:\n`" + currentValue + "`\n\n💡 Отправьте новое значение или пустое сообщение для очистки");
                    message.setParseMode("Markdown");
                } else {
                    message.setText(prompt + "\n\n💡 Отправьте значение или пустое сообщение для пропуска");
                }
                
                org.telegram.telegrambots.meta.api.objects.Message sentMessage = execute(message);
                
                // Сохраняем ID сообщения-запроса для последующего удаления
                state.setPromptMessageId(sentMessage.getMessageId());
                conversationStateManager.updateState(userId, state);
                
            } catch (Exception e) {
                logger.severe("Ошибка при отправке запроса поля: " + e.getMessage());
            }
        }
    }
    
    private void updateSubmissionForm(Long chatId, ConversationState state) {
        try {
            org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup editMarkup = 
                new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup();
            editMarkup.setChatId(chatId.toString());
            editMarkup.setMessageId(state.getFormMessageId());
            editMarkup.setReplyMarkup(buildSubmissionFormKeyboard(state));
            
            execute(editMarkup);
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении формы: " + e.getMessage());
        }
    }
    
    private void handleFormSubmit(Long userId, Long chatId) {
        ConversationState state = conversationStateManager.getState(userId);
        if (state == null) {
            sendMessage(chatId, "❌ Ошибка: состояние не найдено");
            return;
        }
        
        try {
            sendMessage(chatId, "⏳ Обрабатываю заявку...");
            
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(state.getPhotoFileId());
            org.telegram.telegrambots.meta.api.objects.File tgFile = execute(getFileMethod);
            java.io.File photoFile = downloadFile(tgFile);
            
            String name = state.getName() != null ? state.getName() : "unknown";
            String sanitizedName = name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_-]", "_");
            String fileName = sanitizedName + "_" + state.getUsername() + "_" + System.currentTimeMillis() + ".jpg";
            String yandexPath;
            
            try (InputStream photoStream = new java.io.FileInputStream(photoFile)) {
                yandexPath = yandexDiskService.uploadToOffers(photoStream, fileName);
            }
            
            Submission submission = submissionService.createSubmission(
                userId,
                state.getUsername(),
                yandexPath,
                state.getName(),
                state.getBrand(),
                state.getAlternativeModels()
            );
            
            conversationStateManager.clearState(userId);
            photoFile.delete();
            
            SendMessage successMessage = new SendMessage();
            successMessage.setChatId(chatId.toString());
            successMessage.setText("✅ Заявка успешно отправлена!\n\n" +
                    "📋 ID заявки: " + submission.getId() + "\n" +
                    "📊 Статус: ⏳ На модерации\n\n" +
                    "Вы получите уведомление, когда заявка будет рассмотрена.");
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("📊 Мои заявки")
                .callbackData("menu_mystatus")
                .build());
            keyboard.add(row1);
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row2);
            
            markup.setKeyboard(keyboard);
            successMessage.setReplyMarkup(markup);
            
            execute(successMessage);
            
        } catch (Exception e) {
            logger.severe("Error finalizing submission: " + e.getMessage());
            sendMessage(chatId, "❌ Произошла ошибка при обработке заявки. Попробуйте позже.");
            conversationStateManager.clearState(userId);
        }
    }
    
    private void handleFormClose(Long userId, Long chatId) {
        conversationStateManager.clearState(userId);
        sendMessage(chatId, "❌ Форма закрыта. Данные не сохранены.");
        sendMainMenu(chatId);
    }
    
    private void handleListApprovedCertificates(Long chatId) {
        handleListApprovedCertificates(chatId, 0, null, null);
    }
    
    private void handleListApprovedCertificates(Long chatId, int page, String searchQuery) {
        handleListApprovedCertificates(chatId, page, searchQuery, null);
    }
    
    private void handleListApprovedCertificates(Long chatId, int page, String searchQuery, String selectedBrand) {
        try {
            // Если бренд не выбран, показываем список брендов
            if (selectedBrand == null) {
                showBrandsList(chatId, page, searchQuery);
            } else {
                // Если бренд выбран, показываем модели этого бренда
                showKnifeNamesByBrand(chatId, selectedBrand, page, searchQuery);
            }
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка");
        }
    }
    
    /**
     * Показывает список брендов.
     */
    private void showBrandsList(Long chatId, int page, String searchQuery) {
        try {
            List<String> allBrands;
            
            // Используем поиск или получаем все бренды
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                allBrands = submissionService.getAllApprovedBrands().stream()
                    .filter(brand -> brand.toLowerCase().contains(searchQuery.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
            } else {
                allBrands = submissionService.getAllApprovedBrands();
            }
            
            if (allBrands.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(searchQuery != null 
                    ? "🔍 По запросу \"" + searchQuery + "\" ничего не найдено."
                    : "📭 Пока нет одобренных сертификатов.");
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                    new ArrayList<>();
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                execute(message);
                return;
            }
            
            // Пагинация: 10 строк по 2 кнопки = 20 элементов на страницу (для брендов)
            int itemsPerPage = 20;
            int totalPages = (int) Math.ceil((double) allBrands.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allBrands.size());
            
            List<String> pageItems = allBrands.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            text.append("🏷️ Выберите бренд:\n\n");
            text.append("Найдено: ").append(allBrands.size()).append(" брендов\n");
            if (totalPages > 1) {
                text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            // Кнопки с брендами (2 колонки)
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                String brand = pageItems.get(i);
                
                // Обрезаем длинные названия
                String displayName = brand;
                if (displayName.length() > 20) {
                    displayName = displayName.substring(0, 17) + "...";
                }
                
                currentRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("brand_" + brand)
                    .build());
                
                // Добавляем строку после 2 кнопок
                if (currentRow.size() == 2) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            // Добавляем последнюю неполную строку
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            // Кнопки пагинации
            if (totalPages > 1) {
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                if (page > 0) {
                    paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("brands_page_" + (page - 1))
                        .build());
                }
                
                paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("list_current_page")
                    .build());
                
                if (page < totalPages - 1) {
                    paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("Вперёд ➡️")
                        .callbackData("brands_page_" + (page + 1))
                        .build());
                }
                
                keyboard.add(paginationRow);
            }
            
            // Кнопка возврата в меню
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отображении брендов: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении брендов");
        }
    }
    
    /**
     * Показывает список моделей ножей конкретного бренда.
     */
    private void showKnifeNamesByBrand(Long chatId, String brand, int page, String searchQuery) {
        try {
            List<String> allKnifeNames = submissionService.getApprovedKnifeNamesByBrand(brand);
            
            if (allKnifeNames.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("📭 У бренда \"" + brand + "\" пока нет сертификатов.");
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                    new ArrayList<>();
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("🔙 К брендам")
                    .callbackData("menu_list")
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                execute(message);
                return;
            }
            
            // Пагинация: 10 строк по 3 кнопки = 30 элементов на страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) allKnifeNames.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allKnifeNames.size());
            
            List<String> pageItems = allKnifeNames.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            text.append("🏷️ Бренд: ").append(brand).append("\n\n");
            text.append("📋 Выберите модель:\n\n");
            text.append("Найдено: ").append(allKnifeNames.size()).append(" моделей\n");
            if (totalPages > 1) {
                text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            // Кнопки с моделями (3 колонки)
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                String knifeName = pageItems.get(i);
                
                // Обрезаем длинные названия
                String displayName = knifeName;
                if (displayName.length() > 15) {
                    displayName = displayName.substring(0, 12) + "...";
                }
                
                currentRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("search_knife_" + knifeName)
                    .build());
                
                // Добавляем строку после 3 кнопок
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            // Добавляем последнюю неполную строку
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            // Кнопки пагинации
            if (totalPages > 1) {
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                if (page > 0) {
                    String prevCallback = "brand_models_" + brand + "_page_" + (page - 1);
                    paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData(prevCallback)
                        .build());
                }
                
                paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("list_current_page")
                    .build());
                
                if (page < totalPages - 1) {
                    String nextCallback = "brand_models_" + brand + "_page_" + (page + 1);
                    paginationRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("Вперёд ➡️")
                        .callbackData(nextCallback)
                        .build());
                }
                
                keyboard.add(paginationRow);
            }
            
            // Кнопка возврата к брендам
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 К брендам")
                .callbackData("menu_list")
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отображении моделей бренда: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении моделей");
        }
    }
    
    private void handleViewCertificate(Long chatId, Long certId) {
        try {
            Submission submission = submissionService.getSubmissionById(certId).orElse(null);
            
            if (submission == null) {
                sendMessage(chatId, "❌ Сертификат не найден");
                return;
            }
            
            StringBuilder text = new StringBuilder();
            text.append("🔪 ").append(submission.getDisplayName()).append("\n\n");
            
            if (submission.getBrand() != null && !submission.getBrand().isEmpty()) {
                text.append("🏷️ Бренд: ").append(submission.getBrand()).append("\n");
            }
            
            // Загружаем фото с Yandex.Disk и отправляем
            try {
                String photoPath = submission.getPhotoPath();
                try (java.io.InputStream photoStream = yandexDiskService.downloadPhoto(photoPath)) {
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId.toString());
                    sendPhoto.setPhoto(new InputFile(photoStream, photoPath.substring(photoPath.lastIndexOf('/') + 1)));
                    sendPhoto.setCaption(text.toString());
                    
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                        new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                    List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                        new ArrayList<>();
                    
                    List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                    row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                        .text("🔙 К списку")
                        .callbackData("menu_list")
                        .build());
                    keyboard.add(row);
                    
                    markup.setKeyboard(keyboard);
                    sendPhoto.setReplyMarkup(markup);
                    
                    execute(sendPhoto);
                }
            } catch (Exception photoEx) {
                // Если не удалось загрузить фото, отправляем только текст
                logger.warning("Не удалось загрузить фото: " + photoEx.getMessage());
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(text.toString() + "\n\n⚠️ Фото временно недоступно");
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                    new ArrayList<>();
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("🔙 К списку")
                    .callbackData("menu_list")
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                execute(message);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при загрузке сертификата");
        }
    }
    
    private void handleListSearchRequest(Long userId, Long chatId) {
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_SEARCH_QUERY);
        conversationStateManager.updateState(userId, state);
        sendMessage(chatId, "🔍 Введите название модели для поиска:");
    }
    
    /**
     * Обрабатывает поиск сертификата по названию ножа из списка.
     * 
     * @param chatId ID чата
     * @param knifeName название ножа
     */
    private void handleSearchKnifeByName(Long chatId, String knifeName) {
        try {
            var searchResult = submissionService.searchCertificatesByKnifeNameDetailed(knifeName);
            
            if (searchResult.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат не найден");
                return;
            }
            
            List<Submission> results = searchResult.getSubmissions();
            
            if (searchResult.isPrimaryMatch() && results.size() == 1) {
                // Точное совпадение - показываем сертификат
                handleViewCertificate(chatId, results.get(0).getId());
            } else {
                // Показываем список альтернатив
                showAlternativesList(chatId, knifeName, results, searchResult.isPrimaryMatch());
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при поиске по названию: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }
    
    /**
     * Показывает список альтернативных сертификатов.
     * 
     * @param chatId ID чата
     * @param knifeName название ножа
     * @param alternatives список альтернативных сертификатов
     * @param isPrimaryMatch true если найден точный сертификат
     */
    private void showAlternativesList(Long chatId, String knifeName, List<Submission> alternatives, boolean isPrimaryMatch) {
        try {
            StringBuilder text = new StringBuilder();
            
            if (isPrimaryMatch) {
                text.append("✅ Найдено несколько сертификатов на модель \"").append(knifeName).append("\":\n\n");
            } else {
                text.append("⚠️ К сожалению, сертификата именно на модель \"").append(knifeName).append("\" нет в системе.\n\n");
                text.append("Но мы можем предложить альтернативные варианты:\n\n");
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            // Показываем до 10 альтернатив
            int maxAlternatives = Math.min(alternatives.size(), 10);
            for (int i = 0; i < maxAlternatives; i++) {
                Submission alt = alternatives.get(i);
                String displayName = alt.getDisplayName();
                
                if (displayName.length() > 40) {
                    displayName = displayName.substring(0, 37) + "...";
                }
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("view_cert_" + alt.getId())
                    .build());
                keyboard.add(row);
            }
            
            if (alternatives.size() > 10) {
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> moreRow = new ArrayList<>();
                moreRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("... и еще " + (alternatives.size() - 10) + " вариантов")
                    .callbackData("list_current_page")
                    .build());
                keyboard.add(moreRow);
            }
            
            // Кнопка возврата
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 К списку")
                .callbackData("menu_list")
                .build());
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отображении альтернатив: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении альтернатив");
        }
    }
    
    /**
     * Показывает результаты расширенного поиска по моделям ножей.
     * 
     * @param chatId ID чата
     * @param searchQuery поисковый запрос
     * @param results список найденных сертификатов
     * @param isPrimaryMatch true если найден точный сертификат, false если альтернативы
     */
    private void showSearchResults(Long chatId, String searchQuery, List<Submission> results, boolean isPrimaryMatch) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("🔍 Результаты поиска: \"").append(searchQuery).append("\"\n\n");
            
            if (isPrimaryMatch) {
                // Найден точный сертификат на эту модель
                text.append("✅ Найден сертификат на эту модель!\n");
                text.append("Найдено: ").append(results.size()).append(" сертификат(ов)");
            } else {
                // Найдены только альтернативы
                text.append("⚠️ К сожалению, сертификата именно на модель \"").append(searchQuery).append("\" нет в системе.\n\n");
                text.append("Но мы можем предложить альтернативные варианты:\n");
                text.append("Найдено: ").append(results.size()).append(" подходящих сертификат(ов)");
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
                new ArrayList<>();
            
            // Показываем до 10 результатов
            int maxResults = Math.min(results.size(), 10);
            for (int i = 0; i < maxResults; i++) {
                Submission sub = results.get(i);
                String displayName = sub.getDisplayName();
                
                // Обрезаем длинные названия
                if (displayName.length() > 40) {
                    displayName = displayName.substring(0, 37) + "...";
                }
                
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new ArrayList<>();
                row.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text(displayName)
                    .callbackData("view_cert_" + sub.getId())
                    .build());
                keyboard.add(row);
            }
            
            if (results.size() > 10) {
                List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> moreRow = new ArrayList<>();
                moreRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                    .text("... и еще " + (results.size() - 10) + " результатов")
                    .callbackData("list_current_page")
                    .build());
                keyboard.add(moreRow);
            }
            
            // Кнопки навигации
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> searchRow = new ArrayList<>();
            searchRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔍 Новый поиск")
                .callbackData("list_search")
                .build());
            keyboard.add(searchRow);
            
            List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отображении результатов поиска: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении результатов");
        }
    }
}