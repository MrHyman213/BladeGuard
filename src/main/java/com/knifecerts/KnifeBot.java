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
            } else if (data.equals("form_edit_model")) {
                handleFormEditField(userId, chatId, "model");
            } else if (data.equals("form_edit_desc")) {
                handleFormEditField(userId, chatId, "desc");
            } else if (data.equals("form_edit_alt")) {
                handleFormEditField(userId, chatId, "alt");
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
            case WAITING_FOR_MODEL_NAME:
                handleModelNameInput(update);
                break;
            case WAITING_FOR_DESCRIPTION:
                handleDescriptionInput(update);
                break;
            case WAITING_FOR_ALTERNATIVE_MODELS:
                handleAlternativeModelsInput(update);
                break;
            case FORM_WAITING_MODEL:
            case FORM_WAITING_DESC:
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
            state.setCurrentStep(ConversationStep.FORM_WAITING_MODEL); // Устанавливаем статус формы
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
        String modelText = state.getModelName() != null ? "🔪 " + state.getModelName() : "🔪 Название (не указано)";
        row1.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(modelText)
            .callbackData("form_edit_model")
            .build());
        keyboard.add(row1);
        
        // Кнопка "Описание"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new ArrayList<>();
        String descText = state.getDescription() != null 
            ? "📝 " + (state.getDescription().length() > 20 ? state.getDescription().substring(0, 20) + "..." : state.getDescription()) 
            : "📝 Описание (не указано)";
        row2.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(descText)
            .callbackData("form_edit_desc")
            .build());
        keyboard.add(row2);
        
        // Кнопка "Альтернативные модели"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row3 = new ArrayList<>();
        String altText = (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) 
            ? "🔄 " + String.join(", ", state.getAlternativeModels()) 
            : "🔄 Альтернативные модели (не указано)";
        row3.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text(altText)
            .callbackData("form_edit_alt")
            .build());
        keyboard.add(row3);
        
        // Кнопки "Отправить" и "Закрыть"
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("✅ Отправить заявку")
            .callbackData("form_submit")
            .build());
        row4.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("❌ Закрыть форму")
            .callbackData("form_close")
            .build());
        keyboard.add(row4);
        
        markup.setKeyboard(keyboard);
        return markup;
    }

    private void handleModelNameInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите название модели или отправьте /skip");
            return;
        }
        
        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);
        
        if (text.equals("/skip")) {
            state.setModelName(null);
        } else {
            state.setModelName(text);
        }
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_DESCRIPTION);
        conversationStateManager.updateState(userId, state);
        
        sendMessage(chatId, "Введите описание сертификата или отправьте /skip, чтобы пропустить:");
    }

    private void handleDescriptionInput(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!update.getMessage().hasText()) {
            sendMessage(chatId, "Пожалуйста, введите описание или отправьте /skip");
            return;
        }
        
        String text = update.getMessage().getText();
        ConversationState state = conversationStateManager.getState(userId);
        
        if (text.equals("/skip")) {
            state.setDescription(null);
        } else {
            state.setDescription(text);
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
            
            // Используем название модели для имени файла
            String modelName = state.getModelName() != null ? state.getModelName() : "unknown";
            // Очищаем название от недопустимых символов
            String sanitizedModelName = modelName.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_-]", "_");
            String fileName = sanitizedModelName + "_" + state.getUsername() + "_" + System.currentTimeMillis() + ".jpg";
            String yandexPath;
            
            try (InputStream photoStream = new java.io.FileInputStream(photoFile)) {
                yandexPath = yandexDiskService.uploadToOffers(photoStream, fileName);
            }
            
            Submission submission = submissionService.createSubmission(
                userId,
                state.getUsername(),
                yandexPath,
                state.getModelName(),
                state.getDescription(),
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
                text.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n");
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
            text.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n");
            text.append("📝 Описание: ").append(submission.getDescription() != null ? submission.getDescription() : "не указано").append("\n");
            
            List<String> altModels = submission.getAlternativeModelsList();
            if (!altModels.isEmpty()) {
                text.append("🔄 Альтернативные модели:\n");
                for (String model : altModels) {
                    text.append("  • ").append(model).append("\n");
                }
            }
            
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
                case FORM_WAITING_MODEL:
                    state.setModelName(text.trim().isEmpty() ? null : text.trim());
                    break;
                case FORM_WAITING_DESC:
                    state.setDescription(text.trim().isEmpty() ? null : text.trim());
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
            state.setCurrentStep(ConversationStep.FORM_WAITING_MODEL);
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
            
            // Удаляем сообщение-запрос
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
            case "model":
                prompt = "🔪 Введите название модели ножа:";
                currentValue = state.getModelName() != null ? state.getModelName() : "";
                newStep = ConversationStep.FORM_WAITING_MODEL;
                break;
            case "desc":
                prompt = "📝 Введите описание сертификата:";
                currentValue = state.getDescription() != null ? state.getDescription() : "";
                newStep = ConversationStep.FORM_WAITING_DESC;
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
                    
                    message.setText(prompt + "\n\nТекущее значение:\n" + currentValue + "\n\n💡 Отправьте новое значение или пустое сообщение для очистки");
                } else {
                    message.setText(prompt + "\n\n💡 Отправьте значение или пустое сообщение для пропуска");
                }
                
                // ForceReply заставит пользователя ответить на это сообщение
                org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard forceReply = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard();
                forceReply.setSelective(true);
                forceReply.setInputFieldPlaceholder(currentValue.isEmpty() ? "Введите значение..." : currentValue);
                message.setReplyMarkup(forceReply);
                
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
            
            String modelName = state.getModelName() != null ? state.getModelName() : "unknown";
            String sanitizedModelName = modelName.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_-]", "_");
            String fileName = sanitizedModelName + "_" + state.getUsername() + "_" + System.currentTimeMillis() + ".jpg";
            String yandexPath;
            
            try (InputStream photoStream = new java.io.FileInputStream(photoFile)) {
                yandexPath = yandexDiskService.uploadToOffers(photoStream, fileName);
            }
            
            Submission submission = submissionService.createSubmission(
                userId,
                state.getUsername(),
                yandexPath,
                state.getModelName(),
                state.getDescription(),
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
        try {
            List<Submission> approved = submissionService.getApprovedSubmissions();
            
            if (approved.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("📭 Пока нет одобренных сертификатов.");
                
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
            
            StringBuilder text = new StringBuilder("📋 Список одобренных моделей:\n\n");
            for (Submission sub : approved) {
                text.append("🔪 ").append(sub.getModelName() != null ? sub.getModelName() : "Без названия").append("\n");
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
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка");
        }
    }
}