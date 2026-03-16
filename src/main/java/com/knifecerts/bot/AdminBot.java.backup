package com.knifecerts.bot;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

import com.knifecerts.model.Knife;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.service.KnifeService;
import com.knifecerts.service.SubmissionBufferService;
import com.knifecerts.service.YandexDiskService;

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
    private SubmissionBufferService submissionBufferService;

    @Autowired
    private KnifeService knifeService;

    @Autowired
    private KnifeBot knifeBot;
    
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
    
    // Разделитель для альтернативных моделей
    private String alternativeSeparator = ",";
    
    // Внутренний класс для хранения ID сообщений чата
    private static class ChatMessages {
        private Integer mainMenuMessageId;
        private Integer lastWindowMessageId; // ID последнего сообщения окна
        private Integer pendingListMessageId; // ID сообщения со списком ожидающих заявок
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
        public Integer getPendingListMessageId() { return pendingListMessageId; }
        public void setPendingListMessageId(Integer id) { this.pendingListMessageId = id; }
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
        private final Object original; // SubmissionBuffer или Knife
        private String name;
        private String brand;
        private String indexCode;
        private List<String> alternativeModels;
        private Integer formMessageId;
        private Integer promptMessageId;
        private String editingField; // "name", "brand", "index", "alt"
        private boolean isApprovedView; // true если это просмотр одобренного сертификата
        
        public ModerationState(SubmissionBuffer original) {
            this.original = original;
            this.name = original.getModelName();
            this.brand = original.getBrandName();
            this.indexCode = original.getIndex();
            this.alternativeModels = original.getAlternatives().stream()
                .map(alt -> alt.getModelName() + (alt.getBrandName() != null ? " (" + alt.getBrandName() + ")" : ""))
                .collect(java.util.stream.Collectors.toList());
            this.isApprovedView = false;
        }
        
        public ModerationState(Knife original, boolean isApprovedView) {
            this.original = original;
            this.name = original.getModel().getName();
            this.brand = original.getBrand().getName();
            this.indexCode = original.getIndex();
            this.alternativeModels = original.getAlternatives().stream()
                .map(alt -> alt.getModel().getName() + " (" + alt.getBrand().getName() + ")")
                .collect(java.util.stream.Collectors.toList());
            this.isApprovedView = isApprovedView;
        }
        
        // Проверка были ли изменения
        public boolean hasChanges() {
            if (original instanceof SubmissionBuffer) {
                SubmissionBuffer sub = (SubmissionBuffer) original;
                String originalName = sub.getModelName();
                String originalBrand = sub.getBrandName();
                String originalIndex = sub.getIndex();
                List<String> originalAlts = sub.getAlternatives().stream()
                    .map(alt -> alt.getModelName() + (alt.getBrandName() != null ? " (" + alt.getBrandName() + ")" : ""))
                    .collect(java.util.stream.Collectors.toList());
                
                boolean nameChanged = !java.util.Objects.equals(originalName, name);
                boolean brandChanged = !java.util.Objects.equals(originalBrand, brand);
                boolean indexChanged = !java.util.Objects.equals(originalIndex, indexCode);
                boolean altsChanged = !java.util.Objects.equals(originalAlts, alternativeModels);
                
                return nameChanged || brandChanged || indexChanged || altsChanged;
            } else if (original instanceof Knife) {
                Knife knife = (Knife) original;
                String originalName = knife.getModel().getName();
                String originalBrand = knife.getBrand().getName();
                String originalIndex = knife.getIndex();
                List<String> originalAlts = knife.getAlternatives().stream()
                    .map(alt -> alt.getModel().getName() + " (" + alt.getBrand().getName() + ")")
                    .collect(java.util.stream.Collectors.toList());
                
                boolean nameChanged = !java.util.Objects.equals(originalName, name);
                boolean brandChanged = !java.util.Objects.equals(originalBrand, brand);
                boolean indexChanged = !java.util.Objects.equals(originalIndex, indexCode);
                boolean altsChanged = !java.util.Objects.equals(originalAlts, alternativeModels);
                
                return nameChanged || brandChanged || indexChanged || altsChanged;
            }
            return false;
        }
        
        public Object getOriginal() { return original; }
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
                Integer userMessageId = update.getMessage().getMessageId(); // НОВОЕ: Сохраняем ID
                
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
                                // НОВОЕ: Удаляем сообщение пользователя
                                deleteUserMessage(chatId, userMessageId);
                                handleApprovedCommand(chatId, 0, messageText);
                            } else if ("separator".equals(searchType)) {
                                // НОВОЕ: Обработка изменения разделителя
                                handleSeparatorInput(chatId, messageText, update.getMessage().getMessageId());
                            }
                        } else {
                            // Проверяем, есть ли активное состояние редактирования
                            ModerationState state = moderationStates.get(chatId);
                            if (state != null && state.getEditingField() != null) {
                                // НОВОЕ: Удаляем сообщение пользователя ПЕРЕД обработкой
                                deleteUserMessage(chatId, userMessageId);
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
            } else if (data.equals("settings_change_separator")) {
                handleChangeSeparatorRequest(chatId);
            } else if (data.equals("disk_clean_broken")) {
                handleCleanBrokenLinks(chatId);
            } else if (data.equals("disk_clean_orphaned")) {
                handleCleanOrphanedPhotos(chatId);
            } else if (data.equals("error_log_clear")) {
                handleErrorLogClear(chatId);
            } else if (data.equals("back_to_menu")) {
                // Удаляем сообщение с кнопкой
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(callbackQuery.getMessage().getMessageId());
                    execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete message: " + e.getMessage());
                }
                
                // НОВОЕ: Удаляем все отслеживаемые сообщения
                ChatMessages messages = chatMessages.get(chatId);
                if (messages != null) {
                    for (Integer msgId : messages.getOtherMessageIds()) {
                        try {
                            DeleteMessage deleteMsg = new DeleteMessage();
                            deleteMsg.setChatId(chatId.toString());
                            deleteMsg.setMessageId(msgId);
                            execute(deleteMsg);
                        } catch (Exception e) {
                            // Игнорируем ошибки
                        }
                    }
                    messages.clearOtherMessages();
                }
                
                // Очищаем состояния
                moderationStates.remove(chatId);
                searchStates.remove(chatId);
                currentWindow.remove(chatId);
                
                sendMainMenu(chatId);
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
            } else if (data.startsWith("mod_add_alt_")) {
                Long submissionId = Long.parseLong(data.substring(12));
                handleModAddAlternative(chatId, submissionId);
            } else if (data.startsWith("mod_remove_alt_")) {
                String[] parts = data.substring(15).split("_");
                int index = Integer.parseInt(parts[0]);
                Long submissionId = Long.parseLong(parts[1]);
                handleModRemoveAlternative(chatId, submissionId, index);
            } else if (data.startsWith("mod_cancel_input_")) {
                Long submissionId = Long.parseLong(data.substring(17));
                handleModCancelInput(chatId, submissionId);
            } else if (data.equals("alt_separator")) {
                // Ignore clicks on separator
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
            } else if (data.startsWith("mod_cancel_confirm_")) {
                Long submissionId = Long.parseLong(data.substring(19));
                handleModCancelConfirm(chatId, submissionId);
            } else if (data.startsWith("mod_cancel_no_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleModCancelNo(chatId, submissionId);
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
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            if (knifeBot != null) {
                Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
                if (submissionOpt.isPresent()) {
                    SubmissionBuffer submission = submissionOpt.get();
                    String userMessage = "✅ Ваша заявка #" + submission.getId() + " одобрена!\n\n" +
                            "Сертификат добавлен в систему.";
                    knifeBot.notifyUser(submission.getUserId(), userMessage);
                }
            }
            
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    private void handleRejectCallback(Long chatId, Long moderatorId, Long submissionId) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            submissionBufferService.rejectSubmission(submissionId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            if (knifeBot != null) {
                String userMessage = "❌ Ваша заявка #" + submission.getId() + " отклонена.\n\n" +
                        "Вы можете подать новую заявку командой /submit";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
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
            List<SubmissionBuffer> pendingSubmissions = submissionBufferService.getAllPendingSubmissions();
            
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
                
                Message sent = executeAndTrack(message);
                
                // НОВОЕ: Сохраняем ID сообщения со списком
                ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                messages.setPendingListMessageId(sent.getMessageId());
                return;
            }
            
            // Пагинация: 10 строк по 3 кнопки = 30 элементов на страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, pendingSubmissions.size());
            
            List<SubmissionBuffer> pageItems = pendingSubmissions.subList(startIndex, endIndex);
            
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
                SubmissionBuffer sub = pageItems.get(i);
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
            
            Message sent = execute(message);
            
            // НОВОЕ: Сохраняем ID сообщения со списком
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setPendingListMessageId(sent.getMessageId());
            messages.setLastWindowMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка заявок: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка заявок");
        }
    }
    
    /**
     * Обновляет существующий список ожидающих заявок без отправки нового сообщения.
     * Используется после отмены редактирования для проверки изменений в списке.
     */
    private void updatePendingListIfNeeded(Long chatId) {
        ChatMessages messages = chatMessages.get(chatId);
        if (messages == null || messages.getPendingListMessageId() == null) {
            // Если нет сохраненного списка - отправляем новый
            handlePendingCommand(chatId);
            return;
        }
        
        try {
            List<SubmissionBuffer> pendingSubmissions = submissionBufferService.getAllPendingSubmissions();
            
            // Формируем обновленный список (копируем логику из handlePendingCommand)
            int page = 0; // Всегда возвращаемся на первую страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, pendingSubmissions.size());
            
            List<SubmissionBuffer> pageItems = pendingSubmissions.isEmpty() ? 
                new ArrayList<>() : 
                pendingSubmissions.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            if (pendingSubmissions.isEmpty()) {
                text.append("📭 Очередь пуста. Нет ожидающих заявок.");
            } else {
                text.append("📋 Ожидающие заявки\n\n");
                text.append("Всего: ").append(pendingSubmissions.size()).append(" заявок\n");
                text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            }
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            if (!pendingSubmissions.isEmpty()) {
                // Кнопки с заявками (3 колонки, до 10 строк)
                List<InlineKeyboardButton> currentRow = new ArrayList<>();
                for (int i = 0; i < pageItems.size(); i++) {
                    SubmissionBuffer sub = pageItems.get(i);
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
            }
            
            // Кнопка возврата в меню
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            
            // КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: Редактируем существующее сообщение вместо отправки нового
            org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editMessage = 
                new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messages.getPendingListMessageId());
            editMessage.setText(text.toString());
            editMessage.setReplyMarkup(markup);
            
            try {
                execute(editMessage);
                logger.info("Список ожидающих заявок обновлен (message ID: " + messages.getPendingListMessageId() + ")");
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                // Если не удалось отредактировать (сообщение удалено), отправляем новое
                logger.warning("Не удалось отредактировать список, отправляем новый: " + e.getMessage());
                handlePendingCommand(chatId);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении списка заявок: " + e.getMessage());
            // В случае ошибки отправляем новый список
            handlePendingCommand(chatId);
        }
    }
    
    private void handleApprovedCommand(Long chatId, int page) {
        handleApprovedCommand(chatId, page, null);
    }
    
    private void handleApprovedCommand(Long chatId, int page, String searchQuery) {
        try {
            List<Knife> approvedKnives;
            
            // Используем поиск или получаем все одобренные
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                approvedKnives = knifeService.searchKnives(searchQuery);
            } else {
                approvedKnives = knifeService.getAllCertificates();
            }
            
            if (approvedKnives.isEmpty()) {
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
            int totalPages = (int) Math.ceil((double) approvedKnives.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, approvedKnives.size());
            
            List<Knife> pageItems = approvedKnives.subList(startIndex, endIndex);
            
            StringBuilder text = new StringBuilder();
            if (searchQuery != null) {
                text.append("🔍 Результаты поиска: \"").append(searchQuery).append("\"\n\n");
            } else {
                text.append("✅ Одобренные сертификаты\n\n");
            }
            text.append("Всего: ").append(approvedKnives.size()).append(" сертификатов\n");
            text.append("Страница ").append(page + 1).append(" из ").append(totalPages);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Кнопки с сертификатами (3 колонки, до 10 строк)
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                Knife knife = pageItems.get(i);
                String buttonText = knife.getDisplayName();
                
                // Обрезаем длинные названия
                if (buttonText.length() > 15) {
                    buttonText = buttonText.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("view_approved_" + knife.getId())
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
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка #").append(submission.getId()).append("\n\n");
            caption.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
            caption.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            caption.append("📅 Дата: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            caption.append("📊 Статус: Ожидает рассмотрения").append("\n\n");
            caption.append("🔪 Название: ").append(submission.getModelName() != null ? submission.getModelName() : "не указано").append("\n");
            caption.append("🏷️ Бренд: ").append(submission.getBrandName() != null ? submission.getBrandName() : "не указан").append("\n");
            caption.append("🔢 Индекс: ").append(submission.getIndex() != null ? submission.getIndex() : "не указан").append("\n\n");
            
            if (!submission.getAlternatives().isEmpty()) {
                caption.append("🔄 Альтернативные модели:\n");
                for (var alt : submission.getAlternatives()) {
                    caption.append("  • ").append(alt.getModelName());
                    if (alt.getBrandName() != null) {
                        caption.append(" (").append(alt.getBrandName()).append(")");
                    }
                    caption.append("\n");
                }
            }
            
            String photoUrl = yandexDiskService.getDownloadUrl(submission.getPhotoPath());
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(photoUrl));
            sendPhoto.setCaption(caption.toString());
            
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
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }

    private void handleEditCommand(Long chatId, Long moderatorId, String command) {
        sendMessage(chatId, "⚠️ Web App редактор временно недоступен. Используйте кнопки для редактирования.");
    }

    private void handleReviewCommand(Long chatId, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Использование: /review <ID>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена.");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            sendSubmissionDetails(chatId, submission);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /review <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }

    private void sendSubmissionDetails(Long chatId, SubmissionBuffer submission) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder message = new StringBuilder();
            message.append("📄 Детали заявки #").append(submission.getId()).append("\n\n");
            message.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
            message.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            
            if (submission.getModelName() != null) {
                message.append("🔪 Название: ").append(submission.getModelName()).append("\n");
            } else {
                message.append("🔪 Название: не указано\n");
            }
            
            if (submission.getBrandName() != null) {
                message.append("🏷️ Бренд: ").append(submission.getBrandName()).append("\n");
            } else {
                message.append("🏷️ Бренд: не указан\n");
            }
            
            if (submission.getIndex() != null) {
                message.append("🔢 Индекс: ").append(submission.getIndex()).append("\n");
            } else {
                message.append("🔢 Индекс: не указан\n");
            }
            
            message.append("📅 Дата подачи: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            message.append("📊 Статус: Ожидает рассмотрения").append("\n");
            
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
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            if (knifeBot != null) {
                // Получаем информацию о пользователе из заявки перед удалением
                Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
                if (submissionOpt.isPresent()) {
                    SubmissionBuffer submission = submissionOpt.get();
                    String userMessage = "✅ Ваша заявка #" + submission.getId() + " одобрена!\n\n" +
                            "Сертификат добавлен в систему.";
                    knifeBot.notifyUser(submission.getUserId(), userMessage);
                }
            }
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /approve <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
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
            
            // Получаем информацию о пользователе перед удалением заявки
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            submissionBufferService.rejectSubmission(submissionId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            if (knifeBot != null) {
                String userMessage = "❌ Ваша заявка #" + submission.getId() + " отклонена.\n\n" +
                        "Вы можете подать новую заявку командой /submit";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /reject <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
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
            SubmissionBuffer submission = submissionBufferService.getSubmissionByIdWithAlternatives(submissionId);
            
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
            
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            // Минимальный caption с основной информацией
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка на добавление сертификата\n\n");
            caption.append("🆔 ID: #").append(submission.getId()).append("\n");
            caption.append("👤 @").append(submission.getUsername()).append("\n");
            caption.append("📅 ").append(submission.getCreatedAt().format(formatter)).append("\n");
            caption.append("📊 Ожидает рассмотрения");
            
            InlineKeyboardMarkup markup = buildModFormKeyboard(state, submissionId, submission);
            
            // Если форма уже существует - редактируем
            if (state.getFormMessageId() != null) {
                try {
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption editCaption = 
                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption();
                    editCaption.setChatId(chatId.toString());
                    editCaption.setMessageId(state.getFormMessageId());
                    editCaption.setCaption(caption.toString());
                    editCaption.setReplyMarkup(markup);
                    execute(editCaption);
                    return;
                } catch (Exception e) {
                    logger.warning("Не удалось отредактировать форму, отправляем новую: " + e.getMessage());
                }
            }
            
            // Отправляем новую форму с фото
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
    
    private InlineKeyboardMarkup buildModFormKeyboard(ModerationState state, Long submissionId, SubmissionBuffer submission) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Блок 1: Кнопки полей (показывают текущие значения)
        
        // Кнопка "Бренд"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        String brandText = state.getBrand() != null ? 
            "🏷️ " + (state.getBrand().length() > 25 ? state.getBrand().substring(0, 22) + "..." : state.getBrand()) : 
            "🏷️ Бренд";
        row1.add(InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("mod_edit_brand_" + submissionId)
            .build());
        keyboard.add(row1);
        
        // Кнопка "Название"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        String nameText = state.getName() != null ? 
            "📝 " + (state.getName().length() > 25 ? state.getName().substring(0, 22) + "..." : state.getName()) : 
            "📝 Название";
        row2.add(InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("mod_edit_name_" + submissionId)
            .build());
        keyboard.add(row2);
        
        // Кнопка "Индекс"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        String indexText = state.getIndexCode() != null ? 
            "🔢 " + state.getIndexCode() : 
            "🔢 Индекс";
        row3.add(InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("mod_edit_index_" + submissionId)
            .build());
        keyboard.add(row3);
        
        // Блок 2: Альтернативы
        
        // Разделитель альтернатив
        if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
            List<InlineKeyboardButton> separatorRow = new ArrayList<>();
            separatorRow.add(InlineKeyboardButton.builder()
                .text("──── Альтернативы ────")
                .callbackData("alt_separator")
                .build());
            keyboard.add(separatorRow);
            
            // Список альтернатив с кнопками удаления
            for (int i = 0; i < state.getAlternativeModels().size(); i++) {
                String alt = state.getAlternativeModels().get(i);
                List<InlineKeyboardButton> altRow = new ArrayList<>();
                String altText = alt.length() > 35 ? alt.substring(0, 32) + "..." : alt;
                altRow.add(InlineKeyboardButton.builder()
                    .text("❌ " + altText)
                    .callbackData("mod_remove_alt_" + i + "_" + submissionId)
                    .build());
                keyboard.add(altRow);
            }
        }
        
        // Кнопка добавления альтернативы
        List<InlineKeyboardButton> addAltRow = new ArrayList<>();
        addAltRow.add(InlineKeyboardButton.builder()
            .text("➕ Добавить альтернативу")
            .callbackData("mod_add_alt_" + submissionId)
            .build());
        keyboard.add(addAltRow);
        
        // Пустая строка-разделитель
        keyboard.add(new ArrayList<>());
        
        // Блок 3: Действия модератора
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        actionRow.add(InlineKeyboardButton.builder()
            .text("✅ Одобрить")
            .callbackData("mod_approve_" + submissionId)
            .build());
        actionRow.add(InlineKeyboardButton.builder()
            .text("❌ Отклонить")
            .callbackData("mod_reject_" + submissionId)
            .build());
        keyboard.add(actionRow);
        
        // Кнопка отмены
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        cancelRow.add(InlineKeyboardButton.builder()
            .text("🔙 Назад к списку")
            .callbackData("mod_cancel_" + submissionId)
            .build());
        keyboard.add(cancelRow);
        
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
            
            Optional<Knife> knifeOpt = knifeService.getKnifeById(submissionId);
            
            if (knifeOpt.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат #" + submissionId + " не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            // Создаем временную копию для редактирования с флагом isApprovedView
            moderationStates.put(chatId, new ModerationState(knife, true));
            
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
            
            Knife knife = (Knife) state.getOriginal();
            
            // Формируем текст с информацией о сертификате
            StringBuilder caption = new StringBuilder();
            caption.append("✅ Одобренный сертификат\n\n");
            
            caption.append("ID: #").append(knife.getId()).append("\n");
            caption.append("Статус: Одобрен\n");
            
            // Отправляем фото с подписью
            try {
                String photoUrl = yandexDiskService.getDownloadUrl(knife.getPhotoPath());
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
        
        Knife knife = (Knife) state.getOriginal();
        
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
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }
    
    private void handleSetNameCommand(Long chatId, Long moderatorId, String command) {
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }
    
    private void handleSetBrandCommand(Long chatId, Long moderatorId, String command) {
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }
    
    private void handleSetIndexCommand(Long chatId, Long moderatorId, String command) {
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }
    
    private void handleSetAltCommand(Long chatId, Long moderatorId, String command) {
        sendMessage(chatId, "⚠️ Редактирование через команды больше не поддерживается. Используйте кнопки в интерфейсе бота.");
    }
    
    private void handleClearAllCommand(Long chatId, Long moderatorId) {
        try {
            sendMessage(chatId, "⚠️ Очистка всех данных...");
            
            int totalDeleted = 0;
            int dbRecordsDeleted = 0;
            
            // Очистка базы данных - только буфер заявок
            try {
                List<SubmissionBuffer> allSubmissions = submissionBufferService.getAllPendingSubmissions();
                dbRecordsDeleted = allSubmissions.size();
                
                if (dbRecordsDeleted > 0) {
                    for (SubmissionBuffer submission : allSubmissions) {
                        submissionBufferService.rejectSubmission(submission.getId());
                    }
                    sendMessage(chatId, "✅ Буфер заявок очищен. Удалено записей: " + dbRecordsDeleted);
                } else {
                    sendMessage(chatId, "ℹ️ Буфер заявок пуст");
                }
            } catch (Exception e) {
                logger.warning("Ошибка очистки буфера заявок: " + e.getMessage());
                sendMessage(chatId, "⚠️ Ошибка очистки буфера заявок");
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
        
        // НОВОЕ: Удаляем предыдущее сообщение-запрос, если оно существует
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
                logger.info("Deleted previous prompt message: " + state.getPromptMessageId());
            } catch (Exception e) {
                logger.warning("Failed to delete previous prompt: " + e.getMessage());
            }
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
        
        String field = state.getEditingField();
        
        switch (field) {
            case "name":
                state.setName(text);
                break;
            case "brand":
                state.setBrand(text);
                break;
            case "index":
                state.setIndexCode(text);
                break;
            case "alt":
                if (state.getAlternativeModels() == null) {
                    state.setAlternativeModels(new ArrayList<>());
                }
                // Поддержка ввода через запятую
                String[] alternatives = text.split(",");
                for (String alt : alternatives) {
                    String trimmed = alt.trim();
                    if (!trimmed.isEmpty()) {
                        state.getAlternativeModels().add(trimmed);
                    }
                }
                break;
        }
        
        state.setEditingField(null);
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt message: " + e.getMessage());
            }
            state.setPromptMessageId(null);
        }
        
        // Обновляем форму
        Long submissionId = ((SubmissionBuffer) state.getOriginal()).getId();
        sendSubmissionForm(chatId, submissionId);
    }
    
    private void handleModAddAlternative(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Удаляем предыдущее сообщение-запрос, если оно есть
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt message: " + e.getMessage());
            }
            state.setPromptMessageId(null);
        }
        
        state.setEditingField("alt");
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("➕ Введите альтернативу в формате:\nБренд / Название\n\n" +
                "Можно ввести несколько через запятую:\nБренд1 / Название1, Бренд2 / Название2");
            
            // Добавляем кнопку "Отмена"
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("❌ Отмена")
                .callbackData("mod_cancel_input_" + submissionId)
                .build());
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.addOtherMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Error sending alternative prompt: " + e.getMessage());
        }
    }
    
    private void handleModRemoveAlternative(Long chatId, Long submissionId, int index) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null || state.getAlternativeModels() == null) {
            return;
        }
        
        if (index >= 0 && index < state.getAlternativeModels().size()) {
            state.getAlternativeModels().remove(index);
            sendSubmissionForm(chatId, submissionId);
        }
    }
    
    private void handleModCancelInput(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt message: " + e.getMessage());
            }
            state.setPromptMessageId(null);
        }
        
        state.setEditingField(null);
    }
    
    private void handleModApprove(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            // Применяем изменения к заявке
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            
            // Обновляем поля заявки
            submission.setModelName(state.getName());
            submission.setBrandName(state.getBrand());
            submission.setIndex(state.getIndexCode());
            
            // Обновляем альтернативы
            submission.getAlternatives().clear();
            if (state.getAlternativeModels() != null) {
                for (String altStr : state.getAlternativeModels()) {
                    String[] parts = altStr.split(" / ");
                    if (parts.length >= 2) {
                        String brandName = parts[0].trim();
                        String modelName = parts[1].trim();
                        
                        com.knifecerts.model.BufferAlternative alt = new com.knifecerts.model.BufferAlternative(modelName, brandName);
                        submission.addAlternative(alt);
                    }
                }
            }
            
            // Сохраняем изменения (через сервис, который имеет доступ к репозиторию)
            // Просто одобряем заявку - она уже содержит обновленные данные
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
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
            
            // Удаляем сообщение-запрос, если оно есть
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
            
            // Очищаем состояние
            moderationStates.remove(chatId);
            
            // Отправляем уведомление
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            
            // Уведомляем пользователя
            if (knifeBot != null) {
                String userMessage = "✅ Ваша заявка #" + submission.getId() + " одобрена!\n\n" +
                        "Сертификат добавлен в систему.";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
            // Возвращаемся к списку
            handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            logError("Одобрение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }
    
    private void handleModRejectRequest(Long chatId, Long submissionId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("⚠️ Вы уверены, что хотите отклонить заявку #" + submissionId + "?");
            
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
            
            Message sent = execute(message);
            
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.addOtherMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Error sending reject confirmation: " + e.getMessage());
        }
    }
    
    private void handleCancelRejectConfirmation(Long chatId, Long submissionId) {
        // Удаляем только сообщение подтверждения
        ChatMessages messages = chatMessages.get(chatId);
        if (messages != null) {
            for (Integer msgId : messages.getOtherMessageIds()) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(msgId);
                    execute(deleteMsg);
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            messages.clearOtherMessages();
        }
    }
    
    private void handleModReject(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            submissionBufferService.rejectSubmission(submissionId);
            
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
            
            // Удаляем сообщение-запрос, если оно есть
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
            
            // Удаляем сообщение подтверждения отклонения, если оно есть
            ChatMessages messages = chatMessages.get(chatId);
            if (messages != null) {
                for (Integer msgId : messages.getOtherMessageIds()) {
                    try {
                        DeleteMessage deleteMsg = new DeleteMessage();
                        deleteMsg.setChatId(chatId.toString());
                        deleteMsg.setMessageId(msgId);
                        execute(deleteMsg);
                    } catch (Exception e) {
                        // Игнорируем
                    }
                }
                messages.clearOtherMessages();
            }
            
            // Очищаем состояние
            moderationStates.remove(chatId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            // Уведомляем пользователя
            if (knifeBot != null) {
                String userMessage = "❌ Ваша заявка #" + submission.getId() + " отклонена.\n\n" +
                        "Вы можете подать новую заявку командой /submit";
                knifeBot.notifyUser(submission.getUserId(), userMessage);
            }
            
            // Возвращаемся к списку
            handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            logError("Отклонение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
        }
    }
    
    private void handleModCancel(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        if (state == null) {
            return;
        }
        
        // Проверяем, были ли изменения
        if (state.hasChanges()) {
            // Показываем подтверждение отмены
            try {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("⚠️ Вы внесли изменения в заявку.\n\nОтменить изменения и вернуться к списку?");
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("✅ Да, отменить")
                    .callbackData("mod_cancel_confirm_" + submissionId)
                    .build());
                row.add(InlineKeyboardButton.builder()
                    .text("❌ Нет, продолжить")
                    .callbackData("mod_cancel_no_" + submissionId)
                    .build());
                keyboard.add(row);
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                Message sent = execute(message);
                
                ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                messages.addOtherMessageId(sent.getMessageId());
                
            } catch (Exception e) {
                logger.severe("Error sending cancel confirmation: " + e.getMessage());
            }
        } else {
            // Нет изменений - сразу отменяем
            handleModCancelConfirm(chatId, submissionId);
        }
    }
    
    private void handleModCancelConfirm(Long chatId, Long submissionId) {
        ModerationState state = moderationStates.get(chatId);
        
        // Удаляем форму заявки
        if (state != null && state.getFormMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getFormMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete form message: " + e.getMessage());
            }
        }
        
        // Удаляем сообщение-запрос, если оно есть
        if (state != null && state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt message: " + e.getMessage());
            }
        }
        
        // Удаляем сообщение подтверждения
        ChatMessages messages = chatMessages.get(chatId);
        if (messages != null) {
            for (Integer msgId : messages.getOtherMessageIds()) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(msgId);
                    execute(deleteMsg);
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            messages.clearOtherMessages();
        }
        
        // Очищаем состояние модерации (отменяем все изменения)
        moderationStates.remove(chatId);
        
        // ИЗМЕНЕНИЕ: Вместо handlePendingCommand используем updatePendingListIfNeeded
        updatePendingListIfNeeded(chatId);
    }
    
    private void handleModCancelNo(Long chatId, Long submissionId) {
        // Удаляем только сообщение подтверждения
        ChatMessages messages = chatMessages.get(chatId);
        if (messages != null) {
            for (Integer msgId : messages.getOtherMessageIds()) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(msgId);
                    execute(deleteMsg);
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            messages.clearOtherMessages();
        }
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
            
            // TODO: Реализовать сохранение изменений в одобренных сертификатах
            sendMessage(chatId, "⚠️ Редактирование одобренных сертификатов пока не реализовано в новой схеме");
            
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
            Optional<Knife> knifeOpt = knifeService.getKnifeById(submissionId);
            
            if (knifeOpt.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            // Удаляем файл с Яндекс.Диска
            try {
                if (knife.getPhotoPath() != null) {
                    yandexDiskService.deleteFile(knife.getPhotoPath());
                    logger.info("Файл удален с Яндекс.Диска: " + knife.getPhotoPath());
                }
            } catch (Exception e) {
                logger.warning("Не удалось удалить файл с Яндекс.Диска: " + e.getMessage());
            }
            
            // TODO: Реализовать удаление сертификата из новой схемы
            sendMessage(chatId, "⚠️ Удаление сертификатов пока не реализовано в новой схеме");
            
            moderationStates.remove(chatId);
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
        sendMessage(chatId, "⚠️ Анализ диска временно недоступен в новой схеме базы данных. Функция будет добавлена позже.");
        sendMainMenu(chatId);
    }
    
    private void handleCleanBrokenLinks(Long chatId) {
        sendMessage(chatId, "⚠️ Очистка битых ссылок временно недоступна в новой схеме базы данных. Функция будет добавлена позже.");
        sendMainMenu(chatId);
    }
    
    private void handleCleanOrphanedPhotos(Long chatId) {
        sendMessage(chatId, "⚠️ Очистка неиспользуемых фото временно недоступна в новой схеме базы данных. Функция будет добавлена позже.");
        sendMainMenu(chatId);
    }
    
    private void logError(String operation, String error) {
        errorLogs.add(new ErrorLog(operation, error));
        while (errorLogs.size() > MAX_ERROR_LOGS) {
            errorLogs.poll();
        }
    }
    
    private void handleErrorLog(Long chatId) {
        logger.info("Showing error log for chat: " + chatId);
        
        ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
        
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
                Message sent = execute(message);
                messages.setLastWindowMessageId(sent.getMessageId());
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
                try {
                    Message sent = execute(new SendMessage(chatId.toString(), fullLog.substring(start, end)));
                    // НОВОЕ: Сохраняем ID каждого отправленного сообщения
                    messages.addOtherMessageId(sent.getMessageId());
                } catch (TelegramApiException e) {
                    logger.severe("Error sending log part: " + e.getMessage());
                }
                start = end;
            }
        } else {
            try {
                Message sent = execute(new SendMessage(chatId.toString(), fullLog));
                // НОВОЕ: Сохраняем ID сообщения
                messages.addOtherMessageId(sent.getMessageId());
            } catch (TelegramApiException e) {
                logger.severe("Error sending log: " + e.getMessage());
            }
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
            Message sent = execute(actionMessage);
            // НОВОЕ: Сохраняем ID сообщения с кнопками
            messages.setLastWindowMessageId(sent.getMessageId());
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
    
    /**
     * Удаляет сообщение пользователя из чата.
     * Используется для очистки чата от введенных данных после их обработки.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     */
    private void deleteUserMessage(Long chatId, Integer messageId) {
        if (messageId == null) {
            logger.warning("Попытка удалить сообщение с null ID");
            return;
        }
        
        try {
            DeleteMessage deleteMsg = new DeleteMessage();
            deleteMsg.setChatId(chatId.toString());
            deleteMsg.setMessageId(messageId);
            execute(deleteMsg);
            logger.info("Удалено сообщение пользователя: " + messageId + " в чате " + chatId);
        } catch (TelegramApiException e) {
            // Логируем, но не прерываем выполнение
            logger.warning("Не удалось удалить сообщение пользователя " + messageId + ": " + e.getMessage());
        }
    }
    
    private void handleChangeSeparatorRequest(Long chatId) {
        // Сохраняем состояние ожидания ввода разделителя
        searchStates.put(chatId, "separator");
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("✏️ Введите новый знак разделителя для альтернативных моделей:\n\n" +
                       "Текущий: \"" + alternativeSeparator + "\"\n\n" +
                       "Примеры: , (запятая), ; (точка с запятой), | (вертикальная черта)");
        
        try {
            Message sent = execute(message);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.addOtherMessageId(sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending separator request: " + e.getMessage());
        }
    }

    private void handleSeparatorInput(Long chatId, String separator, Integer userMessageId) {
        // Удаляем сообщение пользователя
        try {
            DeleteMessage deleteMsg = new DeleteMessage();
            deleteMsg.setChatId(chatId.toString());
            deleteMsg.setMessageId(userMessageId);
            execute(deleteMsg);
        } catch (Exception e) {
            logger.warning("Failed to delete user message: " + e.getMessage());
        }
        
        // Валидация разделителя
        if (separator == null || separator.trim().isEmpty()) {
            sendMessage(chatId, "❌ Разделитель не может быть пустым");
            handleSettingsCommand(chatId);
            return;
        }
        
        if (separator.length() > 3) {
            sendMessage(chatId, "❌ Разделитель не может быть длиннее 3 символов");
            handleSettingsCommand(chatId);
            return;
        }
        
        // Сохраняем новый разделитель
        alternativeSeparator = separator.trim();
        
        sendMessage(chatId, "✅ Разделитель изменен на: \"" + alternativeSeparator + "\"");
        handleSettingsCommand(chatId);
    }

    private void handleSettingsCommand(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚙️ Настройки\n\nТекущий разделитель альтернатив: \"" + alternativeSeparator + "\"");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка для изменения разделителя
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text("✏️ Изменить знак разделителя")
            .callbackData("settings_change_separator")
            .build());
        keyboard.add(row1);
        
        // Кнопка закрытия
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text("❌ Закрыть настройки")
            .callbackData("back_to_menu")
            .build());
        keyboard.add(row2);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            Message sent = execute(message);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending settings: " + e.getMessage());
        }
    }
}
