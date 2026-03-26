package com.knifecerts.bot.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.manager.ModerationStateManager;
import com.knifecerts.bot.manager.ModerationStateManager.ModerationState;
import com.knifecerts.model.Knife;
import com.knifecerts.repository.KnifeRepository;

@Component
public class TransitiveAlternativesHandler {
    
    private static final Logger logger = Logger.getLogger(TransitiveAlternativesHandler.class.getName());
    
    private final AdminBot adminBot;
    private final ModerationStateManager moderationStateManager;
    private final KnifeRepository knifeRepository;
    
    public TransitiveAlternativesHandler(
            AdminBot adminBot,
            ModerationStateManager moderationStateManager,
            KnifeRepository knifeRepository) {
        this.adminBot = adminBot;
        this.moderationStateManager = moderationStateManager;
        this.knifeRepository = knifeRepository;
    }
    
    public void showProposal(Long chatId, Long knifeId, Set<Knife> transitiveAlternatives) {
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
                .callbackData("transitive_yes_" + knifeId)
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Нет")
                .callbackData("transitive_no_" + knifeId)
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            sendMessage.setReplyMarkup(markup);
            
            Message sent = adminBot.execute(sendMessage);
            
            ModerationState state = moderationStateManager.getState(chatId);
            if (state != null) {
                state.setTransitiveMessageId(sent.getMessageId());
            }
            
        } catch (Exception e) {
            logger.severe("Error showing transitive alternatives proposal: " + e.getMessage());
        }
    }
    
    public void handleYes(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Set<Long> transitiveIds = state.getTransitiveAlternativeIds();
            if (transitiveIds == null || transitiveIds.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Транзитивные альтернативы не найдены");
                return;
            }
            
            Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
            if (knifeOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Нож не найден");
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
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getTransitiveMessageId());
                    adminBot.execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete transitive message: " + e.getMessage());
                }
            }
            
            adminBot.sendMessage(chatId, "✅ Транзитивные альтернативы добавлены!");
            moderationStateManager.removeState(chatId);
            adminBot.handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive yes: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при добавлении транзитивных альтернатив: " + e.getMessage());
        }
    }
    
    public void handleNo(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            if (state.getTransitiveMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getTransitiveMessageId());
                    adminBot.execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete transitive message: " + e.getMessage());
                }
            }
            
            adminBot.sendMessage(chatId, "✅ Изменения сохранены!");
            moderationStateManager.removeState(chatId);
            adminBot.handleApprovedCommand(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Error handling transitive no: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }
}
