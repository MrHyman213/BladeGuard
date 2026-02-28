package com.knifecerts;

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
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.model.ModerationSession;
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

    @Autowired
    private ModerationSessionManager sessionManager;

    @Value("${webapp.base.url}")
    private String webAppBaseUrl;

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
                    } else if (messageText.startsWith("/setmodel ")) {
                        handleSetModelCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setdesc ")) {
                        handleSetDescCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/setalt ")) {
                        handleSetAltCommand(chatId, moderatorId, messageText);
                    } else if (messageText.equals("/clearall")) {
                        handleClearAllCommand(chatId, moderatorId);
                    } else {
                        sendMessage(chatId, "Неизвестная команда. Используйте /start для списка команд.");
                    }
                } else if (update.getMessage().hasPhoto()) {
                    handlePhoto(update);
                }
            }
        } catch (Exception e) {
            logger.severe("Неожиданная ошибка в AdminBot: " + e.getClass().getName() + " - " + e.getMessage());
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
            if (data.equals("menu_pending")) {
                handlePendingCommand(chatId);
            } else if (data.equals("menu_upload")) {
                sendMessage(chatId, "📤 Отправьте фото для загрузки на Яндекс.Диск");
            } else if (data.equals("menu_clearall")) {
                sendClearConfirmation(chatId);
            } else if (data.equals("confirm_clear_yes")) {
                handleClearAllCommand(chatId, moderatorId);
            } else if (data.equals("confirm_clear_no")) {
                sendMessage(chatId, "❌ Очистка отменена");
                sendMainMenu(chatId);
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
            } else if (data.equals("back_to_menu")) {
                sendMainMenu(chatId);
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
                
                execute(message);
                return;
            }
            
            // Показываем список с кнопками для каждой заявки
            for (Submission submission : pendingSubmissions) {
                StringBuilder text = new StringBuilder();
                text.append("📋 Заявка #").append(submission.getId()).append("\n");
                text.append("👤 @").append(submission.getUsername()).append("\n");
                text.append("🔪 ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n");
                text.append("📅 ").append(submission.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(text.toString());
                
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("👁️ Просмотреть")
                    .callbackData("view_" + submission.getId())
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
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            menuButton.setReplyMarkup(markup);
            
            execute(menuButton);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка заявок: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка заявок");
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
            caption.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n\n");
            caption.append("📝 Описание:\n").append(submission.getDescription() != null ? submission.getDescription() : "не указано").append("\n\n");
            
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
            
            execute(sendPhoto);
            
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
            text.append("/setmodel ").append(submissionId).append(" <новая модель>\n");
            text.append("/setdesc ").append(submissionId).append(" <новое описание>\n");
            text.append("/setalt ").append(submissionId).append(" <модель1, модель2, ...>\n\n");
            text.append("Текущие данные:\n");
            text.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n");
            text.append("📝 Описание: ").append(submission.getDescription() != null ? submission.getDescription() : "не указано").append("\n");
            
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
            
            ModerationSession session = sessionManager.createSession(submissionId, moderatorId);
            
            String webAppUrl = webAppBaseUrl + "/moderation.html?token=" + session.getToken();
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Нажмите кнопку ниже для редактирования заявки #" + submissionId);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✏️ Открыть редактор")
                .webApp(new WebAppInfo(webAppUrl))
                .build());
            
            keyboard.add(row);
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
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
            
            if (submission.getModelName() != null) {
                message.append("🔪 Модель: ").append(submission.getModelName()).append("\n");
            } else {
                message.append("🔪 Модель: не указана\n");
            }
            
            if (submission.getDescription() != null) {
                message.append("📝 Описание: ").append(submission.getDescription()).append("\n");
            } else {
                message.append("📝 Описание: не указано\n");
            }
            
            message.append("📅 Дата подачи: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            message.append("📊 Статус: ").append(submission.getStatus()).append("\n");
            
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
            execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending message: " + e.getMessage());
        }
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
            .text("📤 Загрузить фото")
            .callbackData("menu_upload")
            .build());
        keyboard.add(row2);
        
        // Третья строка
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
            .text("🗑️ Очистить все данные")
            .callbackData("menu_clearall")
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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка #").append(submission.getId()).append("\n\n");
            caption.append("👤 Пользователь: @").append(submission.getUsername()).append("\n");
            caption.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            caption.append("📅 Дата: ").append(submission.getCreatedAt().format(formatter)).append("\n");
            caption.append("📊 Статус: ").append(submission.getStatus()).append("\n\n");
            caption.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n\n");
            caption.append("📝 Описание:\n").append(submission.getDescription() != null ? submission.getDescription() : "не указано").append("\n\n");
            
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
                
                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(InlineKeyboardButton.builder()
                    .text("✏️ Редактировать")
                    .callbackData("edit_" + submissionId)
                    .build());
                keyboard.add(row1);
                
                List<InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(InlineKeyboardButton.builder()
                    .text("✅ Одобрить")
                    .callbackData("approve_" + submissionId)
                    .build());
                row2.add(InlineKeyboardButton.builder()
                    .text("❌ Отклонить")
                    .callbackData("reject_" + submissionId)
                    .build());
                keyboard.add(row2);
                
                List<InlineKeyboardButton> row3 = new ArrayList<>();
                row3.add(InlineKeyboardButton.builder()
                    .text("🔙 Главное меню")
                    .callbackData("back_to_menu")
                    .build());
                keyboard.add(row3);
                
                markup.setKeyboard(keyboard);
                sendPhoto.setReplyMarkup(markup);
            }
            
            execute(sendPhoto);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
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
            text.append("/setmodel ").append(submissionId).append(" <новая модель>\n");
            text.append("/setdesc ").append(submissionId).append(" <новое описание>\n");
            text.append("/setalt ").append(submissionId).append(" <модель1, модель2, ...>\n\n");
            text.append("Текущие данные:\n");
            text.append("🔪 Модель: ").append(submission.getModelName() != null ? submission.getModelName() : "не указана").append("\n");
            text.append("📝 Описание: ").append(submission.getDescription() != null ? submission.getDescription() : "не указано").append("\n");
            
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
    
    private void handleSetModelCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setmodel <ID> <новая модель>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String newModel = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            String oldModel = submission.getModelName();
            submission.setModelName(newModel);
            submissionService.updateSubmission(
                submissionId, 
                newModel, 
                submission.getDescription(), 
                submission.getAlternativeModelsList()
            );
            
            sendMessage(chatId, "✅ Модель обновлена: " + newModel);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении модели: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении");
        }
    }
    
    private void handleSetDescCommand(Long chatId, Long moderatorId, String command) {
        try {
            String[] parts = command.split(" ", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Использование: /setdesc <ID> <новое описание>");
                return;
            }
            
            Long submissionId = Long.parseLong(parts[1]);
            String newDesc = parts[2].trim();
            
            Optional<Submission> submissionOpt = submissionService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            Submission submission = submissionOpt.get();
            submission.setDescription(newDesc);
            submissionService.updateSubmission(
                submissionId,
                submission.getModelName(),
                newDesc,
                submission.getAlternativeModelsList()
            );
            
            sendMessage(chatId, "✅ Описание обновлено");
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID");
        } catch (Exception e) {
            logger.severe("Ошибка при обновлении описания: " + e.getMessage());
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
                submission.getModelName(),
                submission.getDescription(),
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
            
            // Очистка папок на Яндекс.Диске
            try {
                yandexDiskService.clearFolder("app:/certificates/offers");
                sendMessage(chatId, "✅ Папка offers очищена");
            } catch (Exception e) {
                logger.warning("Ошибка очистки offers: " + e.getMessage());
                sendMessage(chatId, "⚠️ Папка offers: " + e.getMessage());
            }
            
            try {
                yandexDiskService.clearFolder("app:/certificates/certificates");
                sendMessage(chatId, "✅ Папка certificates очищена");
            } catch (Exception e) {
                logger.warning("Ошибка очистки certificates: " + e.getMessage());
                sendMessage(chatId, "⚠️ Папка certificates: " + e.getMessage());
            }
            
            sendMessage(chatId, "✅ Все данные очищены!");
            sendMainMenu(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при очистке данных: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при очистке данных");
        }
    }
}
