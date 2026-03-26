package com.knifecerts.bot.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.service.NavigationStackService;
import com.knifecerts.service.SubmissionBufferService;

@Component
public class PendingSubmissionsHandler {

    private static final Logger logger = Logger.getLogger(PendingSubmissionsHandler.class.getName());
    private static final int ITEMS_PER_PAGE = 30;

    @Autowired
    private SubmissionBufferService submissionBufferService;

    @Autowired
    private NavigationStackService navigationStackService;

    @Autowired
    private AdminBot adminBot;

    public void handlePendingCommand(Long chatId) {
        handlePendingCommand(chatId, 0);
    }

    public void handlePendingCommand(Long chatId, int page) {
        try {
            List<SubmissionBuffer> pendingSubmissions = submissionBufferService.getAllPendingSubmissions();

            if (pendingSubmissions.isEmpty()) {
                sendEmptyListMessage(chatId);
                return;
            }

            sendPendingList(chatId, pendingSubmissions, page);

        } catch (Exception e) {
            logger.severe("Ошибка при получении списка заявок: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при получении списка заявок");
        }
    }

    private void sendEmptyListMessage(Long chatId) {
        try {
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

            Message sent = adminBot.executeAndTrack(message);

            adminBot.setPendingListMessageId(chatId, sent.getMessageId());

            navigationStackService.setLevel(chatId, 1, sent.getMessageId());

        } catch (Exception e) {
            logger.severe("Error sending empty list message: " + e.getMessage());
        }
    }

    private void sendPendingList(Long chatId, List<SubmissionBuffer> pendingSubmissions, int page) {
        try {
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / ITEMS_PER_PAGE);
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, pendingSubmissions.size());

            List<SubmissionBuffer> pageItems = pendingSubmissions.subList(startIndex, endIndex);

            StringBuilder text = new StringBuilder();
            text.append("📋 Ожидающие заявки\n\n");
            text.append("Всего: ").append(pendingSubmissions.size()).append(" заявок\n");
            text.append("Страница ").append(page + 1).append(" из ").append(totalPages);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());

            InlineKeyboardMarkup markup = buildPendingListKeyboard(pageItems, page, totalPages);
            message.setReplyMarkup(markup);

            Message sent = adminBot.execute(message);

            adminBot.setPendingListMessageId(chatId, sent.getMessageId());
            adminBot.setLastWindowMessageId(chatId, sent.getMessageId());

            navigationStackService.setLevel(chatId, 1, sent.getMessageId());

        } catch (Exception e) {
            logger.severe("Error sending pending list: " + e.getMessage());
        }
    }

    private InlineKeyboardMarkup buildPendingListKeyboard(List<SubmissionBuffer> pageItems, int page, int totalPages) {
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
        return markup;
    }

    public void updatePendingListIfNeeded(Long chatId) {
        Integer pendingListMessageId = adminBot.getPendingListMessageId(chatId);
        if (pendingListMessageId == null) {
            handlePendingCommand(chatId);
            return;
        }

        try {
            List<SubmissionBuffer> pendingSubmissions = submissionBufferService.getAllPendingSubmissions();

            int page = 0;
            int totalPages = (int) Math.ceil((double) pendingSubmissions.size() / ITEMS_PER_PAGE);
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, pendingSubmissions.size());

            List<SubmissionBuffer> pageItems = pendingSubmissions.subList(startIndex, endIndex);

            StringBuilder text = new StringBuilder();
            text.append("📋 Ожидающие заявки\n\n");
            text.append("Всего: ").append(pendingSubmissions.size()).append(" заявок\n");
            text.append("Страница ").append(page + 1).append(" из ").append(totalPages);

            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(pendingListMessageId);
            editMessage.setText(text.toString());

            InlineKeyboardMarkup markup = buildPendingListKeyboard(pageItems, page, totalPages);
            editMessage.setReplyMarkup(markup);

            adminBot.execute(editMessage);

        } catch (Exception e) {
            logger.warning("Не удалось отредактировать список, отправляем новый: " + e.getMessage());
            handlePendingCommand(chatId);
        }
    }
}
