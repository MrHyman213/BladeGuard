package com.knifecerts.bot.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.manager.ModerationStateManager;
import com.knifecerts.bot.manager.ModerationStateManager.ModerationState;
import com.knifecerts.bot.state.ChatMessages;
import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.model.Knife;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;
import com.knifecerts.service.AlternativesParser;
import com.knifecerts.service.SubmissionBufferService;
import com.knifecerts.service.TransitiveAlternativesService;

@Component
public class SubmissionModerationHandler {

    private static final Logger logger = Logger.getLogger(SubmissionModerationHandler.class.getName());

    private final AdminBot adminBot;
    private final ModerationStateManager moderationStateManager;
    private final SubmissionBufferService submissionBufferService;
    private final SubmissionBufferRepository submissionBufferRepository;
    private final KnifeRepository knifeRepository;
    private final TransitiveAlternativesService transitiveAlternativesService;
    private final AlternativesParser alternativesParser;

    public SubmissionModerationHandler(
            AdminBot adminBot,
            ModerationStateManager moderationStateManager,
            SubmissionBufferService submissionBufferService,
            SubmissionBufferRepository submissionBufferRepository,
            KnifeRepository knifeRepository,
            TransitiveAlternativesService transitiveAlternativesService,
            AlternativesParser alternativesParser) {
        this.adminBot = adminBot;
        this.moderationStateManager = moderationStateManager;
        this.submissionBufferService = submissionBufferService;
        this.submissionBufferRepository = submissionBufferRepository;
        this.knifeRepository = knifeRepository;
        this.transitiveAlternativesService = transitiveAlternativesService;
        this.alternativesParser = alternativesParser;
    }

    public void handleModEditField(Long chatId, Long submissionId, String field) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
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
            
