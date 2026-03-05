package com.knifecerts;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.model.Submission;

@Component
public class AdminBot extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(AdminBot.class.getName());

    @Value("${telegram.admin.bot.token}")
    private String botToken;

    @Value("${telegram.admin.bot.username}")
    private String botUsername;

    @Autowired
    private YandexDiskService yandexDiskService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private KnifeBot knifeBot;

    @Value("${webapp.base.url}")
    private String webAppBaseUrl;
    
    // Хранилище состояний модерации для каждого чата
    private final java.util.Map<Long, ModerationState> moderationStates = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Хранилище состояний поиска для каждого чата
    private final java.util.Map<Long, String> searchStates = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Хранилище ID сообщений для каждого чата
    private final java.util.Map<Long, ChatMessages> chatMessages = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Хранилище текущего открытого окна для каждого чата
    private final java.util.Map<Long, String> currentWindow = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Журнал ошибок (последние 50)
    private final java.util.Queue<ErrorLog> errorLogs = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_ERROR_LOGS = 50;
    
    // Внутренний класс для хранения ID сообщений чата
    private static class ChatMessages {
        private Integer mainMenuMessageId;
        private Integer lastWindowMessageId; // ID последнего сообщения окна
        private final java.util.Set<Integer> otherMessageIds = new java.util.HashSet<>();
        private final java.util.List<Integer> recentWindowMessages = new java.util.ArrayList<>(); // Последние сообщения окон
        
        public Integer getMainMenuMessageId() { return mainMenuMessageId; }
        public void setMainMenuMessageId(Integer id) { this.mainMenuMessageId = id; }
        public Integer getLastWindowMessageId() { return lastWindowMessageId; }
        public void setLastWindowMessageId(Integer id) { 
            this.lastWindowMessageId = id;
            if (id != null) {
                recentWindowMessages.add(id);
                System.out.println("DEBUG ChatMessages: Added message " + id + " to list, size now: " + recentWindowMessages.size());
                // Храним только последние 10
                if (recentWindowMessages.size() > 10) {
                    recentWindowMessages.remove(0);
                }
            }
        }
        public java.util.List<Integer> getRecentWindowMessages() { 
            System.out.println("DEBUG ChatMessages: getRecentWindowMessages called, size: " + recentWindowMessages.size());
            return new java.util.ArrayList<>(recentWindowMessages); 
        }
        public void clearRecentWindowMessages() { 
            System.out.println("DEBUG ChatMessages: clearRecentWindowMessages called, was size: " + recentWindowMessages.size());
            recentWindowMessages.clear(); 
        }
        public java.util.Set<Integer> getOtherMessageIds() { return otherMessageIds; }
        public void addOtherMessageId(Integer id) { 
            if (id != null && !id.equals(mainMenuMessageId)) {
                otherMessageIds.add(id); 
            }
        }
        public void clearOtherMessages() { otherMessageIds.clear(); }
    }
    
    // Внутренний класс для журнала ошибок
    private static class ErrorLog {
        private final LocalDateTime timestamp;
        private final String operation;
        private final String error;
        
        public ErrorLog(String operation, String error) {
            this.timestamp = LocalDateTime.now();
            this.operation = operation;
            this.error = error;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getOperation() { return operation; }
        public String getError() { return error; }
    }
    
    // Внутренний класс для хранения состояния модерации
    private static class ModerationState {
        private final Submission original;
        private String name;
        private String brand;
        private String indexCode;
        private List<String> alternativeModels;
        private Integer formMessageId;
        private Integer promptMessageId;
        private String editingField; // "name", "brand", "index", "alt"
        private boolean isApprovedView; // true если это просмотр одобренного сертификата
        
        public ModerationState(Submission original) {
            this.original = original;
            this.name = original.getName();
            this.brand = original.getBrand() != null ? original.getBrand().getName() : null;
            this.indexCode = original.getIndexCode();
            this.alternativeModels = original.getAlternativeModelsList();
            this.isApprovedView = false;
        }
        
        public ModerationState(Submission original, boolean isApprovedView) {
            this.original = original;
            this.name = original.getName();
            this.brand = original.getBrand() != null ? original.getBrand().getName() : null;
            this.indexCode = original.getIndexCode();
            this.alternativeModels = original.getAlternativeModelsList();
            this.isApprovedView = isApprovedView;
        }
        
        // Проверка были ли изменения
        public boolean hasChanges() {
            String originalName = original.getName();
            String originalBrand = original.getBrand() != null ? original.getBrand().getName() : null;
            String originalIndex = original.getIndexCode();
            List<String> originalAlts = original.getAlternativeModelsList();
            
            boolean nameChanged = !java.util.Objects.equals(originalName, name);
            boolean brandChanged = !java.util.Objects.equals(originalBrand, brand);
            boolean indexChanged = !java.util.Objects.equals(originalIndex, indexCode);
            boolean altsChanged = !java.util.Objects.equals(originalAlts, alternativeModels);
            
            return nameChanged || brandChanged || indexChanged || altsChanged;
        }
        
        public Submission getOriginal() { return original; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getIndexCode() { return indexCode; }
        public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
        public List<String> getAlternativeModels() { return alternativeModels; }
        public void setAlternativeModels(List<String> alternativeModels) { this.alternativeModels = alternativeModels; }
        public Integer getFormMessageId() { return formMessageId; }
        public void setFormMessageId(Integer formMessageId) { this.formMessageId = formMessageId; }
        public Integer getPromptMessageId() { return promptMessageId; }
        public void setPromptMessageId(Integer promptMessageId) { this.promptMessageId = promptMessageId; }
        public String getEditingField() { return editingField; }
        public void setEditingField(String editingField) { this.editingField = editingField; }
        public boolean isApprovedView() { return isApprovedView; }
        public void setApprovedView(boolean approvedView) { this.isApprovedView = approvedView; }
    }

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
                
                if (update.getMessage().hasText()) {
                    String messageText = update.getMessage().getText();
                    Long moderatorId = update.getMessage().getFrom().getId();

                    if (messageText.equals("/start")) {
                        // Удаляем команду /start
                        try {
                            DeleteMessage deleteMsg = new DeleteMessage();
                            deleteMsg.setChatId(chatId.toString());
                            deleteMsg.setMessageId(update.getMessage().getMessageId());
                            execute(deleteMsg);
                        } catch (Exception e) {
                            logger.warning("Failed to delete /start command: " + e.getMessage());
                        }
                        sendMainMenu(chatId);
                    } else if (messageText.equals("/upload")) {
                        sendMessage(chatId, "Отправьте фото для загрузки на Яндекс.Диск.");
                    } else if (messageText.equals("/pending")) {
                        handlePendingCommand(chatId);
                    } else if (messageText.startsWith("/view ")) {
                        handleViewCommand(chatId, messageText);
                    } else if (messageText.startsWith("/edit ")) {
                        handleEditCommandSimple(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/review ")) {
                        handleReviewCommand(chatId, messageText);
                    } else if (messageText.startsWith("/approve ")) {
                        handleApproveCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/reject ")) {
                        handleRejectCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setname ")) {
                        handleSetNameCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setbrand ")) {
                        handleSetBrandCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setindex ")) {
                        handleSetIndexCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setalt ")) {
                        handleSetAltCommand(chatId, moderatorId, messageText);
                    } else if (messageText.equals("/clearall")) {
                        handleClearAllCommand(chatId, moderatorId);
                    } else {
                        // Проверяем, есть ли активное состояние поиска
                        if (searchStates.containsKey(chatId)) {
                            String searchType = searchStates.get(chatId);
                            searchStates.remove(chatId);
                            
                            if ("approved".equals(searchType)) {
                                handleApprovedCommand(chatId, 0, messageText);
                            }
                        } else {
                            // Проверяем, есть ли активное состояние редактирования
                            ModerationState state = moderationStates.get(chatId);
                            if (state != null && state.getEditingField() != null) {
                                handleModFieldInput(chatId, messageText);
                            } else {
                                sendMessage(chatId, "Неизвестная команда. Используйте /start для списка команд.");
                            }
                        }
                    }
                } else if (update.getMessage().hasPhoto()) {
                    handlePhoto(update);
                }
            }
        } catch (Exception e) {
            logger.severe("Неожиданная ошибка в AdminBot: " + e.getClass().getName() + " - " + e.getMessage());
            
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
                    sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
                } else if (update.hasCallbackQuery()) {
                    Long chatId = update.getCallbackQuery().getMessage().getChatId();
                    sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
                }
            } catch (Exception notificationError) {
                logger.severe("Не удалось отправить сообщение об ошибке: " + notificationError.getMessage());
            }
        }
    }

    private void handleCallbackQuery(Update update) {
        var callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long moderatorId = callbackQuery.getFrom().getId();
        
        try {
            // При любой кнопке главного меню - удаляем последние сообщения
            if (data.startsWith("menu_")) {
                deleteRecentMessages(chatId, callbackQuery.getMessage().getMessageId());
            }
            
            if (data.equals("menu_pending")) {
                handlePendingCommand(chatId);
            } else if (data.equals("menu_approved")) {
                handleApprovedCommand(chatId, 0);
            } else if (data.equals("menu_upload")) {
                sendMessage(chatId, "📤 Отправьте фото для загрузки на Яндекс.Диск");
            } else if (data.equals("menu_clearall")) {
                sendClearConfirmation(chatId);
            } else if (data.equals("menu_disk_analysis")) {
                handleDiskAnalysis(chatId);
            } else if (data.equals("menu_error_log")) {
                handleErrorLog(chatId);
            } else if (data.equals("menu_settings")) {
                handleSettingsCommand(chatId);
            } else if (data.equals("disk_clean_broken")) {
                handleCleanBrokenLinks(chatId);
            } else if (data.equals("disk_clean_orphaned")) {
                handleCleanOrphanedPhotos(chatId);
            } else if (data.equals("error_log_clear")) {
                handleErrorLogClear(chatId);
            } else if (data.equals("back_to_menu")) {
                // Просто удаляем сообщение с кнопкой
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(callbackQuery.getMessage().getMessageId());
                    execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete message: " + e.getMessage());
                }
                // Очищаем состояния
                moderationStates.remove(chatId);
                searchStates.remove(chatId);
                currentWindow.remove(chatId);
            } else if (data.equals("confirm_clear_yes")) {
                handleClearAllCommand(chatId, moderatorId);
            } else if (data.equals("confirm_clear_no")) {
                sendMessage(chatId, "❌ Очистка отменена");
                sendMainMenu(chatId);
            } else if (data.startsWith("view_approved_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                showApprovedSubmissionDetails(chatId, submissionId);
            } else if (data.startsWith("view_")) {
                Long submissionId = Long.parseLong(data.substring(5));
                showSubmissionDetails(chatId, submissionId);
            } else if (data.startsWith("edit_")) {
                Long submissionId = Long.parseLong(data.substring(5));
                showEditMenu(chatId, submissionId);
            } else if (data.startsWith("approve_")) {
                Long submissionId = Long.parseLong(data.substring(8));
                handleApproveCallback(chatId, moderatorId, submissionId);
            } else if (data.startsWith("reject_")) {
                Long submissionId = Long.parseLong(data.substring(7));
                handleRejectCallback(chatId, moderatorId, submissionId);
            } else if (data.startsWith("mod_edit_name_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleModEditField(chatId, submissionId, "name");
            } else if (data.startsWith("mod_edit_brand_")) {
                Long submissionId = Long.parseLong(data.substring(15));
                handleModEditField(chatId, submissionId, "brand");
            } else if (data.startsWith("mod_edit_index_")) {
                Long submissionId = Long.parseLong(data.substring(15));
                handleModEditField(chatId, submissionId, "index");
            } else if (data.startsWith("mod_edit_alt_")) {
                Long submissionId = Long.parseLong(data.substring(13));
                handleModEditField(chatId, submissionId, "alt");
            } else if (data.startsWith("mod_approve_")) {
                Long submissionId = Long.parseLong(data.substring(12));
                handleModApprove(chatId, moderatorId, submissionId);
            } else if (data.startsWith("mod_reject_")) {
                Long submissionId = Long.parseLong(data.substring(11));
                handleModRejectRequest(chatId, submissionId);
            } else if (data.startsWith("confirm_reject_")) {
                Long submissionId = Long.parseLong(data.substring(15));
                handleModReject(chatId, moderatorId, submissionId);
            } else if (data.startsWith("cancel_reject_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleCancelRejectConfirmation(chatId, submissionId);
            } else if (data.startsWith("mod_cancel_")) {
                Long submissionId = Long.parseLong(data.substring(11));
                handleModCancel(chatId, submissionId);
            } else if (data.startsWith("pending_page_")) {
                int page = Integer.parseInt(data.substring(13));
                handlePendingCommand(chatId, page);
            } else if (data.equals("pending_current_page")) {
                // Ignore clicks on current page indicator
            } else if (data.startsWith("approved_page_")) {
                // Парсим: approved_page_N или approved_page_N_search_query
                String[] parts = data.split("_search_", 2);
                int page = Integer.parseInt(parts[0].substring(14));
                String searchQuery = parts.length > 1 ? parts[1] : null;
                handleApprovedCommand(chatId, page, searchQuery);
            } else if (data.equals("approved_current_page")) {
                // Ignore clicks on current page indicator
            } else if (data.equals("approved_search")) {
                handleApprovedSearchRequest(chatId);
            } else if (data.equals("approved_reset_search")) {
                handleApprovedCommand(chatId, 0, null);
            } else if (data.startsWith("approved_save_confirm_")) {
                Long submissionId = Long.parseLong(data.substring(22));
                handleApprovedSaveConfirm(chatId, submissionId);
            } else if (data.startsWith("approved_save_no_")) {
                Long submissionId = Long.parseLong(data.substring(17));
                handleApprovedSaveNo(chatId, submissionId);
            } else if (data.startsWith("approved_save_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleApprovedSave(chatId, submissionId);
            } else if (data.startsWith("approved_delete_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                handleApprovedDelete(chatId, submissionId);
            } else if (data.startsWith("approved_cancel_confirm_")) {
                Long submissionId = Long.parseLong(data.substring(24));
                handleApprovedCancelConfirm(chatId, submissionId);
            } else if (data.startsWith("approved_cancel_no_")) {
                Long submissionId = Long.parseLong(data.substring(19));
                handleApprovedCancelNo(chatId, submissionId);
            } else if (data.startsWith("approved_cancel_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                handleApprovedCancel(chatId, submissionId);
            } else if (data.equals("back_to_menu")) {
                // Просто удаляем сообщение с кнопкой
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(callbackQuery.getMessage().getMessageId());
                    execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete message: " + e.getMessage());
                }
                // Очищаем состояния
                moderationStates.remove(chatId);
                searchStates.remove(chatId);
                currentWindow.remove(chatId);
            }
            
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            execute(answer);
            
        } catch (Exception e) {
            logger.severe("Ошибка при обработке callback: " + e.getMessage());
            try {
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                answer.setText("❌ Ошибка");
                answer.setShowAlert(true);
                execute(answer);
            } catch (TelegramApiException ex) {
                logger.severe("Не удалось ответить на callback: " + ex.getMessage());
            }
        }
    }

    private void handleApproveCallback(Long chatId, Long moderatorId, Long submissionId) {
        try {
            Submission submission = submissionService.approveSubmission(submissionId, moderatorId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            if (knifeBot != null) {
                String userMessage = "✅ Ваша заявка #" + submission.getId() + " одобрена!\n\n" +
                        "Сертификат добавлен в систему.";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
            sendMainMenu(chatId);
            
        } catch (SubmissionException e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки");
        }
    }

    private void handleRejectCallback(Long chatId, Long moderatorId, Long submissionId) {
        try {
            Submission submission = submissionService.rejectSubmission(submissionId, moderatorId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            if (knifeBot != null) {
                String userMessage = "❌ Ваша заявка #" + submission.getId() + " отклонена.\n\n" +
                        "Вы можете подать новую заявку командой /submit";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
            sendMainMenu(chatId);
            
        } catch (SubmissionException e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки");
        }
    }

    private void handlePhoto(Update update) {
        Long chatId = update.getMessage().getChatId();
        
        try {
            PhotoSize photo = update.getMessage().getPhoto()
                    .stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            
            if (photo == null) {
                sendMessage(chatId, "❌ Не удалось получить фото");
                return;
            }
            
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "photo_" + timestamp + ".jpg";
            
            try (InputStream photoStream = new URL(fileUrl).openStream()) {
                String path = yandexDiskService.uploadPhoto(photoStream, fileName);
                sendMessage(chatId, "✅ Фото успешно загружено!\nПуть: " + path);
            }
            
        } catch (Exception e) {
            logger.severe("Error handling photo: " + e.getClass().getName() + " - " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при загрузке фото. Попробуйте еще раз.");
        }
    }

    private void handlePendingCommand(Long chatId) {
        handlePendingCommand(chatId, 0);
    }
    
    private void handlePendingCommand(Long chatId, int page) {
        try {
            List<Submission> pendingSubmissions = submissionService.getPendingSubmissions();
            
            if (pendingSubmissions.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("📭 Очередь пуста. Нет ожидающих заявок.");
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                executeAndTrack(message);
                return;
            }
            
            // Пагинация: 10 строк по 3 кнопки = 30 элементов на страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, pendingSubmissions.size());
            
            List<Submission> pageItems = pendingSubmissions.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            text.append("📋 Ожидающие заявки\n\n");
            text.append("Всего: ").append(pendingSubmissions.size()).append(" заявок\n");
            text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Кнопки с заявками (3 колонки, до 10 строк)
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                Submission sub = pageItems.get(i);
                String buttonText = sub.getDisplayName();
                
                // Обрезаем длинные названия
                if (buttonText.length() > 15) {
                    buttonText = buttonText.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("view_" + sub.getId())
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
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                if (page > 0) {
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("pending_page_" + (page - 1))
                        .build());
                }
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("pending_current_page")
                    .build());
                
                if (page < totalPages - 1) {
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("Вперёд ➡️")
                        .callbackData("pending_page_" + (page + 1))
                        .build());
                }
                
                keyboard.add(paginationRow);
            }
            
            // Кнопка возврата в меню
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            executeAndTrack(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка заявок: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка заявок");
        }
    }
    
    private void handleApprovedCommand(Long chatId, int page) {
        handleApprovedCommand(chatId, page, null);
    }
    
    private void handleApprovedCommand(Long chatId, int page, String searchQuery) {
        try {
            List<Submission> approvedSubmissions;
            
            // Используем поиск или получаем все одобренные
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                approvedSubmissions = submissionService.searchApprovedSubmissions(searchQuery);
            } else {
                approvedSubmissions = submissionService.getApprovedSubmissions();
            }
            
            if (approvedSubmissions.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(searchQuery != null 
                    ? "🔍 По запросу \"" + searchQuery + "\" ничего не найдено."
                    : "📭 Нет одобренных сертификатов.");
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                if (searchQuery != null) {
                    List<InlineKeyboardButton> searchRow = new ArrayList<>();
                    searchRow.add(InlineKeyboardButton.builder()
                        .text("🔄 Сбросить поиск")
                        .callbackData("approved_reset_search")
                        .build());
                    keyboard.add(searchRow);
                }
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                executeAndTrack(message);
                return;
            }
            
            // Пагинация: 10 строк по 3 кнопки = 30 элементов на страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) approvedSubmissions.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, approvedSubmissions.size());
            
            List<Submission> pageItems = approvedSubmissions.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            if (searchQuery != null) {
                text.append("🔍 Результаты поиска: \"").append(searchQuery).append("\"\n\n");
            } else {
                text.append("✅ Одобренные сертификаты\n\n");
            }
            text.append("Всего: ").append(approvedSubmissions.size()).append(" сертификатов\n");
            text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Кнопки с сертификатами (3 колонки, до 10 строк)
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                Submission sub = pageItems.get(i);
                String buttonText = sub.getDisplayName();
                
                // Обрезаем длинные названия
                if (buttonText.length() > 15) {
                    buttonText = buttonText.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("view_approved_" + sub.getId())
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
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                if (page > 0) {
                    String prevCallback = searchQuery != null 
                        ? "approved_page_" + (page - 1) + "_search_" + searchQuery
                        : "approved_page_" + (page - 1);
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData(prevCallback)
                        .build());
                }
                
                paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData("approved_current_page")
                    .build());
                
                if (page < totalPages - 1) {
                    String nextCallback = searchQuery != null 
                        ? "approved_page_" + (page + 1) + "_search_" + searchQuery
                        : "approved_page_" + (page + 1);
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("Вперёд ➡️")
                        .callbackData(nextCallback)
                        .build());
                }
                
                keyboard.add(paginationRow);
            }
            
            // Кнопка поиска
            List<InlineKeyboardButton> searchRow = new ArrayList<>();
            if (searchQuery != null) {
                searchRow.add(InlineKeyboardButton.builder()
                    .text("🔄 Сбросить поиск")
                    .callbackData("approved_reset_search")
                    .build());
            } else {
                searchRow.add(InlineKeyboardButton.builder()
                    .text("🔍 Поиск")
                    .callbackData("approved_search")
                    .build());
            }
            keyboard.add(searchRow);
            
            // Кнопка возврата в меню
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            executeAndTrack(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка одобренных сертификатов: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка сертификатов");
        }
    }

    private void handleViewCommand(Long chatId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /view <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка #").append(submission.getId()).append("\n\n");
            caption.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
            caption.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            caption.append("📅 Дата: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            caption.append("📊 Статус: ").append(submission.getStatus()).append("\n\n");
            caption.append("🔪 Название: ").append(submission.getName() != null ? submission.getName() : "не указано").append("\n");
            caption.append("🏷️ Бренд: ").append(submission.getBrand() != null ? submission.getBrand() : "не указан").append("\n");
            caption.append("🔢 Индекс: ").append(submission.getIndexCode() != null ? submission.getIndexCode() : "не указан").append("\n\n");
            
            List<String> altModels = submission.getAlternativeModelsList();
            if (!altModels.isEmpty()) {
                caption.append("🔄 Альтернативные модели:\n");
                for (String model : altModels) {
                    caption.append("  • ").append(model).append("\n");
                }
            }
            
            String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            sendPhoto.setCaption(caption.toString());
            
            if (submission.getStatus() == com.knifecerts.model.SubmissionStatus.PENDING) {
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("✅ Одобрить")
                    .callbackData("approve_" + submissionId)
                    .build());
                row.add(InlineKeyboardButton.builder()
                    .text("❌ Отклонить")
                    .callbackData("reject_" + submissionId)
                    .build());
                
                keyboard.add(row);
                markup.setKeyboard(keyboard);
                sendPhoto.setReplyMarkup(markup);
            }
            
            Message sent = execute(sendPhoto);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /view <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }

    private void handleEditCommandSimple(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /edit <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            
            StringBuilder text = new StringBuilder();
            text.append("✏️ Редактирование заявки #").append(submissionId).append("\n\n");
            text.append("Используйте команды для изменения:\n\n");
            text.append("/setname ").append(submissionId).append(" <новое название>\n");
            text.append("/setbrand ").append(submissionId).append(" <новый бренд>\n");
            text.append("/setindex ").append(submissionId).append(" <новый индекс>\n");
            text.append("/setalt ").append(submissionId).append(" <модель1, модель2, ...>\n\n");
            text.append("Текущие данные:\n");
            text.append("🔪 Название: ").append(submission.getName() != null ? submission.getName() : "не указано").append("\n");
            text.append("🏷️ Бренд: ").append(submission.getBrand() != null ? submission.getBrand() : "не указан").append("\n");
            text.append("🔢 Индекс: ").append(submission.getIndexCode() != null ? submission.getIndexCode() : "не указан").append("\n");
            
            List<String> altModels = submission.getAlternativeModelsList();
            if (!altModels.isEmpty()) {
                text.append("🔄 Альтернативные: ").append(String.join(", ", altModels)).append("\n");
            }
            
            message.setText(text.toString());
            execute(message);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /edit <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при открытии редактора: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при открытии редактора");
        }
    }

    private void handleEditCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /edit <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            // Web App функциональность временно отключена (ModerationSessionManager удален)
            sendMessage(chatId, "⚠️ Web App редактор временно недоступен. Используйте кнопки для редактирования.");
            
            /* Закомментировано - Web App не реализован
            ModerationSession session = sessionManager.createSession(submissionId, moderatorId);
            
            String webAppUrl = webAppBaseUrl + "/moderation.html?token=" + session.getToken();
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Нажмите кнопку ниже для редактирования заявки #" + submissionId);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            */
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /edit <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при открытии редактора: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при открытии редактора");
        }
    }

    private void handleReviewCommand(Long chatId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /review <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена.");
                return;
            }
            
            Submission submission = submissionOpt.get();
            sendSubmissionDetails(chatId, submission);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /review <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }

    private void sendSubmissionDetails(Long chatId, Submission submission) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder message = new StringBuilder();
            message.append("📄 Детали заявки #").append(submission.getId()).append("\n\n");
            message.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
            message.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            
            if (submission.getName() != null) {
                message.append("🔪 Название: ").append(submission.getName()).append("\n");
            } else {
                message.append("🔪 Название: не указано\n");
            }
            
            if (submission.getBrand() != null) {
                message.append("🏷️ Бренд: ").append(submission.getBrand()).append("\n");
            } else {
                message.append("🏷️ Бренд: не указан\n");
            }
            
            if (submission.getIndexCode() != null) {
                message.append("� Индекс: ").append(submission.getIndexCode()).append("\n");
            } else {
                message.append("🔢 Индекс: не указан\n");
            }
            
            message.append("📅 Дата подачи: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            message.append("� Статус: ").append(submission.getStatus()).append("\n");
            
            if (submission.getModeratedAt() != null) {
                message.append("✅ Проверена: ").append(submission.getModeratedAt().format(formatter)).append("\n");
                message.append("👮 Модератор ID: ").append(submission.getModeratedBy()).append("\n");
            }
            
            sendMessage(chatId, message.toString());
            
            try {
                String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
                sendMessage(chatId, "🖼️ Фото: " + photoUrl);
            } catch (Exception e) {
                logger.warning("Не удалось получить URL фото: " + e.getMessage());
                sendMessage(chatId, "⚠️ Не удалось загрузить фото. Путь: " + submission.getPhotoPath());
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при отправке деталей заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении деталей заявки.");
        }
    }

    private void handleApproveCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /approve <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Submission submission = submissionService.approveSubmission(submissionId, moderatorId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            if (knifeBot != null) {
                String userMessage = "✅ Ваша заявка #" + submission.getId() + " одобрена!\n\n" +
                        "Сертификат добавлен в систему.";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /approve <ID>");
        } catch (SubmissionException e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки");
        }
    }

    private void handleRejectCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /reject <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Submission submission = submissionService.rejectSubmission(submissionId, moderatorId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            if (knifeBot != null) {
                String userMessage = "❌ Ваша заявка #" + submission.getId() + " отклонена.\n\n" +
                        "Вы можете подать новую заявку командой /submit";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /reject <ID>");
        } catch (SubmissionException e) {
            sendMessage(chatId, "❌ " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки");
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            Message sent = execute(message);
            logger.info("DEBUG: Sent message with ID: " + sent.getMessageId());
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
            logger.info("DEBUG: Saved lastWindowMessageId: " + sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending message: " + e.getMessage());
        }
    }
    
    private Message executeAndTrack(SendMessage message) throws TelegramApiException {
        Message sent = execute(message);
        Long chatId = Long.parseLong(message.getChatId());
        ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
        messages.setLastWindowMessageId(sent.getMessageId());
        return sent;
    }
    
    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔧 Панель администратора\n\nВыберите действие:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Первая строка
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text("📋 Ожидающие заявки")
            .callbackData("menu_pending")
            .build());
        keyboard.add(row1);
        
        // Вторая строка
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text("✅ Одобренные сертификаты")
            .callbackData("menu_approved")
            .build());
        keyboard.add(row2);
        
        // Третья строка
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
            .text("📤 Загрузить фото")
            .callbackData("menu_upload")
            .build());
        keyboard.add(row3);
        
        // Четвертая строка
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(InlineKeyboardButton.builder()
            .text("🔍 Анализ диска")
            .callbackData("menu_disk_analysis")
            .build());
        keyboard.add(row4);
        
        // Пятая строка
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(InlineKeyboardButton.builder()
            .text("📋 Журнал ошибок")
            .callbackData("menu_error_log")
            .build());
        keyboard.add(row5);
        
        // Шестая строка
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(InlineKeyboardButton.builder()
            .text("⚙️ Настройки")
            .callbackData("menu_settings")
            .build());
        keyboard.add(row6);
        
        // Седьмая строка
        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(InlineKeyboardButton.builder()
            .text("🗑️ Очистить все данные")
            .callbackData("menu_clearall")
            .build());
        keyboard.add(row7);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            Message sent = execute(message);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setMainMenuMessageId(sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending menu: " + e.getMessage());
        }
    }
    
    private void sendClearConfirmation(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚠️ Вы уверены, что хотите очистить все фото из папок на Яндекс.Диске?\n\nЭто действие нельзя отменить!");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
            .text("✅ Да, очистить")
            .callbackData("confirm_clear_yes")
            .build());
        row.add(InlineKeyboardButton.builder()
            .text("❌ Отмена")
            .callbackData("confirm_clear_no")
            .build());
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending confirmation: " + e.getMessage());
        }
    }
    
    private void showSubmissionDetails(Long chatId, Long submissionId) {
        try {
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            // Создаем временную копию для редактирования
            moderationStates.put(chatId, new ModerationState(submission));
            
            sendSubmissionForm(chatId, submissionId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }
    
    private void sendSubmissionForm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStates.get(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние модерации не найдено");
                return;
            }
            
            Submission submission = state.getOriginal();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка на добавление сертификата\n\n");
            caption.append("от @").append(submission.getUsername());
            caption.append(", ").append(submission.getCreatedAt().format(formatter)).append("\n");
            
            InlineKeyboardMarkup markup = buildModFormKeyboard(state, submissionId, submission);
            
            // Если форма уже существует - редактируем кнопки
            if (state.getFormMessageId() != null) {
                try {
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup editMarkup = 
                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup();
                    editMarkup.setChatId(chatId.toString());
                    editMarkup.setMessageId(state.getFormMessageId());
                    editMarkup.setReplyMarkup(markup);
                    execute(editMarkup);
                    return;
                } catch (Exception e) {
                    logger.warning("Не удалось отредактировать форму, отправляем новую: " + e.getMessage());
                }
            }
            
            // Отправляем новую форму
            String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            sendPhoto.setCaption(caption.toString());
            sendPhoto.setReplyMarkup(markup);
            
            Message sentMessage = execute(sendPhoto);
            state.setFormMessageId(sentMessage.getMessageId());
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sentMessage.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отправке формы");
        }
    }
    
    private InlineKeyboardMarkup buildModFormKeyboard(ModerationState state, Long submissionId, Submission submission) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка "Название"
        String nameText = state.getName() != null ? "🔪 " + state.getName() : "🔪 Название (не указано)";
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("mod_edit_name_" + submissionId)
            .build());
        keyboard.add(row1);
        
        // Кнопка "Бренд"
        String brandText = state.getBrand() != null 
            ? "🏷️ " + (state.getBrand().length() > 20 ? state.getBrand().substring(0, 20) + "..." : state.getBrand())
            : "🏷️ Бренд (не указан)";
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("mod_edit_brand_" + submissionId)
            .build());
        keyboard.add(row2);
        
        // Кнопка "Индекс"
        String indexText = state.getIndexCode() != null ? "🔢 " + state.getIndexCode() : "🔢 Индекс (не указан)";
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("mod_edit_index_" + submissionId)
            .build());
        keyboard.add(row3);
        
        // Кнопка "Альтернативные модели"
        String altText = (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty())
            ? "🔄 " + String.join(", ", state.getAlternativeModels())
            : "🔄 Альтернативные модели (не указано)";
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(InlineKeyboardButton.builder()
            .text(altText)
            .callbackData("mod_edit_alt_" + submissionId)
            .build());
        keyboard.add(row4);
        
        // Кнопки действий
        if (submission.getStatus() == com.knifecerts.model.SubmissionStatus.PENDING) {
            List<InlineKeyboardButton> row5 = new ArrayList<>();
            row5.add(InlineKeyboardButton.builder()
                .text("✅ Одобрить")
                .callbackData("mod_approve_" + submissionId)
                .build());
            row5.add(InlineKeyboardButton.builder()
                .text("❌ Отклонить")
                .callbackData("mod_reject_" + submissionId)
                .build());
            keyboard.add(row5);
        }
        
        // Кнопка отмены
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(InlineKeyboardButton.builder()
            .text("🚫 Отмена")
            .callbackData("mod_cancel_" + submissionId)
            .build());
        keyboard.add(row5);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private void showApprovedSubmissionDetails(Long chatId, Long submissionId) {
        try {
            // Проверяем, есть ли уже открытая форма
            ModerationState existingState = moderationStates.get(chatId);
            if (existingState != null && existingState.getFormMessageId() != null) {
                // Удаляем только предыдущую форму заявки, не трогая список
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(existingState.getFormMessageId());
                    execute(deleteMsg);
                    logger.info("Deleted previous form message: " + existingState.getFormMessageId());
                } catch (Exception e) {
                    logger.info("Failed to delete previous form: " + e.getMessage());
                }
            }
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат #" + submissionId + " не найден");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            // Создаем временную копию для редактирования с флагом isApprovedView
            moderationStates.put(chatId, new ModerationState(submission, true));
            
            sendApprovedSubmissionForm(chatId, submissionId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре сертификата");
        }
    }
    
    private void sendApprovedSubmissionForm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStates.get(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Submission submission = state.getOriginal();
            
            // Формируем текст с информацией о сертификате
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            StringBuilder caption = new StringBuilder();
            caption.append("✅ Одобренный сертификат\n");
            caption.append("от @").append(submission.getUsername()).append(", ");
            caption.append(submission.getCreatedAt().format(formatter)).append("\n\n");
            
            caption.append("ID: #").append(submission.getId()).append("\n");
            caption.append("Статус: ").append(submission.getStatus()).append("\n");
            
            if (submission.getModeratedAt() != null) {
                caption.append("Одобрено: ").append(submission.getModeratedAt().format(formatter));
            }
            
            // Отправляем фото с подписью
            try {
                String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
                SendPhoto photoMessage = new SendPhoto();
                photoMessage.setChatId(chatId.toString());
                photoMessage.setPhoto(new InputFile(photoUrl));
                photoMessage.setCaption(caption.toString());
                photoMessage.setReplyMarkup(buildApprovedFormKeyboard(submissionId, state));
                
                Message sentMessage = execute(photoMessage);
                state.setFormMessageId(sentMessage.getMessageId());
                ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                messages.setLastWindowMessageId(sentMessage.getMessageId());
                
            } catch (Exception e) {
                logger.warning("Не удалось загрузить фото: " + e.getMessage());
                SendMessage textMessage = new SendMessage();
                textMessage.setChatId(chatId.toString());
                textMessage.setText(caption.toString() + "\n\n⚠️ Не удалось загрузить фото");
                textMessage.setReplyMarkup(buildApprovedFormKeyboard(submissionId, state));
                execute(textMessage);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении сертификата");
        }
    }
    
    private InlineKeyboardMarkup buildApprovedFormKeyboard(Long submissionId, ModerationState state) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        Submission submission = state.getOriginal();
        
        // Кнопка названия
        String nameText = "🔪 Название: " + (state.getName() != null ? state.getName() : "не указано");
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("mod_edit_name_" + submissionId)
            .build());
        keyboard.add(row1);
        
        // Кнопка бренда
        String brandText = "🏷️ Бренд: " + (state.getBrand() != null ? state.getBrand() : "не указан");
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("mod_edit_brand_" + submissionId)
            .build());
        keyboard.add(row2);
        
        // Кнопка индекса
        String indexText = "🔢 Индекс: " + (state.getIndexCode() != null ? state.getIndexCode() : "не указан");
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("mod_edit_index_" + submissionId)
            .build());
        keyboard.add(row3);
        
        // Кнопка альтернативных моделей
        List<String> altModels = state.getAlternativeModels();
        String altText = "🔄 Альтернативные: " + (altModels != null && !altModels.isEmpty() ? altModels.size() + " шт." : "нет");
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(InlineKeyboardButton.builder()
            .text(altText)
            .callbackData("mod_edit_alt_" + submissionId)
            .build());
        keyboard.add(row4);
        
        // Кнопки действий
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(InlineKeyboardButton.builder()
            .text("💾 Сохранить изменения")
            .callbackData("approved_save_" + submissionId)
            .build());
        keyboard.add(row5);
        
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(InlineKeyboardButton.builder()
            .text("🗑️ Удалить сертификат")
            .callbackData("approved_delete_" + submissionId)
            .build());
        keyboard.add(row6);
        
        // Кнопка отмены
        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(InlineKeyboardButton.builder()
            .text("❌ Закрыть")
            .callbackData("approved_cancel_" + submissionId)
            .build());
        keyboard.add(row7);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private void showEditMenu(Long chatId, Long submissionId) {
        try {
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            StringBuilder text = new StringBuilder();
            text.append("✏️ Редактирование заявки #").append(submissionId).append("\n\n");
            text.append("Используйте команды для изменения:\n\n");
            text.append("/setname ").append(submissionId).append(" <новое название>\n");
            text.append("/setbrand ").append(submissionId).append(" <новый бренд>\n");
            text.append("/setindex ").append(submissionId).append(" <новый индекс>\n");
            text.append("/setalt ").append(submissionId).append(" <модель1, модель2, ...>\n\n");
            text.append("Текущие данные:\n");
            text.append("🔪 Название: ").append(submission.getName() != null ? submission.getName() : "не указано").append("\n");
            text.append("🏷️ Бренд: ").append(submission.getBrand() != null ? submission.getBrand() : "не указан").append("\n");
            text.append("🔢 Индекс: ").append(submission.getIndexCode() != null ? submission.getIndexCode() : "не указан").append("\n");
            
            List<String> altModels = submission.getAlternativeModelsList();
            if (!altModels.isEmpty()) {
                text.append("🔄 Альтернативные: ").append(String.join(", ", altModels)).append("\n");
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("🔙 Назад к заявке")
                .callbackData("view_" + submissionId)
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при открытии меню редактирования: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка");
        }
    }
    
    private void handleSetNameCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setname <ID> <новое название>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String newName = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            submissionService.updateSubmission(
                submissionId, 
                newName, 
                submission.getBrand() != null ? submission.getBrand().getName() : null,
                submission.getIndexCode(),
                submission.getAlternativeModelsList()
            );
            
            sendMessage(chatId, "✅ Название обновлено: " + newName);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении модели: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении");
        }
    }
    
    private void handleSetBrandCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setbrand <ID> <новый бренд>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String newBrand = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            submissionService.updateSubmission(
                submissionId,
                submission.getName(),
                newBrand,
                submission.getIndexCode(),
                submission.getAlternativeModelsList()
            );
            
            sendMessage(chatId, "✅ Бренд обновлен");
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении бренда: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении");
        }
    }
    
    private void handleSetIndexCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setindex <ID> <новый индекс>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String newIndex = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            submissionService.updateSubmission(
                submissionId,
                submission.getName(),
                submission.getBrand() != null ? submission.getBrand().getName() : null,
                newIndex,
                submission.getAlternativeModelsList()
            );
            
            sendMessage(chatId, "✅ Индекс обновлен");
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении индекса: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении");
        }
    }
    
    private void handleSetAltCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setalt <ID> <модель1, модель2, ...>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String altModelsStr = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            List<String> altModels = new ArrayList<>();
            if (!altModelsStr.isEmpty() && !altModelsStr.equalsIgnoreCase("нет")) {
                String[] models = altModelsStr.split(",");
                for (String model : models) {
                    String trimmed = model.trim();
                    if (!trimmed.isEmpty()) {
                        altModels.add(trimmed);
                    }
                }
            }
            
            Submission submission = submissionOpt.get();
            submission.setAlternativeModelsList(altModels);
            submissionService.updateSubmission(
                submissionId,
                submission.getName(),
                submission.getBrand() != null ? submission.getBrand().getName() : null,
                submission.getIndexCode(),
                altModels
            );
            
            if (altModels.isEmpty()) {
                sendMessage(chatId, "✅ Альтернативные модели очищены");
            } else {
                sendMessage(chatId, "✅ Альтернативные модели обновлены: " + String.join(", ", altModels));
            }
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении альтернативных моделей: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении");
        }
    }
    
    private void handleClearAllCommand(Long chatId, Long moderatorId) {
        try {
            sendMessage(chatId, "⚠️ Очистка всех данных...");
            
            int totalDeleted = 0;
            int dbRecordsDeleted = 0;
            
            // Очистка базы данных
            try {
                List<Submission> allSubmissions = submissionService.getAllSubmissions();
                dbRecordsDeleted = allSubmissions.size();
                
                if (dbRecordsDeleted > 0) {
                    submissionService.deleteAllSubmissions();
                    sendMessage(chatId, "✅ База данных очищена. Удалено записей: " + dbRecordsDeleted);
                } else {
                    sendMessage(chatId, "ℹ️ База данных пуста");
                }
            } catch (Exception e) {
                logger.warning("Ошибка очистки базы данных: " + e.getMessage());
                sendMessage(chatId, "⚠️ Ошибка очистки базы данных");
            }
            
            // Очистка папок на Яндекс.Диске
            try {
                int deleted = yandexDiskService.clearFolder("app:/certificates/offers");
                if (deleted > 0) {
                    sendMessage(chatId, "✅ Папка offers очищена. Удалено файлов: " + deleted);
                    totalDeleted += deleted;
                } else {
                    sendMessage(chatId, "ℹ️ Папка offers пуста");
                }
            } catch (Exception e) {
                logger.warning("Ошибка очистки offers: " + e.getMessage());
                sendMessage(chatId, "⚠️ Папка offers не найдена");
            }
            
            try {
                int deleted = yandexDiskService.clearFolder("app:/certificates/certificates");
                if (deleted > 0) {
                    sendMessage(chatId, "✅ Папка certificates очищена. Удалено файлов: " + deleted);
                    totalDeleted += deleted;
                } else {
                    sendMessage(chatId, "ℹ️ Папка certificates пуста");
                }
            } catch (Exception e) {
                logger.warning("Ошибка очистки certificates: " + e.getMessage());
                sendMessage(chatId, "⚠️ Папка certificates не найдена");
            }
            
            if (dbRecordsDeleted > 0 || totalDeleted > 0) {
                sendMessage(chatId, "✅ Очистка завершена! Удалено записей: " + dbRecordsDeleted + ", файлов: " + totalDeleted);
            } else {
                sendMessage(chatId, "ℹ️ Все данные уже пусты");
            }
            
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при очистке данных: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при очистке данных");
        }
    }
    
    private void handleModEditField(Long chatId, Long submissionId, String field) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        state.setEditingField(field);
        
        String prompt = "";
        String currentValue = "";
        
        switch (field) {
            case "name":
                prompt = "🔪 Введите название ножа:";
                currentValue = state.getName() != null ? state.getName() : "";
                break;
            case "brand":
                prompt = "🏷️ Введите бренд:";
                currentValue = state.getBrand() != null ? state.getBrand() : "";
                break;
            case "index":
                prompt = "🔢 Введите индекс:";
                currentValue = state.getIndexCode() != null ? state.getIndexCode() : "";
                break;
            case "alt":
                prompt = "🔄 Введите альтернативные модели через запятую:";
                currentValue = (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty())
                    ? String.join(", ", state.getAlternativeModels())
                    : "";
                break;
        }
        
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
            state.setPromptMessageId(sentMessage.getMessageId());
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sentMessage.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при запросе поля: " + e.getMessage());
        }
    }
    
    private void handleModFieldInput(Long chatId, String text) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null || state.getEditingField() == null) {
            return;
        }
        
        try {
            // Обновляем значение поля
            switch (state.getEditingField()) {
                case "name":
                    state.setName(text.trim().isEmpty() ? null : text.trim());
                    break;
                case "brand":
                    state.setBrand(text.trim().isEmpty() ? null : text.trim());
                    break;
                case "index":
                    state.setIndexCode(text.trim().isEmpty() ? null : text.trim());
                    break;
                case "alt":
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
            }
            
            state.setEditingField(null);
            
            // Удаляем сообщение пользователя
            try {
                org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage deleteUserMsg = 
                    new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
                deleteUserMsg.setChatId(chatId.toString());
                deleteUserMsg.setMessageId(state.getPromptMessageId() + 1); // Сообщение пользователя идет после prompt
                execute(deleteUserMsg);
            } catch (Exception e) {
                // Игнорируем ошибку
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
                } catch (Exception e) {
                    // Игнорируем ошибку
                }
            }
            
            // РЕДАКТИРУЕМ существующую форму вместо отправки новой
            if (state.getFormMessageId() != null) {
                try {
                    Submission submission = state.getOriginal();
                    
                    // Формируем caption
                    StringBuilder caption = new StringBuilder();
                    caption.append("📋 Детали сертификата #").append(submission.getId()).append("\n\n");
                    caption.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
                    caption.append("🔪 Название: ").append(state.getName() != null ? state.getName() : "не указано").append("\n");
                    caption.append("🏭 Бренд: ").append(state.getBrand() != null ? state.getBrand() : "не указан").append("\n");
                    caption.append("🔢 Индекс: ").append(state.getIndexCode() != null ? state.getIndexCode() : "не указан").append("\n");
                    
                    if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                        caption.append("🔄 Альтернативы: ").append(String.join(", ", state.getAlternativeModels())).append("\n");
                    }
                    
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                    if (submission.getModeratedAt() != null) {
                        caption.append("Одобрено: ").append(submission.getModeratedAt().format(formatter));
                    }
                    
                    // Редактируем caption
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption editCaption = 
                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption();
                    editCaption.setChatId(chatId.toString());
                    editCaption.setMessageId(state.getFormMessageId());
                    editCaption.setCaption(caption.toString());
                    
                    // Обновляем клавиатуру
                    if (state.isApprovedView()) {
                        editCaption.setReplyMarkup(buildApprovedFormKeyboard(submission.getId(), state));
                    } else {
                        editCaption.setReplyMarkup(buildModFormKeyboard(state, submission.getId(), submission));
                    }
                    
                    execute(editCaption);
                    
                } catch (Exception e) {
                    logger.warning("Не удалось отредактировать форму, отправляем новую: " + e.getMessage());
                    // Если не удалось отредактировать - отправляем новую
                    if (state.isApprovedView()) {
                        sendApprovedSubmissionForm(chatId, state.getOriginal().getId());
                    } else {
                        sendSubmissionForm(chatId, state.getOriginal().getId());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при обработке ввода поля: " + e.getMessage());
        }
    }
    
    private void handleModApprove(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            // Сохраняем изменения
            submissionService.updateSubmission(
                submissionId,
                state.getName(),
                state.getBrand(),
                state.getIndexCode(),
                state.getAlternativeModels()
            );
            
            // Одобряем заявку
            Submission submission = submissionService.approveSubmission(submissionId, moderatorId);
            
            // Очищаем состояние
            moderationStates.remove(chatId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            // Уведомляем пользователя
            try {
                knifeBot.execute(new SendMessage(submission.getUserId().toString(), 
                    "✅ Ваша заявка на сертификат одобрена!\n\n" +
                    "🔪 Название: " + (submission.getName() != null ? submission.getName() : "не указано")));
            } catch (Exception e) {
                logger.warning("Не удалось уведомить пользователя: " + e.getMessage());
            }
            
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            logError("Одобрение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }
    
    private void handleModRejectRequest(Long chatId, Long submissionId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите отклонить заявку #" + submissionId + "?\n\nЗаявка будет удалена без возможности восстановления.");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
            .text("✅ Да, отклонить")
            .callbackData("confirm_reject_" + submissionId)
            .build());
        row.add(InlineKeyboardButton.builder()
            .text("❌ Отмена")
            .callbackData("cancel_reject_" + submissionId)
            .build());
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            Message sent = execute(message);
            ModerationState state = moderationStates.get(chatId);
            if (state != null) {
                state.setPromptMessageId(sent.getMessageId());
            }
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending rejection confirmation: " + e.getMessage());
        }
    }
    
    private void handleCancelRejectConfirmation(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state != null && state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete confirmation: " + e.getMessage());
            }
        }
    }
    
    private void handleModReject(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        // Удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete confirmation: " + e.getMessage());
            }
        }
        
        // Удаляем форму
        if (state.getFormMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getFormMessageId());
                execute(deleteMsg);
                state.setFormMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete form: " + e.getMessage());
            }
        }
        
        try {
            // Сохраняем изменения перед отклонением
            submissionService.updateSubmission(
                submissionId,
                state.getName(),
                state.getBrand(),
                state.getIndexCode(),
                state.getAlternativeModels()
            );
            
            // Отклоняем заявку
            Submission submission = submissionService.rejectSubmission(submissionId, moderatorId);
            
            // Очищаем состояние
            moderationStates.remove(chatId);
            
            // Уведомляем пользователя
            try {
                knifeBot.execute(new SendMessage(submission.getUserId().toString(), 
                    "❌ Ваша заявка на сертификат отклонена.\n\n" +
                    "Вы можете подать новую заявку."));
            } catch (Exception e) {
                logger.warning("Не удалось уведомить пользователя: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении: " + e.getMessage());
            logError("Отклонение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
        }
    }
    
    private void handleModCancel(Long chatId, Long submissionId) {
        // Просто удаляем состояние без сохранения изменений
        moderationStates.remove(chatId);
        sendMessage(chatId, "🚫 Изменения отменены");
        sendMainMenu(chatId);
    }
    
    private void handleApprovedSave(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Спрашиваем подтверждение
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите сохранить изменения?");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
            .text("✅ Да, сохранить")
            .callbackData("approved_save_confirm_" + submissionId)
            .build());
        row.add(InlineKeyboardButton.builder()
            .text("❌ Нет")
            .callbackData("approved_save_no_" + submissionId)
            .build());
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error sending save confirmation: " + e.getMessage());
        }
    }
    
    private void handleApprovedSaveConfirm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStates.get(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            // Удаляем сообщение-подтверждение
            if (state.getPromptMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getPromptMessageId());
                    execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete prompt: " + e.getMessage());
                }
            }
            
            // Удаляем форму
            if (state.getFormMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getFormMessageId());
                    execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete form: " + e.getMessage());
                }
            }
            
            // Сохраняем изменения
            submissionService.updateSubmission(
                submissionId,
                state.getName(),
                state.getBrand(),
                state.getIndexCode(),
                state.getAlternativeModels()
            );
            
            moderationStates.remove(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении изменений: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при сохранении изменений");
        }
    }
    
    private void handleApprovedSaveNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Просто удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
    }
    
    private void handleApprovedDelete(Long chatId, Long submissionId) {
        try {
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат не найден");
                return;
            }
            
            Submission submission = submissionOpt.get();
            
            // Удаляем файл с Яндекс.Диска
            try {
                yandexDiskService.deleteFile(submission.getPhotoPath());
                logger.info("Файл удален с Яндекс.Диска: " + submission.getPhotoPath());
            } catch (Exception e) {
                logger.warning("Не удалось удалить файл с Яндекс.Диска: " + e.getMessage());
            }
            
            // Удаляем запись из базы данных
            submissionService.deleteSubmission(submissionId);
            
            moderationStates.remove(chatId);
            sendMessage(chatId, "🗑️ Сертификат #" + submissionId + " удален");
            handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Ошибка при удалении сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при удалении сертификата");
        }
    }
    
    private void handleApprovedCancel(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Проверяем были ли изменения
        if (state.hasChanges()) {
            // Есть изменения - спрашиваем подтверждение
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❓ Вы уверены, что хотите закрыть редактирование?\n\nИзменения не будут сохранены.");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✅ Да, закрыть")
                .callbackData("approved_cancel_confirm_" + submissionId)
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Нет")
                .callbackData("approved_cancel_no_" + submissionId)
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            try {
                Message sent = execute(message);
                state.setPromptMessageId(sent.getMessageId());
            } catch (Exception e) {
                logger.severe("Error sending cancel confirmation: " + e.getMessage());
            }
        } else {
            // Нет изменений - просто закрываем
            handleApprovedCancelConfirm(chatId, submissionId);
        }
    }
    
    private void handleApprovedCancelConfirm(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        
        // Удаляем сообщение-подтверждение если есть
        if (state != null && state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
        
        // Удаляем форму
        if (state != null && state.getFormMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getFormMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete form: " + e.getMessage());
            }
        }
        
        moderationStates.remove(chatId);
    }
    
    private void handleApprovedCancelNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Просто удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
    }
    
    private void handleApprovedSearchRequest(Long chatId) {
        searchStates.put(chatId, "approved");
        sendMessage(chatId, "🔍 Введите название или индекс для поиска:");
    }
    
    private void handleDiskAnalysis(Long chatId) {
        logger.info("Starting disk analysis for chat: " + chatId);
        
        sendMessage(chatId, "🔍 Анализ диска запущен...");
        
        try {
            List<Submission> allSubmissions = submissionService.getAllSubmissions();
            
            // Собираем все пути к фото из базы
            Set<String> usedPhotoPaths = new HashSet<>();
            int brokenLinks = 0;
            StringBuilder brokenList = new StringBuilder();
            
            for (Submission submission : allSubmissions) {
                String photoPath = submission.getPhotoPath();
                if (photoPath == null || photoPath.isEmpty()) {
                    brokenLinks++;
                    brokenList.append("❌ #").append(submission.getId())
                        .append(" - ").append(submission.getDisplayName())
                        .append(" (путь пустой)\n");
                    continue;
                }
                
                try {
                    // Проверяем существование файла на Яндекс.Диске
                    yandexDiskService.getDownloadUrl(photoPath);
                    usedPhotoPaths.add(photoPath);
                } catch (Exception e) {
                    brokenLinks++;
                    brokenList.append("❌ #").append(submission.getId())
                        .append(" - ").append(submission.getDisplayName())
                        .append("\n   Путь: ").append(photoPath).append("\n");
                }
            }
            
            // Проверяем неиспользуемые фото на диске
            int orphanedPhotos = 0;
            StringBuilder orphanedList = new StringBuilder();
            
            try {
                List<String> allPhotosOnDisk = yandexDiskService.listFiles("app:/certificates");
                for (String photoPath : allPhotosOnDisk) {
                    if (!usedPhotoPaths.contains(photoPath)) {
                        orphanedPhotos++;
                        String fileName = photoPath.substring(photoPath.lastIndexOf('/') + 1);
                        orphanedList.append("📄 ").append(fileName).append("\n");
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to list photos on disk: " + e.getMessage());
            }
            
            // Формируем отчет
            StringBuilder report = new StringBuilder();
            report.append("📊 Результаты анализа диска\n\n");
            report.append("Всего сертификатов: ").append(allSubmissions.size()).append("\n");
            report.append("✅ Корректных ссылок: ").append(usedPhotoPaths.size()).append("\n");
            report.append("❌ Битых ссылок: ").append(brokenLinks).append("\n");
            report.append("📄 Неиспользуемых фото: ").append(orphanedPhotos).append("\n\n");
            
            if (brokenLinks > 0) {
                report.append("Битые ссылки:\n").append(brokenList.toString()).append("\n");
            }
            
            if (orphanedPhotos > 0) {
                report.append("Неиспользуемые фото:\n").append(orphanedList.toString());
            }
            
            // Отправляем отчет (разбиваем на части если длинный)
            String reportText = report.toString();
            if (reportText.length() > 4000) {
                int start = 0;
                while (start < reportText.length()) {
                    int end = Math.min(start + 4000, reportText.length());
                    sendMessage(chatId, reportText.substring(start, end));
                    start = end;
                }
            } else {
                sendMessage(chatId, reportText);
            }
            
            // Добавляем кнопки действий
            if (brokenLinks > 0 || orphanedPhotos > 0) {
                SendMessage actionMessage = new SendMessage();
                actionMessage.setChatId(chatId.toString());
                actionMessage.setText("Выберите действие:");
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                if (brokenLinks > 0) {
                    List<InlineKeyboardButton> row1 = new ArrayList<>();
                    row1.add(InlineKeyboardButton.builder()
                        .text("🗑️ Очистить битые ссылки")
                        .callbackData("disk_clean_broken")
                        .build());
                    keyboard.add(row1);
                }
                
                if (orphanedPhotos > 0) {
                    List<InlineKeyboardButton> row2 = new ArrayList<>();
                    row2.add(InlineKeyboardButton.builder()
                        .text("🗑️ Удалить неиспользуемые фото")
                        .callbackData("disk_clean_orphaned")
                        .build());
                    keyboard.add(row2);
                }
                
                List<InlineKeyboardButton> row3 = new ArrayList<>();
                row3.add(InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row3);
                
                markup.setKeyboard(keyboard);
                actionMessage.setReplyMarkup(markup);
                
                execute(actionMessage);
            } else {
                sendMainMenu(chatId);
            }
            
        } catch (Exception e) {
            logger.severe("Error during disk analysis: " + e.getMessage());
            logError("Анализ диска", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при анализе диска: " + e.getMessage());
        }
    }
    
    private void handleCleanBrokenLinks(Long chatId) {
        logger.info("Cleaning broken links for chat: " + chatId);
        
        try {
            List<Submission> allSubmissions = submissionService.getAllSubmissions();
            int deletedCount = 0;
            
            for (Submission submission : allSubmissions) {
                String photoPath = submission.getPhotoPath();
                if (photoPath == null || photoPath.isEmpty()) {
                    submissionService.deleteSubmission(submission.getId());
                    deletedCount++;
                    continue;
                }
                
                try {
                    yandexDiskService.getDownloadUrl(photoPath);
                } catch (Exception e) {
                    submissionService.deleteSubmission(submission.getId());
                    deletedCount++;
                }
            }
            
            sendMessage(chatId, "✅ Удалено сертификатов с битыми ссылками: " + deletedCount);
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Error cleaning broken links: " + e.getMessage());
            logError("Очистка битых ссылок", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при очистке: " + e.getMessage());
        }
    }
    
    private void handleCleanOrphanedPhotos(Long chatId) {
        logger.info("Cleaning orphaned photos for chat: " + chatId);
        
        try {
            List<Submission> allSubmissions = submissionService.getAllSubmissions();
            Set<String> usedPhotoPaths = new HashSet<>();
            
            for (Submission submission : allSubmissions) {
                String photoPath = submission.getPhotoPath();
                if (photoPath != null && !photoPath.isEmpty()) {
                    usedPhotoPaths.add(photoPath);
                }
            }
            
            List<String> allPhotosOnDisk = yandexDiskService.listFiles("app:/certificates");
            int deletedCount = 0;
            
            for (String photoPath : allPhotosOnDisk) {
                if (!usedPhotoPaths.contains(photoPath)) {
                    yandexDiskService.deleteFile(photoPath);
                    deletedCount++;
                }
            }
            
            sendMessage(chatId, "✅ Удалено неиспользуемых фото: " + deletedCount);
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Error cleaning orphaned photos: " + e.getMessage());
            logError("Очистка неиспользуемых фото", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при очистке: " + e.getMessage());
        }
    }
    
    private void logError(String operation, String error) {
        errorLogs.add(new ErrorLog(operation, error));
        while (errorLogs.size() > MAX_ERROR_LOGS) {
            errorLogs.poll();
        }
    }
    
    private void handleErrorLog(Long chatId) {
        logger.info("Showing error log for chat: " + chatId);
        
        if (errorLogs.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📋 Журнал ошибок пуст");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.severe("Error sending empty log message: " + e.getMessage());
            }
            return;
        }
        
        StringBuilder logText = new StringBuilder();
        logText.append("📋 Журнал ошибок (последние ").append(errorLogs.size()).append(")\n\n");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss");
        
        int count = 0;
        for (ErrorLog log : errorLogs) {
            count++;
            logText.append(count).append(". ")
                .append(log.getTimestamp().format(formatter))
                .append("\n   ").append(log.getOperation())
                .append("\n   ❌ ").append(log.getError())
                .append("\n\n");
        }
        
        // Отправляем по частям если длинный
        String fullLog = logText.toString();
        if (fullLog.length() > 4000) {
            int start = 0;
            while (start < fullLog.length()) {
                int end = Math.min(start + 4000, fullLog.length());
                sendMessage(chatId, fullLog.substring(start, end));
                start = end;
            }
        } else {
            sendMessage(chatId, fullLog);
        }
        
        // Кнопки действий
        SendMessage actionMessage = new SendMessage();
        actionMessage.setChatId(chatId.toString());
        actionMessage.setText("Выберите действие:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text("🗑️ Очистить журнал")
            .callbackData("error_log_clear")
            .build());
        keyboard.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text("🔙 Главное меню")
            .callbackData("back_to_menu")
            .build());
        keyboard.add(row2);
        
        markup.setKeyboard(keyboard);
        actionMessage.setReplyMarkup(markup);
        
        try {
            execute(actionMessage);
        } catch (TelegramApiException e) {
            logger.severe("Error sending action message: " + e.getMessage());
        }
    }
    
    private void handleErrorLogClear(Long chatId) {
        logger.info("Clearing error log for chat: " + chatId);
        errorLogs.clear();
        sendMessage(chatId, "✅ Журнал ошибок очищен");
        sendMainMenu(chatId);
    }
    
    private void deleteAllMessagesExceptMenu(Long chatId) {
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null) {
            // Если нет записей, просто очищаем состояния
            moderationStates.remove(chatId);
            searchStates.remove(chatId);
            return;
        }
        
        // Удаляем сообщения из ModerationState
        ModerationState modState = moderationStates.get(chatId);
        if (modState != null) {
            if (modState.getFormMessageId() != null) {
                deleteMessage(chatId, modState.getFormMessageId());
            }
            if (modState.getPromptMessageId() != null) {
                deleteMessage(chatId, modState.getPromptMessageId());
            }
        }
        
        // Удаляем все отслеживаемые сообщения КРОМЕ главного меню
        for (Integer messageId : messages.getOtherMessageIds()) {
            deleteMessage(chatId, messageId);
        }
        
        // Очищаем список и состояния
        messages.clearOtherMessages();
        moderationStates.remove(chatId);
        searchStates.remove(chatId);
    }
    
    private void closeCurrentWindow(Long chatId) {
        logger.info("DEBUG: closeCurrentWindow called for chat " + chatId);
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null) {
            logger.info("DEBUG: No messages found for chat " + chatId);
            return;
        }
        
        // Удаляем последнее сообщение окна
        if (messages.getLastWindowMessageId() != null) {
            logger.info("DEBUG: Deleting lastWindowMessageId: " + messages.getLastWindowMessageId());
            deleteMessage(chatId, messages.getLastWindowMessageId());
            messages.setLastWindowMessageId(null);
        } else {
            logger.info("DEBUG: No lastWindowMessageId to delete");
        }
        
        // Удаляем сообщения из ModerationState
        ModerationState modState = moderationStates.get(chatId);
        if (modState != null) {
            if (modState.getFormMessageId() != null) {
                logger.info("DEBUG: Deleting formMessageId: " + modState.getFormMessageId());
                deleteMessage(chatId, modState.getFormMessageId());
            }
            if (modState.getPromptMessageId() != null) {
                logger.info("DEBUG: Deleting promptMessageId: " + modState.getPromptMessageId());
                deleteMessage(chatId, modState.getPromptMessageId());
            }
        }
        
        // Удаляем все отслеживаемые сообщения
        for (Integer messageId : messages.getOtherMessageIds()) {
            logger.info("DEBUG: Deleting otherMessageId: " + messageId);
            deleteMessage(chatId, messageId);
        }
        
        // Очищаем список и состояния
        messages.clearOtherMessages();
        moderationStates.remove(chatId);
        searchStates.remove(chatId);
        currentWindow.remove(chatId);
        logger.info("DEBUG: closeCurrentWindow completed");
    }
    
    private void deleteRecentMessages(Long chatId, Integer menuMessageId) {
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
                execute(deleteMsg);
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
        moderationStates.remove(chatId);
        searchStates.remove(chatId);
        currentWindow.remove(chatId);
    }
    
    private void deleteMessage(Long chatId, Integer messageId) {
        if (messageId == null) return;
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
        } catch (Exception e) {
            // Игнорируем ошибки удаления (сообщение может быть уже удалено)
        }
    }
    
    private void handleSettingsCommand(Long chatId) {
        sendMessage(chatId, "⚙️ Настройки пока не реализованы");
        sendMainMenu(chatId);
    }
}
