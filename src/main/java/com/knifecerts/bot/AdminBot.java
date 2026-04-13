package com.knifecerts.bot;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.knifecerts.model.*;
import com.knifecerts.repository.*;
import com.knifecerts.service.*;
import com.knifecerts.util.RowBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.dto.AlternativeEntry;

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
    private NavigationStackService navigationStackService;
    
    @Autowired
    private BrandRepository brandRepository;
    
    @Autowired
    private KnifeModelRepository knifeModelRepository;
    
    @Autowired
    private KnifeRepository knifeRepository;

    @Autowired
    private ModerationStateService moderationStateService;

    @Autowired
    private UserMainMenuRepository userMainMenuRepository;
    
    @Autowired
    private TransitiveAlternativesService transitiveAlternativesService;
    
    @Autowired
    private SubmissionBufferRepository submissionBufferRepository;
    
    @Autowired
    private MainMenuUpdateService mainMenuUpdateService;
    
    @Autowired
    private CaptionParser captionParser;

    @Autowired
    private SettingsService settingsService;
    
    @Autowired
    private AlternativesParser alternativesParser;
    
    // Хранилище состояний поиска для каждого чата
    private final java.util.Map<Long, String> searchStates = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Хранилище шагов ожидания фото для каждого чата (AdminBot-specific)
    private final java.util.Map<Long, com.knifecerts.model.ConversationStep> adminPhotoSteps = new java.util.concurrent.ConcurrentHashMap<>();
    
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
                    recentWindowMessages.removeFirst();
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
                        // Удаляем отправленное пользователем сообщение
                        deleteMessage(chatId, update.getMessage().getMessageId());
                        // Удаляем старое главное меню перед отправкой нового
                        ChatMessages oldMessages = chatMessages.get(chatId);
                        if (oldMessages != null && oldMessages.getMainMenuMessageId() != null)
                            deleteMessage(chatId, oldMessages.getMainMenuMessageId());
                        
                        sendMainMenu(chatId);
                    } else {
                        // Проверяем, есть ли активное состояние поиска
                        if (searchStates.containsKey(chatId)) {
                            String searchType = searchStates.get(chatId);
                            searchStates.remove(chatId);
                            
                            if ("approved".equals(searchType)) {
                                // НОВОЕ: Удаляем сообщение пользователя
                                deleteMessage(chatId, userMessageId);
                                handleApprovedCommand(chatId, 0, messageText);
                            } else if ("admin_search".equals(searchType)) {
                                // Удаляем сообщение пользователя
                                deleteMessage(chatId, userMessageId);
                                List<Brand> brands = brandRepository.searchByName(messageText);
                                if (brands.isEmpty()) {
                                    sendMessage(chatId, "❌ Брендов не найдено");
                                } else {
                                    showAdminSearchResults(chatId, new ArrayList<>(brands), "brand", 0, messageText);
                                }
                            } else if (searchType.startsWith("admin_search_models_")) {
                                // Удаляем сообщение пользователя
                                deleteMessage(chatId, userMessageId);
                                String brandName = searchType.substring(20);
                                handleAdminSearchModelsInput(chatId, messageText, brandName);
                            } else if ("separator".equals(searchType)) {
                                // НОВОЕ: Обработка изменения разделителя
                                handleSeparatorInput(chatId, messageText, update.getMessage().getMessageId());
                            }
                        } else {
                            ModerationState state = moderationStateService.getState(chatId);
                            if (state != null && state.getEditingField() != null) {
                                // Проверяем, ожидаем ли мы фото для загрузки
                                com.knifecerts.model.ConversationStep adminStep = adminPhotoSteps.get(chatId);
                                if (adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_UPLOAD_PHOTO) {
                                    handleUploadFieldInput(chatId, messageText, userMessageId);
                                } else {
                                    handleModFieldInput(chatId, messageText, userMessageId);
                                }
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
            if (e.getCause() instanceof java.net.UnknownHostException || e.getCause() instanceof java.net.SocketTimeoutException) {
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
        String toastMessage = null; // Сообщение для Toast-уведомления
        
        try {
            // При любой кнопке главного меню - удаляем последние сообщения
            if (data.startsWith("menu_")) {
                deleteRecentMessages(chatId, callbackQuery.getMessage().getMessageId());
            }
            
            // Обработка выбора бренда в главном меню админа
            if (data.startsWith("admin_brand_")) {
                String brandName = data.substring(12);
                handleAdminBrandSelection(chatId, brandName);
                return;
            } else if (data.startsWith("admin_main_page_")) {
                int page = Integer.parseInt(data.substring(16));
                updateAdminMainMenu(chatId, page);
                return;
            } else if (data.equals("admin_main_current_page")) {
                // Ignore - это просто индикатор страницы
                return;
            }
            
            if (data.equals("menu_pending")) {
                handlePendingCommand(chatId);
            } else if (data.equals("menu_approved")) {
                handleApprovedCommand(chatId, 0);
            } else if (data.equals("menu_search")) {
                handleAdminSearchRequest(chatId);
            } else if (data.equals("menu_upload")) {
                // Начинаем процесс прямой загрузки сертификата
                adminPhotoSteps.put(chatId, com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_UPLOAD_PHOTO);
                String sep = settingsService.getAlternativeSeparator();
                sendMessage(chatId, "📤 Отправьте фото для прямой загрузки сертификата.\n\n" +
                        "Формат: бренд" + sep + "название" + sep + "индекс или бренд" + sep + "название или только название");
            } else if (data.equals("menu_error_log")) {
                handleErrorLog(chatId);
            } else if (data.equals("menu_settings")) {
                handleSettingsCommand(chatId);
            } else if (data.equals("menu_test")) {
                System.out.println("hello world!");
                toastMessage = "hello world!";
            } else if (data.equals("menu_back_to_main")) {
                // Удаляем все сообщения кроме главного меню
                deleteRecentMessages(chatId, null);
                // Главное меню уже есть, просто возвращаемся к нему
                return;
            } else if (data.equals("settings_change_separator")) {
                handleChangeSeparatorRequest(chatId);
            } else if (data.equals("error_log_clear")) {
                handleErrorLogClear(chatId);
            } else if (data.equals("upload_edit_brand")) {
                handleUploadEditField(chatId, "brand");
            } else if (data.equals("upload_edit_name")) {
                handleUploadEditField(chatId, "name");
            } else if (data.equals("upload_edit_index")) {
                handleUploadEditField(chatId, "index");
            } else if (data.equals("upload_edit_alt")) {
                handleUploadEditAlt(chatId);
            } else if (data.equals("upload_save")) {
                handleUploadSave(chatId);
                toastMessage = "💾 Сертификат сохранен";
            } else if (data.equals("upload_add")) {
                handleUploadAdd(chatId);
            } else if (data.equals("upload_cancel")) {
                handleUploadCancelRequest(chatId);
            } else if (data.equals("upload_cancel_confirm")) {
                handleUploadCancelConfirm(chatId);
            } else if (data.equals("upload_cancel_no")) {
                handleUploadCancelNo(chatId);
            } else if (data.equals("back_to_menu")) {
                // Удаляем сообщение с кнопкой
                deleteMessage(chatId, callbackQuery.getMessage().getMessageId());
                // Вызываем deleteRecentMessages для удаления всех отслеживаемых сообщений
                deleteRecentMessages(chatId, null);
                // Очищаем состояния
                moderationStateService.removeState(chatId);
                searchStates.remove(chatId);
                currentWindow.remove(chatId);
                
                // Очищаем навигационный стек до уровня 0
                navigationStackService.clearFrom(chatId, 1);
                
                // Проверяем, есть ли уже главное меню (в памяти или в БД)
                ChatMessages messages = chatMessages.get(chatId);
                Integer mainMenuId = messages != null ? messages.getMainMenuMessageId() : null;
                if (mainMenuId == null) {
                    UserMainMenu saved = userMainMenuRepository.findById(chatId).orElse(null);
                    if (saved != null) {
                        mainMenuId = saved.getMessageId();
                        if (messages != null) {
                            messages.setMainMenuMessageId(mainMenuId);
                        }
                    }
                }
                if (mainMenuId == null) {
                    sendMainMenu(chatId);
                }
                // Если главное меню уже есть - ничего не делаем
            } else if (data.startsWith("view_approved_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                showApprovedSubmissionDetails(chatId, submissionId);
            } else if (data.startsWith("view_")) {
                Long submissionId = Long.parseLong(data.substring(5));
                handleViewSubmissionWithCheck(chatId, submissionId);
            } else if (data.startsWith("approve_")) {
                Long submissionId = Long.parseLong(data.substring(8));
                handleApproveCallback(chatId, submissionId);
                toastMessage = String.format("✅ Заявка %d одобрена", submissionId);
            } else if (data.startsWith("reject_")) {
                Long submissionId = Long.parseLong(data.substring(7));
                handleRejectCallback(chatId, submissionId);
                toastMessage = String.format("❌ Заявка %d отклонена", submissionId);
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
                toastMessage = "✅ Заявка одобрена";
            } else if (data.startsWith("mod_reject_")) {
                Long submissionId = Long.parseLong(data.substring(11));
                handleModRejectRequest(chatId, submissionId);
            } else if (data.startsWith("mod_save_exit_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleModSaveAndExit(chatId, submissionId);
                toastMessage = "💾 Изменения сохранены";
            } else if (data.startsWith("confirm_reject_")) {
                Long submissionId = Long.parseLong(data.substring(15));
                handleModReject(chatId, moderatorId, submissionId);
                toastMessage = "❌ Заявка отклонена";
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
                toastMessage = "💾 Изменения сохранены";
            } else if (data.startsWith("approved_save_no_")) {
                Long submissionId = Long.parseLong(data.substring(17));
                handleApprovedSaveNo(chatId, submissionId);
            } else if (data.startsWith("approved_save_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleApprovedSave(chatId, submissionId);
                toastMessage = "💾 Изменения сохранены";
            } else if (data.startsWith("approved_delete_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                handleApprovedDelete(chatId, submissionId);
                toastMessage = "🗑️ Сертификат удален";
            } else if (data.startsWith("approved_cancel_confirm_")) {
                Long submissionId = Long.parseLong(data.substring(24));
                handleApprovedCancelConfirm(chatId, submissionId);
            } else if (data.startsWith("approved_cancel_no_")) {
                Long submissionId = Long.parseLong(data.substring(19));
                handleApprovedCancelNo(chatId, submissionId);
            } else if (data.startsWith("approved_cancel_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                handleApprovedCancel(chatId, submissionId);
            } else if (data.startsWith("approved_photo_cancel_")) {
                Long knifeId = Long.parseLong(data.substring(22));
                handleApprovedPhotoCancelRequest(chatId, knifeId);
            } else if (data.startsWith("approved_photo_")) {
                Long knifeId = Long.parseLong(data.substring(15));
                handleApprovedPhotoRequest(chatId, knifeId);
            } else if (data.startsWith("admin_brand_models_page_")) {
                // Parse: admin_brand_models_page_N_brandName
                String[] parts = data.substring(24).split("_", 2);
                int page = Integer.parseInt(parts[0]);
                String brandName = parts[1];
                handleAdminBrandModelsPage(chatId, page, brandName);
            } else if (data.equals("admin_brand_models_current_page")) {
                // Ignore clicks on current page indicator
            } else if (data.startsWith("admin_search_models_page_")) {
                // Parse: admin_search_models_page_N_brandName_searchQuery
                String[] parts = data.substring(25).split("_", 3);
                int page = Integer.parseInt(parts[0]);
                String brandName = parts[1];
                String searchQuery = parts[2];
                handleAdminSearchModelsPage(chatId, page, brandName, searchQuery);
            } else if (data.equals("admin_search_models_current_page")) {
                // Ignore clicks on current page indicator
            } else if (data.startsWith("admin_search_models_")) {
                String brandName = data.substring(19);
                handleAdminSearchModelsRequest(chatId, brandName);
            } else if (data.startsWith("admin_search_page_")) {
                // Parse: admin_search_page_N_type where type is "brand" or "model"
                String[] parts = data.substring(18).split("_");
                int page = Integer.parseInt(parts[0]);
                String type = parts[1];
                String searchQuery = data.substring(18 + String.valueOf(page).length() + 1 + type.length() + 1);
                handleAdminSearchPage(chatId, page, type, searchQuery);
            } else if (data.equals("admin_search_current_page")) {
                // Ignore clicks on current page indicator
            } else if (data.startsWith("admin_search_brand_")) {
                String brandName = data.substring(19);
                handleAdminSearchBrandSelect(chatId, brandName);
            } else if (data.startsWith("admin_search_model_")) {
                Long modelId = Long.parseLong(data.substring(19));
                handleAdminSearchModelSelect(chatId, modelId);
            } else if (data.equals("admin_search_back")) {
                handleAdminSearchRequest(chatId);
            } else if (data.startsWith("transitive_yes_")) {
                Long knifeId = Long.parseLong(data.substring(15));
                handleTransitiveYes(chatId, knifeId);
            } else if (data.startsWith("transitive_no_")) {
                Long knifeId = Long.parseLong(data.substring(14));
                handleTransitiveNo(chatId, knifeId);
            } else if (data.startsWith("transitive_pending_yes_")) {
                Long knifeId = Long.parseLong(data.substring(23));
                handleTransitivePendingYes(chatId, knifeId);
            } else if (data.startsWith("transitive_pending_no_")) {
                Long knifeId = Long.parseLong(data.substring(22));
                handleTransitivePendingNo(chatId, knifeId);
            } else if (data.startsWith("alt_confirm_yes_")) {
                Long submissionId = Long.parseLong(data.substring(16));
                handleAltConfirmYes(chatId, submissionId);
            } else if (data.startsWith("alt_confirm_no_")) {
                Long submissionId = Long.parseLong(data.substring(15));
                handleAltConfirmNo(chatId, submissionId);
            } else if (data.startsWith("mod_replace_photo_yes_")) {
                Long submissionId = Long.parseLong(data.substring(22));
                handleDuplicatePhotoYes(chatId, submissionId);
            } else if (data.startsWith("mod_replace_photo_no_")) {
                Long submissionId = Long.parseLong(data.substring(21));
                handleDuplicatePhotoNo(chatId, submissionId);
            } else if (data.startsWith("switch_confirm_yes_")) {
                Long submissionId = Long.parseLong(data.substring(19));
                handleSwitchConfirmYes(chatId, submissionId);
            } else if (data.equals("switch_confirm_no")) {
                handleSwitchConfirmNo(chatId);
            }
            
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            if (toastMessage != null) {
                answer.setText(toastMessage);
                answer.setShowAlert(false); // Toast notification, не popup
                logger.info("Отправляю toast: " + toastMessage);
            }
            execute(answer);

        } catch (Exception e) {
            logger.severe("Ошибка при обработке callback: " + e.getMessage());
            e.printStackTrace();
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

    private void handleApproveCallback(Long chatId, Long submissionId) {
        try {
            submissionBufferService.approveSubmission(submissionId);
            moderationStateService.removeState(chatId);
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    private void handleRejectCallback(Long chatId, Long submissionId) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            submissionBufferService.rejectSubmission(submissionId);
            moderationStateService.removeState(chatId);
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
            
            // Проверяем, ожидает ли AdminBot фото для замены/установки/загрузки
            com.knifecerts.model.ConversationStep adminStep = adminPhotoSteps.get(chatId);
            if (adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_REPLACEMENT_PHOTO ||
                adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_SET_PHOTO ||
                adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_UPLOAD_PHOTO) {
                
                adminPhotoSteps.remove(chatId);
                
                // Удаляем сообщение пользователя с фото
                deleteMessage(chatId, update.getMessage().getMessageId());
                
                // Скачиваем фото из Telegram
                GetFile getFileMethod = new GetFile();
                getFileMethod.setFileId(photo.getFileId());
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
                String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
                
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String newFileName = "photo_" + timestamp + ".jpg";
                String newPath;
                
                try (java.io.InputStream photoStream = new java.net.URL(fileUrl).openStream()) {
                    // Загружаем фото в app:/certificates/
                    newPath = yandexDiskService.uploadToCertificates(photoStream, newFileName);
                }
                
                // Для ADMIN_WAITING_FOR_UPLOAD_PHOTO показываем форму добавления
                if (adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_UPLOAD_PHOTO) {
                    // Требование 15.3: Парсим caption через CaptionParser
                    String caption = update.getMessage().getCaption();
                    
                    // Сохраняем путь к фото в состояние для последующего заполнения формы
                    ModerationState state = moderationStateService.getState(chatId);
                    if (state == null)
                        state = moderationStateService.createEmptyState(chatId);
                    state.setPhotoPath(newPath);
                    
                    // Парсим caption если он есть
                    if (caption != null && !caption.trim().isEmpty()) {
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
                    
                    // Показываем форму добавления (Req 15.2)
                    sendUploadForm(chatId, state);
                    return;
                }
                
                // Для замены/установки фото
                ModerationState state = moderationStateService.getState(chatId);
                if (state == null || state.getApprovedKnifeId() == null) {
                    sendMessage(chatId, "❌ Состояние не найдено. Попробуйте снова.");
                    return;
                }
                
                Long knifeId = state.getApprovedKnifeId();
                
                Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
                if (knifeOpt.isEmpty()) {
                    sendMessage(chatId, "❌ Нож не найден");
                    return;
                }
                Knife knife = knifeOpt.get();
                
                try (java.io.InputStream photoStream = new java.net.URL(fileUrl).openStream()) {
                    if (adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_REPLACEMENT_PHOTO) {
                        String oldPath = knife.getPhotoPath();
                        if (oldPath != null && !oldPath.isEmpty()) {
                            String archivedFileName = "photo_" + timestamp + "_" + knifeId + ".jpg";
                            String archivePath = "app:/archive/replaced/" + archivedFileName;
                            try {
                                yandexDiskService.moveFile(oldPath, archivePath);
                                logger.info("Старое фото перемещено в архив: " + archivePath);
                            } catch (Exception e) {
                                logger.warning("Не удалось переместить старое фото в архив: " + e.getMessage());
                            }
                        }
                    }
                    newPath = yandexDiskService.uploadToCertificates(photoStream, newFileName);
                }
                
                // Обновляем photo_path в knives
                knife.setPhotoPath(newPath);
                knifeRepository.save(knife);

                // Пересоздаём ModerationState с обновлённым ножом
                Integer oldFormMessageId = state.getFormMessageId();
                moderationStateService.initState(chatId, knife);
                ModerationState newState = moderationStateService.getState(chatId);
                newState.setFormMessageId(oldFormMessageId);
                
                sendApprovedSubmissionForm(chatId, knifeId);
                return;
            }

        } catch (Exception e) {
            logger.severe("Error handling photo: " + e.getClass().getName() + " - " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при загрузке фото. Попробуйте еще раз.");
        }
    }

    /**
     * Обрабатывает нажатие кнопки фото в форме одобренного сертификата.
     * Req 11.3: запрашивает новое фото у администратора.
     * Req 11.1: кнопка с путём к файлу для замены.
     * Req 11.2: кнопка "Установить фото" для модели без фото.
     */
    private void handleApprovedPhotoRequest(Long chatId, Long knifeId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        Knife knife = (Knife) state.getOriginal();
        boolean hasPhoto = knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty();
        
        // Сохраняем ID ножа в состоянии для последующей обработки фото
        state.setApprovedKnifeId(knifeId);
        
        // Устанавливаем шаг ожидания фото
        if (hasPhoto) {
            adminPhotoSteps.put(chatId, com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_REPLACEMENT_PHOTO);
        } else {
            adminPhotoSteps.put(chatId, com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_SET_PHOTO);
        }
        
        String promptText = hasPhoto
            ? "📷 Отправьте новое фото для замены существующего.\n\nСтарое фото будет перемещено в архив."
            : "📷 Отправьте фото для установки сертификата.";
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(promptText);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            keyboard.add(RowBuilder.getRow("❌ Отмена", "approved_photo_cancel_" + knifeId));
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при запросе фото: " + e.getMessage());
        }
    }
    
    /**
     * Отменяет ожидание нового фото.
     */
    private void handleApprovedPhotoCancelRequest(Long chatId, Long knifeId) {
        adminPhotoSteps.remove(chatId);
        
        ModerationState state = moderationStateService.getState(chatId);
        if (state != null && state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
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
                keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
                
                markup.setKeyboard(keyboard);
                message.setReplyMarkup(markup);
                
                Message sent = executeAndTrack(message);
                
                // НОВОЕ: Сохраняем ID сообщения со списком
                ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                messages.setPendingListMessageId(sent.getMessageId());
                
                // Устанавливаем список заявок на уровень 1 стека
                navigationStackService.setLevel(chatId, 1, sent.getMessageId());
                return;
            }
            
            // Пагинация: 10 строк по 3 кнопки = 30 элементов на страницу
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / itemsPerPage);
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, pendingSubmissions.size());
            
            List<SubmissionBuffer> pageItems = pendingSubmissions.subList(startIndex, endIndex);

            String text = "📋 Ожидающие заявки\n\n" +
                    "Всего: " + pendingSubmissions.size() + " заявок\n" +
                    "Страница " + (page + 1) + " из " + totalPages;
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (SubmissionBuffer sub : pageItems) {
                String buttonText = sub.getDisplayName();

                if (buttonText.length() > 15)
                    buttonText = buttonText.substring(0, 12) + "...";

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
            keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message sent = execute(message);
            
            // НОВОЕ: Сохраняем ID сообщения со списком
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setPendingListMessageId(sent.getMessageId());
            messages.setLastWindowMessageId(sent.getMessageId());
            
            // Устанавливаем список заявок на уровень 1 стека
            navigationStackService.setLevel(chatId, 1, sent.getMessageId());
            
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
            logger.info("Submission list is null");
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
                    RowBuilder.addRow(currentRow, buttonText, "view_" + sub.getId());
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

                    if (page > 0)
                        RowBuilder.addRow(paginationRow, "⬅️ Назад", "pending_page_" + (page - 1));

                    RowBuilder.addRow(paginationRow, String.format("%d/%d", page + 1, totalPages), "pending_current_page");
                    RowBuilder.addRow(paginationRow, "Вперёд ➡️", "pending_page_" + (page + 1));
                    keyboard.add(paginationRow);
                }
            }
            keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
            
            markup.setKeyboard(keyboard);

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
                if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                    // Содержимое не изменилось — игнорируем, ничего делать не нужно
                    return;
                }
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
                RowBuilder.addRow(currentRow, buttonText, "view_approved_" + knife.getId());

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
                    RowBuilder.addRow(paginationRow, "⬅️ Назад", prevCallback);
                }

                RowBuilder.addRow(paginationRow, String.format("%d/%d", page + 1, totalPages), "approved_current_page");

                if (page < totalPages - 1) {
                    String nextCallback = searchQuery != null 
                        ? "approved_page_" + (page + 1) + "_search_" + searchQuery
                        : "approved_page_" + (page + 1);
                    RowBuilder.addRow(paginationRow, "Вперёд ➡️", nextCallback);
                }
                
                keyboard.add(paginationRow);
            }
            
            // Кнопка поиска
            List<InlineKeyboardButton> searchRow = new ArrayList<>();
            if (searchQuery != null)
                RowBuilder.addRow(searchRow, "🔄 Сбросить поиск", "approved_reset_search");
            else
                RowBuilder.addRow(searchRow, "🔍 Поиск", "approved_search");

            keyboard.add(searchRow);
            
            // Кнопка возврата в меню
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            RowBuilder.addRow(menuRow, "🔙 Главное меню", "back_to_menu");
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            executeAndTrack(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка одобренных сертификатов: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка сертификатов");
        }
    }

    private Message sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            Message sent = execute(message);
            logger.info("DEBUG: Sent message with ID: " + sent.getMessageId());
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
            logger.info("DEBUG: Saved lastWindowMessageId: " + sent.getMessageId());
            return sent;
        } catch (TelegramApiException e) {
            logger.severe("Error sending message: " + e.getMessage());
            return null;
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
        try {
            List<Brand> allBrands = knifeService.getAllBrandsWithKnives();
            
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) allBrands.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            int page = 0; // Всегда показываем первую страницу
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allBrands.size());
            List<Brand> pageBrands = allBrands.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔧 **Админ-панель**");
            message.setParseMode("Markdown");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Сетка брендов (3 колонки)
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageBrands.size(); i++) {
                Brand brand = pageBrands.get(i);
                String displayName = brand.getName();
                if (displayName.length() > 15) {
                    displayName = displayName.substring(0, 12) + "...";
                }
                RowBuilder.addRow(currentRow, displayName, "admin_brand_" + brand.getName());
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            // Пагинация (если нужна)
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                RowBuilder.addRow(paginationRow, "⬅️", "admin_main_page_" + (totalPages - 1));
                RowBuilder.addRow(paginationRow, "1/" + totalPages, "admin_main_current_page");
                RowBuilder.addRow(paginationRow, "➡️", "admin_main_page_1");
                keyboard.add(paginationRow);
            }
            keyboard.add(RowBuilder.getRow("🔍 Поиск", "menu_search"));
            keyboard.add(RowBuilder.getRow("📤 Загрузить","menu_upload"));
            keyboard.add(RowBuilder.getRow("📋 Ожидающие заявки", "menu_pending"));
            keyboard.add(RowBuilder.getRow("📋 Журнал ошибок", "menu_error_log"));
            keyboard.add(RowBuilder.getRow("⚙️ Настройки", "menu_settings"));
            keyboard.add(RowBuilder.getRow("🧪 Тест", "menu_test"));
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            Message sent = execute(message);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setMainMenuMessageId(sent.getMessageId());
            
            // Устанавливаем главное меню на уровень 0 стека
            navigationStackService.setLevel(chatId, 0, sent.getMessageId());
            userMainMenuRepository.save(new UserMainMenu(chatId, sent.getMessageId()));
        } catch (Exception e) {
            logger.severe("Error sending admin menu: " + e.getMessage());
        }
    }
    
    /**
     * Проверяет наличие несохраненных изменений перед переключением на другую заявку.
     * Если изменения есть - показывает подтверждение, иначе - сразу открывает заявку.
     */
    private void handleViewSubmissionWithCheck(Long chatId, Long submissionId) {
        ModerationState currentState = moderationStateService.getState(chatId);
        
        // Если есть текущее состояние и есть несохраненные изменения
        if (currentState != null && currentState.hasChanges()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("⚠️ У вас есть несохраненные изменения в текущей заявке.\n\n" +
                    "Закрыть текущую заявку без сохранения?");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да, закрыть", "switch_confirm_yes_" + submissionId);
            RowBuilder.addRow(row, "❌ Нет", "switch_confirm_no");

            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            try {
                Message sentMessage = executeAndTrack(message);
                currentState.setConfirmationMessageId(sentMessage.getMessageId());
            } catch (TelegramApiException e) {
                logger.severe("Ошибка при отправке подтверждения: " + e.getMessage());
            }
        } else {
            // Нет изменений - сразу открываем новую заявку
            showSubmissionDetails(chatId, submissionId);
        }
    }
    
    private void showSubmissionDetails(Long chatId, Long submissionId) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка не найдена");
                return;
            }

            SubmissionBuffer submission = submissionOpt.get();

            // Создаем временную копию для редактирования
            moderationStateService.initState(chatId, submission);

            sendSubmissionForm(chatId, submissionId);

        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }
    
    private void sendSubmissionForm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние модерации не найдено");
                return;
            }

            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            if (submission == null) {
                logger.severe("submission is null для chatId=" + chatId);
                sendMessage(chatId, "❌ Ошибка: заявка не загружена");
                return;
            }

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
            
            // Отправляем новую форму с фото (если есть)
            if (submission.getPhotoPath() != null && !submission.getPhotoPath().isEmpty()) {
                try {
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
                    
                    // Устанавливаем форму модерации на уровень 2 стека
                    navigationStackService.setLevel(chatId, 2, sentMessage.getMessageId());
                    return;
                } catch (Exception e) {
                    logger.warning("Не удалось загрузить фото, отправляем текстовую форму: " + e.getMessage());
                }
            }
            
            // Если фото нет или не удалось загрузить - отправляем текстовое сообщение
            SendMessage textMessage = new SendMessage();
            textMessage.setChatId(chatId.toString());
            textMessage.setText(caption.toString());
            textMessage.setReplyMarkup(markup);
            
            Message sentMessage = execute(textMessage);
            state.setFormMessageId(sentMessage.getMessageId());
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sentMessage.getMessageId());
            
            // Устанавливаем форму модерации на уровень 2 стека
            navigationStackService.setLevel(chatId, 2, sentMessage.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отправке формы");
        }
    }
    
    private InlineKeyboardMarkup buildModFormKeyboard(ModerationState state, Long submissionId, SubmissionBuffer submission) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        // Блок 1: Кнопки полей (показывают текущие значения)
        String brandText = state.getBrand() != null ?
            "🏷️ " + (state.getBrand().length() > 25 ? state.getBrand().substring(0, 22) + "..." : state.getBrand()) : 
            "🏷️ Бренд";
        keyboard.add(RowBuilder.getRow(brandText, "mod_edit_brand_" + submissionId));
        
        String nameText = state.getName() != null ?
            "📝 " + (state.getName().length() > 25 ? state.getName().substring(0, 22) + "..." : state.getName()) : 
            "📝 Название";
        keyboard.add(RowBuilder.getRow(nameText, "mod_edit_name_" + submissionId));
        
        String indexText = state.getIndexCode() != null ?
            "🔢 " + state.getIndexCode() : 
            "🔢 Индекс";
        keyboard.add(RowBuilder.getRow(indexText, "mod_edit_index_" + submissionId));
        
        // Блок 2: Альтернативы
        
        // Получаем список альтернатив из базы данных для проверки наличия сертификата
        List<Knife> dbAlternatives = new ArrayList<>();
        if (state.getOriginal() instanceof Knife knife) {
            dbAlternatives = knifeService.getAllAlternatives(knife.getId());
        }
        
        // Разделитель альтернатив
        if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
            keyboard.add(RowBuilder.getRow("──── Альтернативы ────", "alt_separator"));
            
            // Список альтернатив с кнопками удаления и идентификаторами
            for (int i = 0; i < state.getAlternativeModels().size(); i++) {
                String altStr = state.getAlternativeModels().get(i);

                // Проверяем, есть ли эта альтернатива в базе с сертификатом
                boolean hasCertificate = false;
                for (Knife alt : dbAlternatives) {
                    String altDisplayName = alt.getBrand().getName() + " / " + alt.getModel().getName();
                    if (altStr.equals(altDisplayName)) {
                        hasCertificate = true;
                        break;
                    }
                }
                
                String altText = altStr.length() > 35 ? altStr.substring(0, 32) + "..." : altStr;
                String prefix = hasCertificate ? "✅ " : "❌ ";
                keyboard.add(RowBuilder.getRow(prefix + altText, "mod_remove_alt_" + i + "_" + submissionId));
            }
        }
        
        // Кнопка добавления альтернативы
        keyboard.add(RowBuilder.getRow("➕ Добавить альтернативу", "mod_add_alt_" + submissionId));
        
        // Пустая строка-разделитель
        keyboard.add(new ArrayList<>());
        
        // Блок 3: Действия модератора
        
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        RowBuilder.addRow(actionRow, "✅ Одобрить", "mod_approve_" + submissionId);
        RowBuilder.addRow(actionRow, "❌ Отклонить", "mod_reject_" + submissionId);
        keyboard.add(actionRow);
        
        if (state.hasChanges())
            keyboard.add(RowBuilder.getRow("💾 Сохранить и выйти", "mod_save_exit_" + submissionId));
        
        // Кнопка отмены
        keyboard.add(RowBuilder.getRow("🔙 Назад к списку", "mod_cancel_" + submissionId));
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private void showApprovedSubmissionDetails(Long chatId, Long submissionId) {
        try {
            // Проверяем, есть ли уже открытая форма
            ModerationState existingState = moderationStateService.getState(chatId);
            if (existingState != null && existingState.getFormMessageId() != null)
                deleteMessage(chatId, existingState.getFormMessageId());
            
            Optional<Knife> knifeOpt = knifeService.getKnifeById(submissionId);
            
            if (knifeOpt.isEmpty()) {
                sendMessage(chatId, "❌ Сертификат #" + submissionId + " не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            // Создаем временную копию для редактирования с флагом isApprovedView
            moderationStateService.initState(chatId, knife);
            
            sendApprovedSubmissionForm(chatId, submissionId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре сертификата");
        }
    }
    
    private void sendApprovedSubmissionForm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Knife knife = (Knife) state.getOriginal();
            
            // Формируем текст с информацией о сертификате
            StringBuilder caption = new StringBuilder();
            caption.append("Сертификат #").append(knife.getId()).append("\n");

            // Отправляем фото с подписью (если есть)
            if (knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty()) {
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
                    textMessage.setText(caption + "\n\n⚠️ Не удалось загрузить фото");
                    textMessage.setReplyMarkup(buildApprovedFormKeyboard(submissionId, state));
                    
                    Message sentMessage = execute(textMessage);
                    state.setFormMessageId(sentMessage.getMessageId());
                    ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                    messages.setLastWindowMessageId(sentMessage.getMessageId());
                }
            } else {
                // Если фото нет, отправляем только текст
                SendMessage textMessage = new SendMessage();
                textMessage.setChatId(chatId.toString());
                textMessage.setText(caption + "\n\n⚠️ Фото отсутствует");
                textMessage.setReplyMarkup(buildApprovedFormKeyboard(submissionId, state));
                
                Message sentMessage = execute(textMessage);
                state.setFormMessageId(sentMessage.getMessageId());
                ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
                messages.setLastWindowMessageId(sentMessage.getMessageId());
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
        
        // Кнопка фото (Req 11.1, 11.2)
        List<InlineKeyboardButton> photoRow = new ArrayList<>();
        if (knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty()) {
            String photoPathText = knife.getPhotoPath();
            if (photoPathText.length() > 40) {
                photoPathText = "..." + photoPathText.substring(photoPathText.length() - 37);
            }
            RowBuilder.addRow(photoRow, "📷 " + photoPathText, "approved_photo_" + submissionId);
        } else
            RowBuilder.addRow(photoRow, "📷 Установить фото сертификата", "approved_photo_" + submissionId);
        keyboard.add(photoRow);
        
        // Кнопка названия
        String nameText = "🔪 Название: " + (state.getName() != null ? state.getName() : "не указано");
        keyboard.add(RowBuilder.getRow(nameText, "mod_edit_name_" + submissionId));
        
        // Кнопка бренда
        String brandText = "🏷️ Бренд: " + (state.getBrand() != null ? state.getBrand() : "не указан");
        keyboard.add(RowBuilder.getRow(brandText, "mod_edit_brand_" + submissionId));
        
        // Кнопка индекса
        String indexText = "🔢 Индекс: " + (state.getIndexCode() != null ? state.getIndexCode() : "не указан");
        keyboard.add(RowBuilder.getRow(indexText, "mod_edit_index_" + submissionId));
        
        // Кнопка альтернативных моделей
        List<String> altModels = state.getAlternativeModels();
        String altText = "🔄 Альтернативные: " + (altModels != null && !altModels.isEmpty() ? altModels.size() + " шт." : "нет");
        keyboard.add(RowBuilder.getRow(altText, "mod_edit_alt_" + submissionId));
        
        // Кнопка сохранения — только если есть изменения
        if (state.hasChanges())
            keyboard.add(RowBuilder.getRow("💾 Сохранить изменения", "approved_save_" + submissionId));
        
        keyboard.add(RowBuilder.getRow("🗑️ Удалить сертификат", "approved_delete_" + submissionId));
        keyboard.add(RowBuilder.getRow("❌ Закрыть", "approved_cancel_" + submissionId));
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Показывает форму добавления сертификата после загрузки фото.
     * Запрашивает у пользователя: бренд, название, индекс, альтернативы.
     */
    private void sendUploadForm(Long chatId, ModerationState state) {
        try {
            // Удаляем предыдущее сообщение-запрос, если оно существует
            if (state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());

            // Формируем caption для фото
            StringBuilder caption = new StringBuilder();
            caption.append("📝 Форма добавления сертификата\n\n");
            caption.append("Путь: ").append(state.getPhotoPath()).append("\n\n");
            String sep = settingsService.getAlternativeSeparator();
            caption.append("Отправьте данные в формате:\n");
            caption.append("`бренд").append(sep).append("название").append(sep).append("индекс` - для полного описания\n");
            caption.append("`бренд").append(sep).append("название` - без индекса\n");
            caption.append("`название` - только название модели\n\n");
            caption.append("Или используйте кнопки для редактирования.");

            InlineKeyboardMarkup markup = buildUploadFormKeyboard(state);

            // Если форма уже существует - редактируем caption
            if (state.getFormMessageId() != null) {
                try {
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption editCaption =
                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption();
                    editCaption.setChatId(chatId.toString());
                    editCaption.setMessageId(state.getFormMessageId());
                    editCaption.setCaption(caption.toString());
                    editCaption.setParseMode("Markdown");
                    editCaption.setReplyMarkup(markup);
                    execute(editCaption);
                    return;
                } catch (Exception e) {
                    logger.warning("Не удалось отредактировать форму, отправляем новую: " + e.getMessage());
                }
            }

            // Отправляем фото с caption
            if (state.getPhotoPath() != null && !state.getPhotoPath().isEmpty()) {
                try {
                    String photoUrl = yandexDiskService.getDownloadUrl(state.getPhotoPath());

                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId.toString());
                    sendPhoto.setPhoto(new InputFile(photoUrl));
                    sendPhoto.setCaption(caption.toString());
                    sendPhoto.setParseMode("Markdown");
                    sendPhoto.setReplyMarkup(markup);

                    Message sentMessage = execute(sendPhoto);
                    state.setFormMessageId(sentMessage.getMessageId());
                    state.setPromptMessageId(null);
                    moderationStateService.setState(chatId, state);
                    return;
                } catch (Exception e) {
                    logger.warning("Не удалось загрузить фото, отправляем текстовую форму: " + e.getMessage());
                }
            }

            // Фоллбэк: текстовое сообщение если нет фото
            SendMessage textMessage = new SendMessage();
            textMessage.setChatId(chatId.toString());
            textMessage.setText(caption.toString());
            textMessage.setParseMode("Markdown");
            textMessage.setReplyMarkup(markup);

            Message sentMessage = execute(textMessage);
            state.setFormMessageId(sentMessage.getMessageId());
            state.setPromptMessageId(null);
            moderationStateService.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы добавления: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при создании формы добавления");
        }
    }

    private InlineKeyboardMarkup buildUploadFormKeyboard(ModerationState state) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопки для редактирования
        keyboard.add(RowBuilder.getRow("🏷️ Бренд: "
                + (state.getBrand() != null ? state.getBrand() : "не указан"), "upload_edit_brand"));
        keyboard.add(RowBuilder.getRow("📝 Название: "
                + (state.getName() != null ? state.getName() : "не указано"), "upload_edit_name"));
        keyboard.add(RowBuilder.getRow("🔢 Индекс: "
                + (state.getIndexCode() != null ? state.getIndexCode() : "не указан"), "upload_edit_index"));
        keyboard.add(RowBuilder.getRow("🔄 Альтернативы: "
                + (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()
                ? state.getAlternativeModels().size() + " шт." : "нет"), "upload_edit_alt"));

        // Кнопки действий
        List<InlineKeyboardButton> actionRow = new ArrayList<>();
        RowBuilder.addRow(actionRow, "💾 Сохранить", "upload_save");
        RowBuilder.addRow(actionRow, "✅ Добавить", "upload_add");
        keyboard.add(actionRow);
        keyboard.add(RowBuilder.getRow("❌ Закрыть", "upload_cancel"));

        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private void handleModEditField(Long chatId, Long submissionId, String field) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        // НОВОЕ: Удаляем предыдущее сообщение-запрос, если оно существует
        if (state.getPromptMessageId() != null)
            deleteMessage(chatId, state.getPromptMessageId());

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
    
    private void handleModFieldInput(Long chatId, String text, Integer userMessageId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null || state.getEditingField() == null) {
            return;
        }
        
        // Удаляем сообщение пользователя
        deleteMessage(chatId, userMessageId);
        
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
                List<AlternativeEntry> parsedAlternatives = alternativesParser.parse(text);
                for (AlternativeEntry entry : parsedAlternatives) {
                    String altStr = entry.brand() != null 
                        ? entry.brand() + settingsService.getAlternativeSeparator() + entry.name()
                        : entry.name();
                    state.getAlternativeModels().add(altStr);
                }
                break;
        }
        
        state.setEditingField(null);
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        // Обновляем форму
        Long submissionId = ((SubmissionBuffer) state.getOriginal()).getId();
        sendSubmissionForm(chatId, submissionId);
    }
    
    private void handleModAddAlternative(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            return;
        }
        
        // Удаляем предыдущее сообщение-запрос, если оно есть
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        state.setEditingField("alt");
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            String sep = settingsService.getAlternativeSeparator();
            message.setText("➕ Введите альтернативу в формате:\nБренд " + sep + " Название\n\n" +
                "Можно ввести несколько через запятую:\nБренд1 " + sep + " Название1, Бренд2 " + sep + " Название2");
            
            // Добавляем кнопку "Отмена"
            message.setReplyMarkup(new InlineKeyboardMarkup(
                    List.of(RowBuilder.getRow("❌ Отмена", "mod_cancel_input_" + submissionId))));
            
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.addOtherMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Error sending alternative prompt: " + e.getMessage());
        }
    }
    
    private void handleModRemoveAlternative(Long chatId, Long submissionId, int index) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null || state.getAlternativeModels() == null) {
            return;
        }
        
        if (index >= 0 && index < state.getAlternativeModels().size()) {
            state.getAlternativeModels().remove(index);
            sendSubmissionForm(chatId, submissionId);
        }
    }
    
    private void handleModCancelInput(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            return;
        }
        
        // Удаляем сообщение-запрос
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        state.setEditingField(null);
    }
    
    private void handleModApprove(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        // Валидация перед одобрением: нужно хотя бы одно из двух — имя ИЛИ индекс
        boolean hasName = state.getName() != null && !state.getName().trim().isEmpty();
        boolean hasIndex = state.getIndexCode() != null && !state.getIndexCode().trim().isEmpty();

        if (!hasName && !hasIndex) {
            StringBuilder message = new StringBuilder();
            message.append("⚠️ Невозможно одобрить заявку:\n\n");
            message.append("❌ Не заполнено название модели или индекс\n");
            message.append("\n📝 Заполните хотя бы одно из полей.");
            sendMessage(chatId, message.toString());
            return;
        }
        
        try {
            // Применяем изменения к заявке
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();

            // Обновляем поля заявки
            submission.setModelName(hasName ? state.getName().trim() : null);
            submission.setBrandName(state.getBrand() != null && !state.getBrand().trim().isEmpty()
                ? state.getBrand().trim()
                : null);
            submission.setIndex(hasIndex ? state.getIndexCode().trim() : null);

            // Обновляем альтернативы (теперь как TEXT поле)
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                StringBuilder altStr = new StringBuilder();
                for (String alt : state.getAlternativeModels()) {
                    if (altStr.length() > 0) altStr.append(", ");
                    altStr.append(alt);
                }
                submission.setAlternatives(altStr.toString());
            } else {
                submission.setAlternatives(null);
            }

            // Проверяем наличие дубликата (нож с совпадающими brand+name+index)
            Optional<Knife> duplicateKnife = submissionBufferService.findDuplicateKnife(
                submission.getBrandName(),
                submission.getModelName(),
                submission.getIndex()
            );
            
            if (duplicateKnife.isPresent()) {
                // Найден дубликат - предлагаем обновить фото существующего ножа
                Knife existingKnife = duplicateKnife.get();
                state.setDuplicateKnifeId(existingKnife.getId());
                
                String duplicateMessage = "⚠️ Найден существующий нож с такими же параметрами:\n\n" +
                        "🔪 " + existingKnife.getDisplayName() + "\n\n" +
                        "Хотите обновить фото существующего ножа вместо создания дубликата?";
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                RowBuilder.addRow(row, "✅ Обновить фото", "mod_replace_photo_yes_" + submissionId);
                RowBuilder.addRow(row, "❌ Создать новый", "mod_replace_photo_no_" + submissionId);

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(duplicateMessage);
                msg.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
                
                Message sentMsg = executeAndTrack(msg);
                state.setDuplicateMessageId(sentMsg.getMessageId());
                
                return;
            }
            
            // Нет дубликата - проверяем наличие pending-альтернатив
            List<String> pendingAlts = state.getAlternativeModels();
            if (pendingAlts != null && !pendingAlts.isEmpty()) {
                // Показываем шаг подтверждения альтернатив (Req 8.1)
                showAltConfirmation(chatId, submissionId, pendingAlts, state);
            } else {
                // Нет альтернатив - пропускаем шаг подтверждения (Req 8.2)
                approveSubmissionInternal(chatId, submissionId, submission, state);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            logError("Одобрение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    /**
     * Показывает сообщение-подтверждение со списком pending-альтернатив.
     */
    private void showAltConfirmation(Long chatId, Long submissionId, List<String> pendingAlts, ModerationState state) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("🔄 Найдены новые альтернативы:\n");
            for (String alt : pendingAlts)
                text.append("• ").append(alt).append("\n");
            text.append("\nЗавершить добавление?");
            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да, завершить", "alt_confirm_yes_" + submissionId);
            RowBuilder.addRow(row, "❌ Отмена", "alt_confirm_no_" + submissionId);
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(text.toString());
            msg.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
            Message sentMsg = execute(msg);
            state.setConfirmationMessageId(sentMsg.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при показе подтверждения альтернатив: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Обработчик подтверждения альтернатив — [✅ Да, завершить].
     * Требование 8.3: создать связи в alternatives и продолжить одобрение.
     */
    private void handleAltConfirmYes(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        // Удаляем сообщение-подтверждение (Req 14.8)
        if (state.getConfirmationMessageId() != null) {
            deleteMessage(chatId, state.getConfirmationMessageId());
            state.setConfirmationMessageId(null);
        }
        
        SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
        approveSubmissionInternal(chatId, submissionId, submission, state);
    }
    
    /**
     * Обработчик отмены одобрения — [❌ Отмена].
     */
    private void handleAltConfirmNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        // Удаляем сообщение-подтверждение (Req 14.8)
        if (state.getConfirmationMessageId() != null) {
            deleteMessage(chatId, state.getConfirmationMessageId());
            state.setConfirmationMessageId(null);
        }
        
        // Возвращаемся к форме модерации без изменений в БД (Req 8.4)
        sendSubmissionForm(chatId, submissionId);
    }
    
    private void handleDuplicatePhotoYes(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            Long duplicateKnifeId = state.getDuplicateKnifeId();
            
            if (duplicateKnifeId == null) {
                sendMessage(chatId, "❌ ID дубликата не найден");
                return;
            }
            
            // Удаляем сообщение с предложением
            if (state.getDuplicateMessageId() != null)
                deleteMessage(chatId, state.getDuplicateMessageId());
            
            // Заменяем фото существующего ножа
            submissionBufferService.replaceKnifePhoto(duplicateKnifeId, submission.getPhotoPath());
            
            // Удаляем форму
            if (state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());
            
            // Удаляем заявку из буфера
            submissionBufferService.rejectSubmission(submissionId);
            
            // Очищаем состояние
            moderationStateService.removeState(chatId);
            
            // Отправляем уведомление
            sendMessage(chatId, "✅ Фото ножа #" + duplicateKnifeId + " успешно обновлено!");
            
            // Возвращаемся к списку
            updatePendingListIfNeeded(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при замене фото: " + e.getMessage());
            logError("Замена фото ножа", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при замене фото: " + e.getMessage());
        }
    }
    
    private void handleDuplicatePhotoNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            // Удаляем сообщение с предложением
            if (state.getDuplicateMessageId() != null)
                deleteMessage(chatId, state.getDuplicateMessageId());
            // Продолжаем одобрение — проверяем наличие pending-альтернатив
            List<String> pendingAlts = state.getAlternativeModels();
            if (pendingAlts != null && !pendingAlts.isEmpty())
                showAltConfirmation(chatId, submissionId, pendingAlts, state);
            else
                approveSubmissionInternal(chatId, submissionId, submission, state);
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            logError("Одобрение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }
    
    private void approveSubmissionInternal(Long chatId, Long submissionId, 
                                          SubmissionBuffer submission, ModerationState state) {
        try {
            // Одобряем заявку — создаём нож и его связи альтернатив
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
            // Удаляем форму
            if (state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());
            
            // Удаляем сообщение-запрос, если оно есть
            if (state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());

            // Ищем транзитивные альтернативы
            Set<Long> newAlternativeIds = knife.getAlternatives().stream()
                .map(Knife::getId)
                .collect(Collectors.toSet());
            
            if (!newAlternativeIds.isEmpty()) {
                Set<Knife> transitiveAlternatives = transitiveAlternativesService.findTransitive(
                    knife.getId(), newAlternativeIds);
                
                if (!transitiveAlternatives.isEmpty()) {
                    // Сохраняем данные для шага транзитивных альтернатив
                    state.setApprovedKnifeId(knife.getId());
                    state.setTransitiveAlternativeIds(transitiveAlternatives.stream()
                        .map(Knife::getId)
                        .collect(Collectors.toSet()));
                    
                    // Показываем предложение транзитивных альтернатив
                    showTransitiveAlternativesProposalForPending(chatId, knife.getId(), transitiveAlternatives, state);
                    return;
                }
            }
            
            // Нет транзитивных альтернатив — завершаем
            moderationStateService.removeState(chatId);
            updatePendingListIfNeeded(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            logError("Одобрение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }
    
    /**
     * Показывает предложение добавить транзитивные альтернативы после одобрения заявки.
     */
    private void showTransitiveAlternativesProposalForPending(Long chatId, Long knifeId,
                                                               Set<Knife> transitiveAlternatives,
                                                               ModerationState state) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("🔄 Найдены транзитивные альтернативы:\n\n");
            
            int count = 0;
            for (Knife knife : transitiveAlternatives) {
                if (count >= 10) {
                    message.append("... и ещё ").append(transitiveAlternatives.size() - 10).append(" альтернатив");
                    break;
                }
                message.append("• ").append(knife.getDisplayName()).append("\n");
                count++;
            }
            message.append("\n❓ Добавить прямые связи с этими альтернативами?");
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message.toString());
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да", "transitive_pending_yes_" + knifeId);
            RowBuilder.addRow(row, "❌ Нет", "transitive_pending_no_" + knifeId);
            sendMessage.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
            
            Message sent = execute(sendMessage);
            state.setTransitiveMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error showing transitive alternatives proposal for pending: " + e.getMessage());
            // Если не удалось показать предложение — просто завершаем
            moderationStateService.removeState(chatId);
            updatePendingListIfNeeded(chatId);
        }
    }
    
    /**
     * Обработчик [✅ Да] для транзитивных альтернатив после одобрения заявки.
     */
    private void handleTransitivePendingYes(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Set<Long> transitiveIds = state.getTransitiveAlternativeIds();
            if (transitiveIds == null || transitiveIds.isEmpty()) {
                sendMessage(chatId, "❌ Транзитивные альтернативы не найдены");
                moderationStateService.removeState(chatId);
                updatePendingListIfNeeded(chatId);
                return;
            }
            
            // Получаем нож и добавляем транзитивные альтернативы
            Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
            if (knifeOpt.isEmpty()) {
                sendMessage(chatId, "❌ Нож не найден");
                moderationStateService.removeState(chatId);
                updatePendingListIfNeeded(chatId);
                return;
            }
            
            Knife knife = knifeOpt.get();
            for (Long transitiveId : transitiveIds) {
                Optional<Knife> transitiveOpt = knifeRepository.findById(transitiveId);
                if (transitiveOpt.isPresent()) {
                    Knife transitiveKnife = transitiveOpt.get();
                    if (!knife.getAlternatives().contains(transitiveKnife)) {
                        knife.addAlternative(transitiveKnife);
                    }
                }
            }
            knifeRepository.save(knife);
            
            // Удаляем сообщение с предложением
            if (state.getTransitiveMessageId() != null) {
                deleteMessage(chatId, state.getTransitiveMessageId());
            }
            
            moderationStateService.removeState(chatId);
            sendMessage(chatId, "✅ Транзитивные альтернативы добавлены!");
            updatePendingListIfNeeded(chatId);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive pending yes: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при добавлении транзитивных альтернатив: " + e.getMessage());
        }
    }
    
    /**
     * Обработчик [❌ Нет] для транзитивных альтернатив после одобрения заявки.
     */
    private void handleTransitivePendingNo(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            
            // Удаляем сообщение с предложением (Req 14.9)
            if (state != null && state.getTransitiveMessageId() != null) {
                deleteMessage(chatId, state.getTransitiveMessageId());
            }
            
            moderationStateService.removeState(chatId);
            updatePendingListIfNeeded(chatId);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive pending no: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }
    
    private void handleModRejectRequest(Long chatId, Long submissionId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("⚠️ Вы уверены, что хотите отклонить заявку #" + submissionId + "?");
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да, отклонить", "confirm_reject_" + submissionId);
            RowBuilder.addRow(row, "❌ Отмена", "cancel_reject_" + submissionId);
            message.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
            
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
            for (Integer msgId : messages.getOtherMessageIds())
                deleteMessage(chatId, msgId);
            messages.clearOtherMessages();
        }
    }
    
    private void handleModReject(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            submissionBufferService.rejectSubmission(submissionId);
            
            // Удаляем форму
            if (state != null && state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());
            
            // Удаляем сообщение-запрос, если оно есть
            if (state != null && state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());

            // Удаляем сообщение подтверждения отклонения, если оно есть
            ChatMessages messages = chatMessages.get(chatId);
            if (messages != null) {
                for (Integer msgId : messages.getOtherMessageIds())
                    deleteMessage(chatId, msgId);
                messages.clearOtherMessages();
            }
            // Очищаем состояние
            moderationStateService.removeState(chatId);
            
            // Возвращаемся к списку
            updatePendingListIfNeeded(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            logError("Отклонение заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
        }
    }
    
    private void handleModCancel(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
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

                List<InlineKeyboardButton> row = new ArrayList<>();
                RowBuilder.addRow(row, "✅ Да, отменить", "mod_cancel_confirm_" + submissionId);
                RowBuilder.addRow(row, "❌ Нет, продолжить", "mod_cancel_no_" + submissionId);
                message.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
                
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
        ModerationState state = moderationStateService.getState(chatId);
        
        // Удаляем форму заявки
        if (state != null && state.getFormMessageId() != null)
            deleteMessage(chatId, state.getFormMessageId());
        
        // Удаляем сообщение-запрос, если оно есть
        if (state != null && state.getPromptMessageId() != null)
            deleteMessage(chatId, state.getPromptMessageId());
        
        // Удаляем сообщение подтверждения
        ChatMessages messages = chatMessages.get(chatId);
        if (messages != null) {
            for (Integer msgId : messages.getOtherMessageIds())
                deleteMessage(chatId, msgId);
            messages.clearOtherMessages();
        }
        
        moderationStateService.removeState(chatId);
        
        updatePendingListIfNeeded(chatId);
    }
    
    private void handleModCancelNo(Long chatId, Long submissionId) {
        // Удаляем только сообщение подтверждения
        ChatMessages messages = chatMessages.get(chatId);
        if (messages != null) {
            for (Integer msgId : messages.getOtherMessageIds())
                deleteMessage(chatId, msgId);
            messages.clearOtherMessages();
        }
    }
    
    /**
     * Обработчик подтверждения переключения на другую заявку (Да, закрыть текущую).
     */
    private void handleSwitchConfirmYes(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        
        if (state != null) {
            // Удаляем сообщение-подтверждение
            if (state.getConfirmationMessageId() != null)
                deleteMessage(chatId, state.getConfirmationMessageId());
            if (state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());
            if (state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());
            moderationStateService.removeState(chatId);
        }
        showSubmissionDetails(chatId, submissionId);
    }
    
    /**
     * Обработчик отмены переключения на другую заявку (Нет, остаться).
     */
    private void handleSwitchConfirmNo(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state != null) {
            if (state.getConfirmationMessageId() != null) {
                deleteMessage(chatId, state.getConfirmationMessageId());
                state.setConfirmationMessageId(null);
            }
        }
    }
    
    /**
     * Сохраняет изменения в заявке и возвращается к списку ожидающих заявок.
     * Реализация требования из WORKFLOW.md: кнопка "Сохранить и выйти" появляется
     * только если администратор внес изменения в форму модерации.
     */
    private void handleModSaveAndExit(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        try {
            // Получаем оригинальную заявку
            Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            
            // Сохраняем изменения в базу данных
            submission.setModelName(state.getName());
            submission.setBrandName(state.getBrand());
            submission.setIndex(state.getIndexCode());
            
            // Сохраняем альтернативы в формате "Бренд / Название, Бренд / Название"
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                String alternativesStr = String.join(", ", state.getAlternativeModels());
                submission.setAlternatives(alternativesStr);
            } else {
                submission.setAlternatives(null);
            }
            
            submissionBufferRepository.save(submission);
            
            // Удаляем форму заявки
            if (state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());
            
            // Удаляем сообщение-запрос, если оно есть
            if (state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());
            
            // Удаляем другие сообщения
            ChatMessages messages = chatMessages.get(chatId);
            if (messages != null) {
                for (Integer msgId : messages.getOtherMessageIds())
                    deleteMessage(chatId, msgId);
                messages.clearOtherMessages();
            }
            
            // Очищаем состояние модерации
            moderationStateService.removeState(chatId);
            
            // Обновляем список ожидающих заявок
            updatePendingListIfNeeded(chatId);
            
            logger.info("Изменения в заявке #" + submissionId + " сохранены");
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении изменений: " + e.getMessage());
            logError("Сохранение изменений заявки #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при сохранении изменений: " + e.getMessage());
        }
    }
    
    // ─── Обработчики формы прямой загрузки (Req 17.1–17.5) ──────────────────
    
    /**
     * Начинает редактирование поля в форме прямой загрузки.
     */
    private void handleUploadEditField(Long chatId, String field) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        state.setEditingField(field);
        
        // Удаляем предыдущее сообщение-запрос, если оно существует
        if (state.getPromptMessageId() != null)
            deleteMessage(chatId, state.getPromptMessageId());
        
        // Запрашиваем ввод
        String promptText;
        if ("brand".equals(field)) {
            promptText = "✏️ Отправьте название бренда:";
        } else if ("name".equals(field)) {
            promptText = "✏️ Отправьте название модели:";
        } else if ("index".equals(field)) {
            promptText = "✏️ Отправьте индекс (или отправьте пустое сообщение):";
        } else {
            promptText = "✏️ Отправьте данные:";
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(promptText);
        
        try {
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateService.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе ввода: " + e.getMessage());
        }
    }
    
    /**
     * Начинает редактирование альтернатив в форме прямой загрузки.
     */
    private void handleUploadEditAlt(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        state.setEditingField("alt");
        
        // Удаляем предыдущее сообщение-запрос, если оно существует
        if (state.getPromptMessageId() != null)
            deleteMessage(chatId, state.getPromptMessageId());

        // Запрашиваем ввод альтернатив
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        String sep = settingsService.getAlternativeSeparator();
        message.setText("✏️ Отправьте альтернативные модели через запятую:\n" +
                "Формат: `название` или `бренд" + sep + "название`\n" +
                "Пример: `Модель1, Бренд2" + sep + "Модель2`");
        message.setParseMode("Markdown");
        
        try {
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateService.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе альтернатив: " + e.getMessage());
        }
    }
    
    /**
     * Обрабатывает ввод в форме прямой загрузки.
     * Использует handleModFieldInput для обновления состояния.
     */
    private void handleUploadFieldInput(Long chatId, String text, Integer userMessageId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null || state.getEditingField() == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Используем handleModFieldInput для обновления состояния
        handleModFieldInput(chatId, text, userMessageId);
        
        // Обновляем форму
        sendUploadForm(chatId, state);
    }
    
    /**
     * Сохраняет данные в submissions_buffer (Req 15.5).
     */
    private void handleUploadSave(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        try {
            // Создаем запись в submissions_buffer
            SubmissionBuffer submission = new SubmissionBuffer(
                0L, // user_id будет установлен при обработке
                "admin",
                state.getName(),
                state.getBrand(),
                state.getPhotoPath()
            );
            submission.setIndex(state.getIndexCode());
            
            // Формируем строку альтернатив
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                String sep = settingsService.getAlternativeSeparator();
                List<AlternativeEntry> alternatives = state.getAlternativeModels().stream()
                    .map(alt -> {
                        String[] parts = alt.split(" " + java.util.regex.Pattern.quote(sep) + " ");
                        if (parts.length == 2) {
                            return new AlternativeEntry(parts[1].trim(), parts[0].trim());
                        } else {
                            return new AlternativeEntry(parts[0].trim(), null);
                        }
                    })
                    .toList();
                String altStr = alternatives.stream()
                    .map(alt -> alt.brand() != null ? alt.brand() + sep + alt.name() : alt.name())
                    .collect(java.util.stream.Collectors.joining(", "));
                submission.setAlternatives(altStr);
            }
            
            submission = submissionBufferRepository.save(submission);
            
            sendMessage(chatId, "✅ Данные сохранены в буфере!\n" +
                    "ID заявки: #" + submission.getId() + "\n" +
                    "Ожидает одобрения модератором.");
            
            // Очищаем состояние
            moderationStateService.removeState(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при сохранении: " + e.getMessage());
        }
    }
    
    /**
     * Добавляет сертификат напрямую в knives (Req 15.6, 15.7).
     */
    private void handleUploadAdd(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        // Валидация: нужно хотя бы одно из двух — имя ИЛИ индекс
        boolean hasName = state.getName() != null && !state.getName().trim().isEmpty();
        boolean hasIndex = state.getIndexCode() != null && !state.getIndexCode().trim().isEmpty();

        if (!hasName && !hasIndex) {
            sendMessage(chatId, "⚠️ Заполните хотя бы одно из полей: название модели или индекс.");
            return;
        }

        try {
            // Создаем или находим бренд и модель
            Brand brand = state.getBrand() != null && !state.getBrand().trim().isEmpty()
                ? brandRepository.findByName(state.getBrand())
                    .orElseGet(() -> brandRepository.save(new Brand(state.getBrand())))
                : null;
            KnifeModel model = hasName
                ? knifeModelRepository.findByName(state.getName())
                    .orElseGet(() -> knifeModelRepository.save(new KnifeModel(state.getName())))
                : null;
            
            // Создаем запись в knives
            Knife knife = new Knife(model, brand, state.getIndexCode(), state.getPhotoPath());
            knife = knifeRepository.save(knife);
            
            // Обрабатываем альтернативы
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                String sep = settingsService.getAlternativeSeparator();
                List<AlternativeEntry> alternatives = state.getAlternativeModels().stream()
                    .map(alt -> {
                        String[] parts = alt.split(" " + java.util.regex.Pattern.quote(sep) + " ");
                        if (parts.length == 2) {
                            return new AlternativeEntry(parts[1].trim(), parts[0].trim());
                        } else {
                            return new AlternativeEntry(parts[0].trim(), null);
                        }
                    })
                    .toList();
                
                for (AlternativeEntry altEntry : alternatives) {
                    Brand altBrand = brandRepository.findByName(altEntry.brand())
                        .orElseGet(() -> brandRepository.save(new Brand(altEntry.brand())));
                    KnifeModel altModel = knifeModelRepository.findByName(altEntry.name())
                        .orElseGet(() -> knifeModelRepository.save(new KnifeModel(altEntry.name())));
                    
                    Optional<Knife> existingKnife = knifeRepository.findByModelAndBrand(altModel, altBrand);
                    Knife alternativeKnife;
                    
                    if (existingKnife.isPresent()) {
                        alternativeKnife = existingKnife.get();
                    } else {
                        alternativeKnife = new Knife(altModel, altBrand, null, null);
                        alternativeKnife = knifeRepository.save(alternativeKnife);
                    }
                    
                    knife.addAlternative(alternativeKnife);
                }
            }
            
            knifeRepository.save(knife);
            
            sendMessage(chatId, "✅ Сертификат добавлен!\n" +
                    "ID ножа: #" + knife.getId() + "\n" +
                    "Путь к фото: " + state.getPhotoPath());
            
            // Обновляем меню если был создан новый бренд
            if (brandRepository.findByName(state.getBrand()).isEmpty()) {
                mainMenuUpdateService.updateAllUserMenus();
            }
            
            // Очищаем состояние
            moderationStateService.removeState(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при добавлении: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при добавлении: " + e.getMessage());
        }
    }
    
    /**
     * Запрашивает подтверждение закрытия формы (Req 15.8).
     */
    private void handleUploadCancelRequest(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Удаляем форму
        if (state.getFormMessageId() != null) {
            deleteMessage(chatId, state.getFormMessageId());
            state.setFormMessageId(null);
        }
        
        // Показываем подтверждение
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите закрыть форму? Все несохраненные данные будут потеряны.");
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        RowBuilder.addRow(row, "✅ Да, закрыть", "upload_cancel_confirm");
        RowBuilder.addRow(row, "❌ Нет, продолжить", "upload_cancel_no");
        message.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
        
        try {
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateService.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе подтвержден��я: " + e.getMessage());
        }
    }
    
    /**
     * Подтверждает закрытие формы.
     */
    private void handleUploadCancelConfirm(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Удаляем подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        // Очищаем состояние
        moderationStateService.removeState(chatId);
        
        sendMessage(chatId, "✅ Форма закрыта.");
    }
    
    /**
     * Отменяет закрытие формы.
     */
    private void handleUploadCancelNo(Long chatId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Удаляем подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        // Показываем форму снова
        sendUploadForm(chatId, state);
    }
    
    private void handleApprovedSave(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        // Спрашиваем подтверждение
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите сохранить изменения?");

        List<InlineKeyboardButton> row = new ArrayList<>();
        RowBuilder.addRow(row, "✅ Да, сохранить", "approved_save_confirm_" + submissionId);
        RowBuilder.addRow(row, "❌ Нет", "approved_save_no_" + submissionId);
        message.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
        
        try {
            Message sent = execute(message);
            state.setPromptMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error sending save confirmation: " + e.getMessage());
        }
    }
    
    private void handleApprovedSaveConfirm(Long chatId, Long submissionId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            // Удаляем сообщение-подтверждение
            if (state.getPromptMessageId() != null)
                deleteMessage(chatId, state.getPromptMessageId());
            
            // Удаляем форму
            if (state.getFormMessageId() != null)
                deleteMessage(chatId, state.getFormMessageId());

            // Получаем одобренный нож
            Knife knife = (Knife) state.getOriginal();
            Long knifeId = knife.getId();
            
            // Сохраняем текстовые поля
            if (state.getName() != null && !state.getName().isEmpty()) {
                knife.getModel().setName(state.getName());
            }
            if (state.getBrand() != null && !state.getBrand().isEmpty()) {
                knife.getBrand().setName(state.getBrand());
            }
            if (state.getIndexCode() != null && !state.getIndexCode().isEmpty()) {
                knife.setIndex(state.getIndexCode());
            }
            
            // Обрабатываем новые альтернативы
            Set<Long> newAlternativeIds = new HashSet<>();
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                for (String altStr : state.getAlternativeModels()) {
                    List<AlternativeEntry> entries = alternativesParser.parse(altStr);
                    for (AlternativeEntry entry : entries) {
                        Brand altBrand = findOrCreateBrand(entry.brand());
                        KnifeModel altModel = findOrCreateKnifeModel(entry.name());
                        Optional<Knife> existingKnife = knifeRepository.findByModelAndBrand(altModel, altBrand);
                        Knife altKnife;
                        if (existingKnife.isPresent())
                            altKnife = existingKnife.get();
                         else {
                            altKnife = new Knife(altModel, altBrand, null, null);
                            altKnife = knifeRepository.save(altKnife);
                        }
                        if (!knife.getAlternatives().contains(altKnife)) {
                            knife.addAlternative(altKnife);
                            newAlternativeIds.add(altKnife.getId());
                        }
                    }
                }
            }
            
            // Сохраняем изменения
            knifeRepository.save(knife);
            
            // Если были добавлены новые альтернативы, ищем транзитивные
            if (!newAlternativeIds.isEmpty()) {
                Set<Knife> transitiveAlternatives = transitiveAlternativesService.findTransitive(knifeId, newAlternativeIds);
                
                if (!transitiveAlternatives.isEmpty()) {
                    // Сохраняем транзитивные альтернативы в состояние
                    state.setTransitiveAlternativeIds(transitiveAlternatives.stream()
                        .map(Knife::getId)
                        .collect(Collectors.toSet()));
                    
                    // Показываем предложение транзитивных альтернатив
                    showTransitiveAlternativesProposal(chatId, knifeId, transitiveAlternatives);
                    return;
                }
            }
            
            // Если нет транзитивных альтернатив, просто завершаем
            sendMessage(chatId, "✅ Изменения сохранены!");
            moderationStateService.removeState(chatId);
            handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении изменений: " + e.getMessage());
            logError("Сохранение одобренного сертификата #" + submissionId, e.getMessage());
            sendMessage(chatId, "❌ Ошибка при сохранении изменений: " + e.getMessage());
        }
    }
    
    private void handleApprovedSaveNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            return;
        }
        
        // Просто удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
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
            
            moderationStateService.removeState(chatId);
            handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Ошибка при удалении сертификата: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при удалении сертификата");
        }
    }
    
    private void handleApprovedCancel(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            return;
        }
        
        // Проверяем были ли изменения
        if (state.hasChanges()) {
            // Есть изменения - спрашиваем подтверждение
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❓ Вы уверены, что хотите закрыть редактирование?\n\nИзменения не будут сохранены.");

            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да, закрыть", "approved_cancel_confirm_" + submissionId);
            RowBuilder.addRow(row, "❌ Нет", "approved_cancel_no_" + submissionId);
            message.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
            
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
        ModerationState state = moderationStateService.getState(chatId);
        
        // Удаляем сообщение-подтверждение если есть
        if (state != null && state.getPromptMessageId() != null)
            deleteMessage(chatId, state.getPromptMessageId());

        
        // Удаляем форму
        if (state != null && state.getFormMessageId() != null)
            deleteMessage(chatId, state.getFormMessageId());
        
        moderationStateService.removeState(chatId);
    }
    
    private void handleApprovedCancelNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateService.getState(chatId);
        if (state == null) {
            return;
        }
        
        // Просто удаляем сообщение-подтверждение
        if (state.getPromptMessageId() != null) {
            deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
    }
    
    private void handleApprovedSearchRequest(Long chatId) {
        searchStates.put(chatId, "approved");
        sendMessage(chatId, "🔍 Введите название или индекс для поиска:");
    }

    private void handleAdminSearchRequest(Long chatId) {
        searchStates.put(chatId, "admin_search");
        Message sent = sendMessage(chatId, "🔍 Введите название бренда для поиска:");
    }

    private void handleAdminSearchPage(Long chatId, int page, String type, String searchQuery) {
        try {
            List<?> results;
            if ("brand".equals(type)) {
                results = new ArrayList<>(brandRepository.searchByName(searchQuery));
            } else {
                results = new ArrayList<>(knifeModelRepository.searchByName(searchQuery));
            }
            
            showAdminSearchResults(chatId, results, type, page, searchQuery);
        } catch (Exception e) {
            logger.severe("Error handling search page: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }

    private void handleAdminSearchBrandSelect(Long chatId, String brandName) {
        try {
            List<KnifeModel> models = knifeModelRepository.findByBrand(brandName);
            if (models.isEmpty()) {
                sendMessage(chatId, "❌ Моделей не найдено");
            } else {
                showAdminBrandModels(chatId, brandName, new ArrayList<>(models), 0);
            }
        } catch (Exception e) {
            logger.severe("Error selecting brand: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при выборе бренда");
        }
    }

    private void showAdminBrandModels(Long chatId, String brandName, List<KnifeModel> models, int page) {
        try {
            int itemsPerPage = 20;
            int totalPages = (int) Math.ceil((double) models.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, models.size());
            List<KnifeModel> pageModels = models.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🏷️ " + brandName + "\n\nВсего: " + models.size() + " моделей");
            
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            for (KnifeModel model : pageModels) {
                String name = model.getName();
                if (name.length() > 40)
                    name = name.substring(0, 37) + "...";
                keyboard.add(RowBuilder.getRow(name, "admin_search_model_" + model.getId()));
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                RowBuilder.addRow(paginationRow, "⬅️", "admin_brand_models_page_" + prevPage + "_" + brandName);
                RowBuilder.addRow(paginationRow, String.format("%d/%d", page + 1, totalPages), "admin_brand_models_current_page");
                RowBuilder.addRow(paginationRow, "➡️", "admin_brand_models_page_" + nextPage + "_" + brandName);
                keyboard.add(paginationRow);
            }
            keyboard.add(RowBuilder.getRow("🔍 Поиск", "admin_search_models_" + brandName));
            keyboard.add(RowBuilder.getRow("🔙 Назад", "admin_search_back"));
            keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
            
            message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
            execute(message);
        } catch (Exception e) {
            logger.severe("Error showing brand models: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении моделей");
        }
    }

    private void handleAdminSearchModelsRequest(Long chatId, String brandName) {
        searchStates.put(chatId, "admin_search_models_" + brandName);
        sendMessage(chatId, "🔍 Введите название модели для поиска:");
    }

    private void handleAdminSearchModelsInput(Long chatId, String searchQuery, String brandName) {
        try {
            List<KnifeModel> allModels = knifeModelRepository.findByBrand(brandName);
            List<KnifeModel> filteredModels = allModels.stream()
                .filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .collect(Collectors.toList());
            
            if (filteredModels.isEmpty()) {
                sendMessage(chatId, "❌ Моделей не найдено");
            } else {
                showAdminSearchModelResults(chatId, brandName, filteredModels, 0, searchQuery);
            }
        } catch (Exception e) {
            logger.severe("Error handling search models input: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }

    private void showAdminSearchModelResults(Long chatId, String brandName, List<KnifeModel> models, int page, String searchQuery) {
        try {
            int itemsPerPage = 20;
            int totalPages = (int) Math.ceil((double) models.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, models.size());
            List<KnifeModel> pageModels = models.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔍 Результаты поиска (" + models.size() + " найдено)");
            
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (KnifeModel model : pageModels) {
                String name = model.getName();
                if (name.length() > 40)
                    name = name.substring(0, 37) + "...";
                keyboard.add(RowBuilder.getRow(name, "admin_search_model_" + model.getId()));
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                RowBuilder.addRow(paginationRow, "⬅️", "admin_search_models_page_" + prevPage + "_" + brandName + "_" + searchQuery);
                RowBuilder.addRow(paginationRow, String.format("%d/%d", page + 1, totalPages), "admin_search_models_current_page");
                RowBuilder.addRow(paginationRow, "➡️", "admin_search_models_page_" + nextPage + "_" + brandName + "_" + searchQuery);
                keyboard.add(paginationRow);
            }
            keyboard.add(RowBuilder.getRow("🔙 Назад", "admin_search_back"));
            keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));

            message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
            execute(message);
        } catch (Exception e) {
            logger.severe("Error showing search model results: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении результатов");
        }
    }

    private void handleAdminSearchModelSelect(Long chatId, Long modelId) {
        try {
            Optional<KnifeModel> modelOpt = knifeModelRepository.findById(modelId);
            if (modelOpt.isPresent()) {
                KnifeModel model = modelOpt.get();
                List<Knife> knives = knifeRepository.findByModelId(modelId);
                
                StringBuilder sb = new StringBuilder();
                sb.append("📋 Модель: ").append(model.getName()).append("\n\n");
                
                if (knives.isEmpty()) {
                    sb.append("❌ Нет сертификатов для этой модели");
                } else {
                    sb.append("Сертификаты:\n");
                    for (Knife knife : knives) {
                        sb.append("• ").append(knife.getBrand().getName()).append(" / ").append(model.getName());
                        if (knife.getIndex() != null && !knife.getIndex().isEmpty()) {
                            sb.append(" / ").append(knife.getIndex());
                        }
                        sb.append("\n");
                    }
                }
                
                sendMessage(chatId, sb.toString());
            } else {
                sendMessage(chatId, "❌ Модель не найдена");
            }
        } catch (Exception e) {
            logger.severe("Error selecting model: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при выборе модели");
        }
    }

    private void showAdminSearchResults(Long chatId, List<?> results, String type, int page, String searchQuery) {
        try {
            int itemsPerPage = 20;
            int totalPages = (int) Math.ceil((double) results.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, results.size());
            List<?> pageResults = results.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔍 Результаты поиска (" + results.size() + " найдено)");
            
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (Object result : pageResults) {
                String name;
                String callbackData;
                if (type.equals("brand")) {
                    Brand brand = (Brand) result;
                    name = brand.getName();
                    callbackData = "admin_search_brand_" + brand.getName();
                } else {
                    KnifeModel model = (KnifeModel) result;
                    name = model.getName();
                    callbackData = "admin_search_model_" + model.getId();
                }
                if (name.length() > 40)
                    name = name.substring(0, 37) + "...";

                keyboard.add(RowBuilder.getRow(name, callbackData));
            }
            
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                
                String pageCallbackPrefix = "admin_search_page_" + prevPage + "_" + type + "_" + searchQuery;
                RowBuilder.addRow(paginationRow, "⬅️", pageCallbackPrefix);
                RowBuilder.addRow(paginationRow, String.format("%d/%d", page + 1, totalPages), "admin_search_current_page");

                pageCallbackPrefix = "admin_search_page_" + nextPage + "_" + type + "_" + searchQuery;
                RowBuilder.addRow(paginationRow, "➡️", pageCallbackPrefix);
                keyboard.add(paginationRow);
            }
            keyboard.add(RowBuilder.getRow("🔙 Назад", "admin_search_back"));
            keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
            
            message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
            execute(message);
        } catch (Exception e) {
            logger.severe("Error showing search results: " + e.getMessage());
        }
    }
    
    private void handleAdminBrandModelsPage(Long chatId, int page, String brandName) {
        try {
            List<KnifeModel> models = knifeModelRepository.findByBrand(brandName);
            showAdminBrandModels(chatId, brandName, models, page);
        } catch (Exception e) {
            logger.severe("Error handling brand models page: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отображении моделей");
        }
    }

    private void handleAdminSearchModelsPage(Long chatId, int page, String brandName, String searchQuery) {
        try {
            List<KnifeModel> allModels = knifeModelRepository.findByBrand(brandName);
            List<KnifeModel> filteredModels = allModels.stream()
                .filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .collect(Collectors.toList());
            
            showAdminSearchModelResults(chatId, brandName, filteredModels, page, searchQuery);
        } catch (Exception e) {
            logger.severe("Error handling search models page: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при поиске");
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
        
        ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
        
        if (errorLogs.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📋 Журнал ошибок пуст");
            message.setReplyMarkup(new InlineKeyboardMarkup(List.of(
                    RowBuilder.getRow("🔙 Главное меню", "back_to_menu"))));
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
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(RowBuilder.getRow("🗑️ Очистить журнал", "error_log_clear"));
        keyboard.add(RowBuilder.getRow("🔙 Главное меню", "back_to_menu"));
        
        actionMessage.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
        
        try {
            Message sent = execute(actionMessage);
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
            moderationStateService.removeState(chatId);
            searchStates.remove(chatId);
            return;
        }
        
        // Удаляем сообщения из ModerationState
        ModerationState modState = moderationStateService.getState(chatId);
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
        moderationStateService.removeState(chatId);
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
        ModerationState modState = moderationStateService.getState(chatId);
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
        moderationStateService.removeState(chatId);
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
                deleteMessage(chatId, msgId);
                deletedCount++;
        }

        // Очищаем список ПОСЛЕ удаления
        messages.clearRecentWindowMessages();
        logger.info("DEBUG: Deleted " + deletedCount + " messages, cleared list");
        
        // Очищаем состояния
        moderationStateService.removeState(chatId);
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
    
    private void handleChangeSeparatorRequest(Long chatId) {
        // Сохраняем состояние ожидания ввода разделителя
        searchStates.put(chatId, "separator");
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("✏️ Введите новый знак разделителя для альтернативных моделей:\n\n" +
                       "Текущий: \"" + settingsService.getAlternativeSeparator() + "\"\n\n" +
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
        deleteMessage(chatId, userMessageId);

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
        settingsService.setAlternativeSeparator(separator.trim());
        
        sendMessage(chatId, "✅ Разделитель изменен на: \"" + settingsService.getAlternativeSeparator() + "\"");
        handleSettingsCommand(chatId);
    }

    private void handleSettingsCommand(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚙️ Настройки\n\nТекущий разделитель альтернатив: \"" + settingsService.getAlternativeSeparator() + "\"");
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(RowBuilder.getRow("✏️ Изменить знак разделителя", "settings_change_separator"));
        keyboard.add(RowBuilder.getRow("❌ Закрыть настройки", "back_to_menu"));
        message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
        try {
            Message sent = execute(message);
            ChatMessages messages = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            messages.setLastWindowMessageId(sent.getMessageId());
        } catch (TelegramApiException e) {
            logger.severe("Error sending settings: " + e.getMessage());
        }
    }
    
    /**
     * Обработчик подтверждения добавления транзитивных альтернатив.
     * @param chatId ID чата
     * @param knifeId ID ножа
     */
    private void handleTransitiveYes(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Set<Long> transitiveIds = state.getTransitiveAlternativeIds();
            if (transitiveIds == null || transitiveIds.isEmpty()) {
                sendMessage(chatId, "❌ Транзитивные альтернативы не найдены");
                return;
            }
            
            // Получаем нож и добавляем транзитивные альтернативы
            Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
            if (knifeOpt.isEmpty()) {
                sendMessage(chatId, "❌ Нож не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            // Добавляем все транзитивные альтернативы
            for (Long transitiveId : transitiveIds) {
                Optional<Knife> transitiveOpt = knifeRepository.findById(transitiveId);
                if (transitiveOpt.isPresent()) {
                    Knife transitiveKnife = transitiveOpt.get();
                    if (!knife.getAlternatives().contains(transitiveKnife)) {
                        knife.addAlternative(transitiveKnife);
                    }
                }
            }
            
            knifeRepository.save(knife);
            
            // Удаляем сообщение с предложением
            if (state.getTransitiveMessageId() != null)
                deleteMessage(chatId, state.getTransitiveMessageId());
            
            sendMessage(chatId, "✅ Транзитивные альтернативы добавлены!");
            moderationStateService.removeState(chatId);
            handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive yes: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при добавлении транзитивных альтернатив: " + e.getMessage());
        }
    }
    
    /**
     * Обработчик отклонения добавления транзитивных альтернатив.
     * 
     * @param chatId ID чата
     * @param knifeId ID ножа
     */
    private void handleTransitiveNo(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateService.getState(chatId);
            if (state == null) {
                sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            // Удаляем сообщение с предложением
            if (state.getTransitiveMessageId() != null)
                    deleteMessage(chatId, state.getTransitiveMessageId());
            
            sendMessage(chatId, "✅ Изменения сохранены!");
            moderationStateService.removeState(chatId);
            handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive no: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }
    
    /**
     * Показывает предложение добавить транзитивные альтернативы.
     * Требование 9.7: При добавлении альтернативы к одобренному сертификату через редактирование
     * система должна запустить поиск транзитивных альтернатив и предложить их модератору.
     * 
     * @param chatId ID чата
     * @param knifeId ID ножа
     * @param transitiveAlternatives Множество найденных транзитивных альтернатив
     */
    private void showTransitiveAlternativesProposal(Long chatId, Long knifeId, Set<Knife> transitiveAlternatives) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("🔄 Найдены транзитивные альтернативы:\n\n");
            
            int count = 0;
            for (Knife knife : transitiveAlternatives) {
                if (count >= 10) {
                    message.append("... и ещё ").append(transitiveAlternatives.size() - 10).append(" альтернатив");
                    break;
                }
                message.append("• ").append(knife.getDisplayName()).append("\n");
                count++;
            }
            
            message.append("\n❓ Добавить эти альтернативы?");
            
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message.toString());
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            RowBuilder.addRow(row, "✅ Да", "transitive_yes_" + knifeId);
            RowBuilder.addRow(row, "❌ Нет", "transitive_no_" + knifeId);
            sendMessage.setReplyMarkup(new InlineKeyboardMarkup(List.of(row)));
            
            Message sent = execute(sendMessage);
            ModerationState state = moderationStateService.getState(chatId);
            if (state != null)
                state.setTransitiveMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error showing transitive alternatives proposal: " + e.getMessage());
        }
    }
    
    /**
     * Находит или создает бренд по названию.
     * 
     * @param name Название бренда
     * @return Объект Brand
     */
    private Brand findOrCreateBrand(String name) {
        if (name == null || name.trim().isEmpty()) {
            return brandRepository.findById(1L)
                    .orElseGet(() -> brandRepository.save(new Brand(null)));
        }
        
        String trimmedName = name.trim();
        return brandRepository.findByName(trimmedName)
                .orElseGet(() -> brandRepository.save(new Brand(trimmedName)));
    }
    
    /**
     * Находит или создает модель ножа по названию.
     * 
     * @param name Название модели
     * @return Объект KnifeModel
     */
    private KnifeModel findOrCreateKnifeModel(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя модели не может быть пустым");
        }
        
        String trimmedName = name.trim();
        return knifeModelRepository.findByName(trimmedName)
                .orElseGet(() -> knifeModelRepository.save(new KnifeModel(trimmedName)));
    }
    
    private void handleAdminBrandSelection(Long chatId, String brandName) {
        try {
            // Удаляем все сообщения уровня 1 и выше
            List<Integer> messagesToDelete = navigationStackService.getMessagesAtOrBelow(chatId, 1);
            for (Integer messageId : messagesToDelete) {
                deleteMessage(chatId, messageId);
            }
            navigationStackService.clearFrom(chatId, 1);
            
            // Показываем список моделей бренда (аналогично KnifeBot)
            List<Knife> knives = knifeService.getAllKnivesByBrand(brandName);
            
            if (knives.isEmpty()) {
                sendMessage(chatId, "❌ У бренда " + brandName + " нет моделей");
                return;
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🏷️ " + brandName + "\nВсего: " + knives.size() + " моделей");
            
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            int itemsPerPage = 20;
            int endIndex = Math.min(itemsPerPage, knives.size());
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < endIndex; i++) {
                Knife knife = knives.get(i);
                String displayName = knife.getDisplayName();
                if (displayName.length() > 30) {
                    displayName = displayName.substring(0, 27) + "...";
                }
                RowBuilder.addRow(currentRow, displayName, "view_approved_" + knife.getId());

                if (currentRow.size() == 2) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            if (!currentRow.isEmpty())
                keyboard.add(currentRow);

            keyboard.add(RowBuilder.getRow("🔙 К главному меню", "menu_back_to_main"));
            message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
            Message sent = execute(message);
            navigationStackService.setLevel(chatId, 1, sent.getMessageId());
            ChatMessages chatMsgs = chatMessages.computeIfAbsent(chatId, k -> new ChatMessages());
            chatMsgs.getRecentWindowMessages().add(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error handling admin brand selection: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при загрузке моделей бренда");
        }
    }
    
    private void updateAdminMainMenu(Long chatId, int page) {
        try {
            List<Brand> allBrands = knifeService.getAllBrandsWithKnives();
            
            int itemsPerPage = 30;
            int totalPages = (int) Math.ceil((double) allBrands.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allBrands.size());
            List<Brand> pageBrands = allBrands.subList(startIndex, endIndex);
            
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (Brand brand : pageBrands) {
                String displayName = brand.getName();
                if (displayName.length() > 15)
                    displayName = displayName.substring(0, 12) + "...";

                RowBuilder.addRow(currentRow, displayName, "admin_brand_" + brand.getName());
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            if (!currentRow.isEmpty())
                keyboard.add(currentRow);
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                int prevPage = (page - 1 + totalPages) % totalPages;
                int nextPage = (page + 1) % totalPages;
                RowBuilder.addRow(paginationRow, "⬅️", "admin_main_page_" + prevPage);
                RowBuilder.addRow(paginationRow, (page + 1) + "/" + totalPages, "admin_main_current_page");
                RowBuilder.addRow(paginationRow, "➡️", "admin_main_page_" + nextPage);
                keyboard.add(paginationRow);
            }
            keyboard.add(RowBuilder.getRow( "🔍 Поиск", "menu_search"));
            keyboard.add(RowBuilder.getRow("📤 Загрузить", "menu_upload"));
            keyboard.add(RowBuilder.getRow("📋 Ожидающие заявки", "menu_pending"));
            keyboard.add(RowBuilder.getRow("📋 Журнал ошибок", "menu_error_log"));
            keyboard.add(RowBuilder.getRow("⚙️ Настройки", "menu_settings"));

            ChatMessages messages = chatMessages.get(chatId);
            if (messages != null && messages.getMainMenuMessageId() != null) {
                EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
                editMarkup.setChatId(chatId.toString());
                editMarkup.setMessageId(messages.getMainMenuMessageId());
                editMarkup.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
                execute(editMarkup);
            }
            
        } catch (Exception e) {
            logger.severe("Error updating admin main menu: " + e.getMessage());
        }
    }
}