            Message sentMessage = adminBot.execute(message);
            state.setPromptMessageId(sentMessage.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при запросе поля: " + e.getMessage());
        }
    }

    public void handleModFieldInput(Long chatId, String text, Integer userMessageId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null || state.getEditingField() == null) {
            return;
        }
        
        adminBot.deleteUserMessage(chatId, userMessageId);
        
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
                        ? entry.brand() + " / " + entry.name()
                        : entry.name();
                    state.getAlternativeModels().add(altStr);
                }
                break;
        }
        
        state.setEditingField(null);
        
        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        Long submissionId = ((SubmissionBuffer) state.getOriginal()).getId();
        adminBot.sendSubmissionForm(chatId, submissionId);
    }

    public void handleModAddAlternative(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        state.setEditingField("alt");
        
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("➕ Введите альтернативу в формате:\nБренд / Название\n\n" +
                "Можно ввести несколько через запятую:\nБренд1 / Название1, Бренд2 / Название2");
            
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
            
            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Error sending alternative prompt: " + e.getMessage());
        }
    }

    public void handleModRemoveAlternative(Long chatId, Long submissionId, int index) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null || state.getAlternativeModels() == null) {
            return;
        }
        
        if (index >= 0 && index < state.getAlternativeModels().size()) {
            state.getAlternativeModels().remove(index);
            adminBot.sendSubmissionForm(chatId, submissionId);
        }
    }

    public void handleModCancelInput(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
        
        state.setEditingField(null);
    }

    public void handleModApprove(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        List<String> errors = new ArrayList<>();
        
        if (state.getName() == null || state.getName().trim().isEmpty()) {
            errors.add("❌ Название модели не заполнено");
        }
        
        if (state.getIndexCode() == null || state.getIndexCode().trim().isEmpty()) {
            errors.add("❌ Индекс не заполнен");
        }
        
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("⚠️ Невозможно одобрить заявку:\n\n");
            for (String error : errors) {
                message.append(error).append("\n");
            }
            message.append("\n📝 Заполните все обязательные поля.");
            
            adminBot.sendMessage(chatId, message.toString());
            return;
        }
        
        try {
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            
            submission.setModelName(state.getName().trim());
            submission.setBrandName(state.getBrand() != null && !state.getBrand().trim().isEmpty() 
                ? state.getBrand().trim() 
                : null);
            submission.setIndex(state.getIndexCode().trim());
            
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
            
            Optional<Knife> duplicateKnife = submissionBufferService.findDuplicateKnife(
                submission.getBrandName(),
                submission.getModelName(),
                submission.getIndex()
            );
            
            if (duplicateKnife.isPresent()) {
                Knife existingKnife = duplicateKnife.get();
                state.setDuplicateKnifeId(existingKnife.getId());
                
                String duplicateMessage = "⚠️ Найден существующий нож с такими же параметрами:\n\n" +
                        "🔪 " + existingKnife.getDisplayName() + "\n\n" +
                        "Хотите обновить фото существующего ножа вместо создания дубликата?";
                
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(InlineKeyboardButton.builder()
                    .text("✅ Обновить фото")
                    .callbackData("mod_replace_photo_yes_" + submissionId)
                    .build());
                row.add(InlineKeyboardButton.builder()
                    .text("❌ Создать новый")
                    .callbackData("mod_replace_photo_no_" + submissionId)
                    .build());
                rows.add(row);
                keyboard.setKeyboard(rows);
                
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(duplicateMessage);
                msg.setReplyMarkup(keyboard);
                
                Message sentMsg = adminBot.execute(msg);
                state.setDuplicateMessageId(sentMsg.getMessageId());
                
                return;
            }
            
            List<String> pendingAlts = state.getAlternativeModels();
            if (pendingAlts != null && !pendingAlts.isEmpty()) {
                showAltConfirmation(chatId, submissionId, pendingAlts, state);
            } else {
                approveSubmissionInternal(chatId, submissionId, submission, state);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    private void showAltConfirmation(Long chatId, Long submissionId, List<String> pendingAlts, ModerationState state) {
        try {
            StringBuilder text = new StringBuilder();
            text.append("🔄 Найдены новые альтернативы:\n");
            for (String alt : pendingAlts) {
                text.append("• ").append(alt).append("\n");
            }
            text.append("\nЗавершить добавление?");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✅ Да, завершить")
                .callbackData("alt_confirm_yes_" + submissionId)
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Отмена")
                .callbackData("alt_confirm_no_" + submissionId)
                .build());
            rows.add(row);
            markup.setKeyboard(rows);
            
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(text.toString());
            msg.setReplyMarkup(markup);
            
            Message sentMsg = adminBot.execute(msg);
            state.setConfirmationMessageId(sentMsg.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Ошибка при показе подтверждения альтернатив: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    public void handleAltConfirmYes(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        if (state.getConfirmationMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getConfirmationMessageId());
            state.setConfirmationMessageId(null);
        }
        
        SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
        approveSubmissionInternal(chatId, submissionId, submission, state);
    }

    public void handleAltConfirmNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        if (state.getConfirmationMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getConfirmationMessageId());
            state.setConfirmationMessageId(null);
        }
        
        adminBot.sendSubmissionForm(chatId, submissionId);
    }

    public void handleDuplicatePhotoYes(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            Long duplicateKnifeId = state.getDuplicateKnifeId();
            
            if (duplicateKnifeId == null) {
                adminBot.sendMessage(chatId, "❌ ID дубликата не найден");
                return;
            }
            
            if (state.getDuplicateMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getDuplicateMessageId());
            }
            
            submissionBufferService.replaceKnifePhoto(duplicateKnifeId, submission.getPhotoPath());
            
            if (state.getFormMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getFormMessageId());
            }
            
            submissionBufferService.rejectSubmission(submissionId);
            
            moderationStateManager.removeState(chatId);
            
            adminBot.sendMessage(chatId, "✅ Фото ножа #" + duplicateKnifeId + " успешно обновлено!");
            
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при замене фото: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при замене фото: " + e.getMessage());
        }
    }

    public void handleDuplicatePhotoNo(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние модерации не найдено");
            return;
        }
        
        try {
            SubmissionBuffer submission = (SubmissionBuffer) state.getOriginal();
            
            if (state.getDuplicateMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getDuplicateMessageId());
            }
            
            List<String> pendingAlts = state.getAlternativeModels();
            if (pendingAlts != null && !pendingAlts.isEmpty()) {
                showAltConfirmation(chatId, submissionId, pendingAlts, state);
            } else {
                approveSubmissionInternal(chatId, submissionId, submission, state);
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

    private void approveSubmissionInternal(Long chatId, Long submissionId, 
                                          SubmissionBuffer submission, ModerationState state) {
        try {
            Knife knife = submissionBufferService.approveSubmission(submissionId);
            
            if (state.getFormMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getFormMessageId());
            }
            
            if (state.getPromptMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getPromptMessageId());
            }
            
            Set<Long> newAlternativeIds = knife.getAlternatives().stream()
                .map(Knife::getId)
                .collect(Collectors.toSet());
            
            if (!newAlternativeIds.isEmpty()) {
                Set<Knife> transitiveAlternatives = transitiveAlternativesService.findTransitive(
                    knife.getId(), newAlternativeIds);
                
                if (!transitiveAlternatives.isEmpty()) {
                    state.setApprovedKnifeId(knife.getId());
                    state.setTransitiveAlternativeIds(transitiveAlternatives.stream()
                        .map(Knife::getId)
                        .collect(Collectors.toSet()));
                    
                    showTransitiveAlternativesProposalForPending(chatId, knife.getId(), transitiveAlternatives, state);
                    return;
                }
            }
            
            moderationStateManager.removeState(chatId);
            adminBot.sendMessage(chatId, "✅ Заявка #" + submissionId + " одобрена!");
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при одобрении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при одобрении заявки: " + e.getMessage());
        }
    }

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
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✅ Да")
                .callbackData("transitive_pending_yes_" + knifeId)
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Нет")
                .callbackData("transitive_pending_no_" + knifeId)
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            sendMessage.setReplyMarkup(markup);
            
            Message sent = adminBot.execute(sendMessage);
            state.setTransitiveMessageId(sent.getMessageId());
            
        } catch (Exception e) {
            logger.severe("Error showing transitive alternatives proposal for pending: " + e.getMessage());
            moderationStateManager.removeState(chatId);
            adminBot.handlePendingCommand(chatId);
        }
    }

    public void handleTransitivePendingYes(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Set<Long> transitiveIds = state.getTransitiveAlternativeIds();
            if (transitiveIds == null || transitiveIds.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Транзитивные альтернативы не найдены");
                moderationStateManager.removeState(chatId);
                adminBot.handlePendingCommand(chatId);
                return;
            }
            
            Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
            if (knifeOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Нож не найден");
                moderationStateManager.removeState(chatId);
                adminBot.handlePendingCommand(chatId);
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
            
            if (state.getTransitiveMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getTransitiveMessageId());
            }
            
            moderationStateManager.removeState(chatId);
            adminBot.sendMessage(chatId, "✅ Транзитивные альтернативы добавлены!");
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive pending yes: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при добавлении транзитивных альтернатив: " + e.getMessage());
        }
    }

    public void handleTransitivePendingNo(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            
            if (state != null && state.getTransitiveMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getTransitiveMessageId());
            }
            
            moderationStateManager.removeState(chatId);
            adminBot.sendMessage(chatId, "✅ Заявка одобрена!");
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive pending no: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    public void handleModRejectRequest(Long chatId, Long submissionId) {
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
            
            adminBot.execute(message);
            
        } catch (Exception e) {
            logger.severe("Error sending reject confirmation: " + e.getMessage());
        }
    }

    public void handleModReject(Long chatId, Long moderatorId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferService.getSubmissionById(submissionId);
            if (submissionOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            submissionBufferService.rejectSubmission(submissionId);
            
            if (state != null && state.getFormMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getFormMessageId());
            }
            
            if (state != null && state.getPromptMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getPromptMessageId());
            }
            
            moderationStateManager.removeState(chatId);
            
            adminBot.sendMessage(chatId, "❌ Заявка #" + submissionId + " отклонена");
            
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при отклонении заявки: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при отклонении заявки: " + e.getMessage());
        }
    }

    public void handleModCancel(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.hasChanges()) {
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
                
                adminBot.execute(message);
                
            } catch (Exception e) {
                logger.severe("Error sending cancel confirmation: " + e.getMessage());
            }
        } else {
            handleModCancelConfirm(chatId, submissionId);
        }
    }

    public void handleModCancelConfirm(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        
        if (state != null && state.getFormMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getFormMessageId());
        }
        
        if (state != null && state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
        }
        
        moderationStateManager.removeState(chatId);
        
        adminBot.updatePendingListIfNeeded(chatId);
    }

    public void handleModSaveAndExit(Long chatId, Long submissionId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        try {
            Optional<SubmissionBuffer> submissionOpt = submissionBufferRepository.findById(submissionId);
            if (submissionOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Заявка #" + submissionId + " не найдена");
                return;
            }
            
            SubmissionBuffer submission = submissionOpt.get();
            
            submission.setModelName(state.getName());
            submission.setBrandName(state.getBrand());
            submission.setIndex(state.getIndexCode());
            
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                String alternativesStr = String.join(", ", state.getAlternativeModels());
                submission.setAlternatives(alternativesStr);
            } else {
                submission.setAlternatives(null);
            }
            
            submissionBufferRepository.save(submission);
            
            if (state.getFormMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getFormMessageId());
            }
            
            if (state.getPromptMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getPromptMessageId());
            }
            
            moderationStateManager.removeState(chatId);
            
            adminBot.sendMessage(chatId, "💾 Изменения сохранены!");
            adminBot.handlePendingCommand(chatId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при сохранении: " + e.getMessage());
        }
    }
}
