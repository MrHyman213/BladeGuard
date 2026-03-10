package com.knifecerts;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.model.BufferAlternative;
import com.knifecerts.model.Knife;
import com.knifecerts.model.SubmissionBuffer;

@Component("adminBotNew")
public class AdminBotNew extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(AdminBotNew.class.getName());

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
                        deleteMessage(chatId, update.getMessage().getMessageId());
                        sendMainMenu(chatId);
                    } else if (messageText.equals("/pending")) {
                        handlePendingCommand(chatId);
                    } else if (messageText.startsWith("/approve ")) {
                        handleApproveCommand(chatId, moderatorId, messageText);
                    } else if (messageText.startsWith("/reject ")) {
                        handleRejectCommand(chatId, moderatorId, messageText);
                    } else {
                        sendMessage(chatId, "Неизвестная команда. Используйте /start для списка команд.");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Неожиданная ошибка в AdminBotNew: " + e.getMessage());
            e.printStackTrace();
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
            } else if (data.equals("menu_approved")) {
                handleApprovedCommand(chatId);
            } else if (data.startsWith("view_")) {
                Long submissionId = Long.parseLong(data.substring(5));
                showSubmissionDetails(chatId, submissionId);
            } else if (data.startsWith("approve_")) {
                Long submissionId = Long.parseLong(data.substring(8));
                handleApproveSubmission(chatId, moderatorId, submissionId);
            } else if (data.startsWith("reject_")) {
                Long submissionId = Long.parseLong(data.substring(7));
                handleRejectSubmission(chatId, moderatorId, submissionId);
            } else if (data.startsWith("confirm_alt_")) {
                String[] parts = data.substring(12).split("_");
                Long submissionId = Long.parseLong(parts[0]);
                int altIndex = Integer.parseInt(parts[1]);
                handleConfirmAlternative(chatId, submissionId, altIndex);
            } else if (data.startsWith("remove_alt_")) {
                String[] parts = data.substring(11).split("_");
                Long submissionId = Long.parseLong(parts[0]);
                int altIndex = Integer.parseInt(parts[1]);
                handleRemoveAlternative(chatId, submissionId, altIndex);
            } else if (data.startsWith("final_approve_")) {
                Long submissionId = Long.parseLong(data.substring(14));
                handleFinalApprove(chatId, moderatorId, submissionId);
            } else if (data.equals("back_to_menu")) {
                deleteMessage(chatId, callbackQuery.getMessage().getMessageId());
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

    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔧 Панель администратора (Новая схема)\n\nВыберите действие:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text("📋 Ожидающие заявки")
            .callbackData("menu_pending")
            .build());
        keyboard.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text("✅ Одобренные сертификаты")
            .callbackData("menu_approved")
            .build());
        keyboard.add(row2);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.severe("Error sending menu: " + e.getMessage());
        }
    }

    private void handlePendingCommand(Long chatId) {
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
                
                execute(message);
                return;
            }
            
            StringBuilder text = new StringBuilder();
            text.append("📋 Ожидающие заявки\n\n");
            text.append("Всего: ").append(pendingSubmissions.size()).append(" заявок\n");
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Кнопки с заявками (3 колонки)
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pendingSubmissions.size(); i++) {
                SubmissionBuffer sub = pendingSubmissions.get(i);
                String buttonText = sub.getDisplayName();
                
                if (buttonText.length() > 15) {
                    buttonText = buttonText.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("view_" + sub.getId())
                    .build());
                
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка заявок: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении списка заявок");
        }
    }
    private void showSubmissionDetails(Long chatId, Long submissionId) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            
            StringBuilder caption = new StringBuilder();
            caption.append("📋 Заявка #").append(submission.getId()).append("\n\n");
            caption.append("👤 Пользователь: ").append(submission.getUsername()).append("\n");
            caption.append("🆔 User ID: ").append(submission.getUserId()).append("\n");
            caption.append("📅 Дата: ").append(submission.getCreatedAt().format(formatter)).append("\n\n");
            caption.append("🔪 Модель: ").append(submission.getModelName()).append("\n");
            caption.append("🏷️ Бренд: ").append(submission.getBrandName()).append("\n");
            if (submission.getIndex() != null) {
                caption.append("🔢 Индекс: ").append(submission.getIndex()).append("\n");
            }
            
            List<BufferAlternative> alternatives = submission.getAlternatives();
            if (!alternatives.isEmpty()) {
                caption.append("\n🔄 Альтернативные модели:\n");
                for (BufferAlternative alt : alternatives) {
                    caption.append("  • ").append(alt.toString()).append("\n");
                }
            }
            
            try (InputStream photoStream = yandexDiskService.downloadPhoto(submission.getPhotoPath())) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new InputFile(photoStream, "certificate.jpg"));
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
                
                List<InlineKeyboardButton> backRow = new ArrayList<>();
                backRow.add(InlineKeyboardButton.builder()
                    .text("🔙 Назад")
                    .callbackData("menu_pending")
                    .build());
                keyboard.add(backRow);
                
                markup.setKeyboard(keyboard);
                sendPhoto.setReplyMarkup(markup);
                
                execute(sendPhoto);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при просмотре заявки");
        }
    }

    private void handleApproveSubmission(Long chatId, Long moderatorId, Long submissionId) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            
            // Если есть альтернативы, показываем меню подтверждения
            if (!submission.getAlternatives().isEmpty()) {
                showAlternativesConfirmation(chatId, submission);
            } else {
                // Если альтернатив нет, сразу одобряем
                handleFinalApprove(chatId, moderatorId, submissionId);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки");
        }
    }

    private void showAlternativesConfirmation(Long chatId, SubmissionBuffer submission) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("✅ Вы уверены что хотите добавить в базу следующие альтернативные модели?\n\n");
            
            List<BufferAlternative> alternatives = submission.getAlternatives();
            for (int i = 0; i < alternatives.size(); i++) {
                BufferAlternative alt = alternatives.get(i);
                text.append("• ").append(alt.toString()).append("\n");
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Кнопки для каждой альтернативы
            for (int i = 0; i < alternatives.size(); i++) {
                BufferAlternative alt = alternatives.get(i);
                List<InlineKeyboardButton> altRow = new ArrayList<>();
                altRow.add(InlineKeyboardButton.builder()
                    .text("❌ " + alt.toString())
                    .callbackData("remove_alt_" + submission.getId() + "_" + i)
                    .build());
                keyboard.add(altRow);
            }
            
            // Кнопки действий
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("✅ Принять")
                .callbackData("final_approve_" + submission.getId())
                .build());
            actionRow.add(InlineKeyboardButton.builder()
                .text("❌ Отклонить")
                .callbackData("reject_" + submission.getId())
                .build());
            keyboard.add(actionRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при показе альтернатив: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при показе альтернатив");
        }
    }

    private void handleRemoveAlternative(Long chatId, Long submissionId, int altIndex) {
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            
            if (submissionOpt.isEmpty()) {
                sendMessage(chatId, "❌ Заявка не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            List<BufferAlternative> alternatives = submission.getAlternatives();
            
            if (altIndex >= 0 && altIndex < alternatives.size()) {
                BufferAlternative removed = alternatives.get(altIndex);
                submissionBufferService.removeAlternativeFromSubmission(
                    submissionId, 
                    removed.getModelName(), 
                    removed.getBrandName()
                );
                
                // Обновляем меню
                showAlternativesConfirmation(chatId, submissionBufferService.getSubmissionById(submissionId).get());
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при удалении альтернативы: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при удалении альтернативы");
        }
    }

    private void handleFinalApprove(Long chatId, Long moderatorId, Long submissionId) {
        try {
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
            sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!\n" +
                "Создан сертификат: " + knife.getDisplayName());
            
            // Возвращаемся к списку заявок
            handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при финальном одобрении: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    private void handleRejectSubmission(Long chatId, Long moderatorId, Long submissionId) {
        try {
            submissionBufferService.rejectSubmission(submissionId);
            
            sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            // Возвращаемся к списку заявок
            handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки");
        }
    }

    private void handleApprovedCommand(Long chatId) {
        try {
            List<Knife> approvedKnives = knifeService.getAllCertificates();
            
            if (approvedKnives.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("📭 Нет одобренных сертификатов.");
                
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
            
            StringBuilder text = new StringBuilder();
            text.append("✅ Одобренные сертификаты\n\n");
            text.append("Всего: ").append(approvedKnives.size()).append(" сертификатов\n");
            
            for (Knife knife : approvedKnives) {
                text.append("• ").append(knife.getDisplayName()).append("\n");
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            execute(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении одобренных сертификатов: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении сертификатов");
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
            handleFinalApprove(chatId, moderatorId, submissionId);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /approve <ID>");
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
            handleRejectSubmission(chatId, moderatorId, submissionId);
            
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /reject <ID>");
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при отклонении заявки");
        }
    }

    private void handleConfirmAlternative(Long chatId, Long submissionId, int altIndex) {
        // Этот метод может быть использован для подтверждения конкретной альтернативы
        // Пока не реализован
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
}