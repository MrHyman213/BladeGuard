package com.knifecerts.bot.handler;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.manager.ModerationStateManager;
import com.knifecerts.bot.manager.ModerationStateManager.ModerationState;
import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.dto.ParsedCaption;
import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.repository.SubmissionBufferRepository;
import com.knifecerts.service.AlternativesParser;
import com.knifecerts.service.AlternativesParserImpl;
import com.knifecerts.service.CaptionParser;
import com.knifecerts.service.MainMenuUpdateService;
import com.knifecerts.service.YandexDiskService;

@Component
public class PhotoUploadHandler {

    private static final Logger logger = Logger.getLogger(PhotoUploadHandler.class.getName());

    @Autowired
    private AdminBot adminBot;

    @Autowired
    private YandexDiskService yandexDiskService;

    @Autowired
    private ModerationStateManager moderationStateManager;

    @Autowired
    private CaptionParser captionParser;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private KnifeModelRepository knifeModelRepository;

    @Autowired
    private KnifeRepository knifeRepository;

    @Autowired
    private SubmissionBufferRepository submissionBufferRepository;

    @Autowired
    private MainMenuUpdateService mainMenuUpdateService;

    @Value("${telegram.admin.bot.token}")
    private String botToken;

    /**
     * Обрабатывает загрузку фото для прямой загрузки сертификата (Req 15.1-15.3).
     */
    public void handleUploadPhoto(Update update, Long chatId) {
        try {
            PhotoSize photo = update.getMessage().getPhoto()
                    .stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            if (photo == null) {
                adminBot.sendMessage(chatId, "❌ Не удалось получить фото");
                return;
            }

            // Удаляем сообщение пользователя с фото
            adminBot.deleteUserMessage(chatId, update.getMessage().getMessageId());

            // Скачиваем фото из Telegram
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = adminBot.execute(getFileMethod);
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newFileName = "photo_" + timestamp + ".jpg";
            String newPath;

            try (InputStream photoStream = new java.net.URL(fileUrl).openStream()) {
                // Загружаем фото в app:/certificates/
                newPath = yandexDiskService.uploadToCertificates(photoStream, newFileName);
            }

            // Требование 15.3: Парсим caption через CaptionParser
            String caption = update.getMessage().getCaption();

            // Сохраняем путь к фото в состояние для последующего заполнения формы
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                state = moderationStateManager.createEmptyState(chatId);
            }
            state.setPhotoPath(newPath);

            // Парсим caption если он есть
            if (caption != null && !caption.trim().isEmpty()) {
                ParsedCaption parsed = captionParser.parse(caption);
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

        } catch (Exception e) {
            logger.severe("Error handling upload photo: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при загрузке фото. Попробуйте еще раз.");
        }
    }

    /**
     * Обрабатывает загрузку фото для замены/установки в одобренном сертификате (Req 11.1-11.3).
     */
    public void handleApprovedPhoto(Update update, Long chatId, com.knifecerts.model.ConversationStep adminStep) {
        try {
            PhotoSize photo = update.getMessage().getPhoto()
                    .stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            if (photo == null) {
                adminBot.sendMessage(chatId, "❌ Не удалось получить фото");
                return;
            }

            // Удаляем сообщение пользователя с фото
            adminBot.deleteUserMessage(chatId, update.getMessage().getMessageId());

            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null || state.getApprovedKnifeId() == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено. Попробуйте снова.");
                return;
            }

            Long knifeId = state.getApprovedKnifeId();

            Optional<Knife> knifeOpt = knifeRepository.findById(knifeId);
            if (knifeOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Нож не найден");
                return;
            }
            Knife knife = knifeOpt.get();

            // Скачиваем фото из Telegram
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = adminBot.execute(getFileMethod);
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newFileName = "photo_" + timestamp + ".jpg";
            String newPath;

            try (InputStream photoStream = new java.net.URL(fileUrl).openStream()) {
                if (adminStep == com.knifecerts.model.ConversationStep.ADMIN_WAITING_FOR_REPLACEMENT_PHOTO) {
                    // Замена: переместить старое в app:/archive/replaced/, загрузить новое в app:/certificates/
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
                    // Загружаем новое фото в app:/certificates/
                    newPath = yandexDiskService.uploadToCertificates(photoStream, newFileName);
                } else {
                    // Установка: загрузить в app:/certificates/
                    newPath = yandexDiskService.uploadToCertificates(photoStream, newFileName);
                }
            }

            // Обновляем photo_path в knives
            knife.setPhotoPath(newPath);
            knifeRepository.save(knife);

            // Обновляем форму сертификата
            adminBot.sendMessage(chatId, "✅ Фото обновлено: " + newPath);

            // Пересоздаём ModerationState с обновлённым ножом
            Integer oldFormMessageId = state.getFormMessageId();
            ModerationState newState = moderationStateManager.createState(chatId, knife, true);
            newState.setFormMessageId(oldFormMessageId);

            adminBot.sendApprovedSubmissionForm(chatId, knifeId);

        } catch (Exception e) {
            logger.severe("Error handling approved photo: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при загрузке фото. Попробуйте еще раз.");
        }
    }

    /**
     * Отправляет форму прямой загрузки сертификата (Req 15.2).
     */
    public void sendUploadForm(Long chatId, ModerationState state) {
        try {
            // Удаляем предыдущее сообщение-запрос, если оно существует
            if (state.getPromptMessageId() != null) {
                adminBot.deleteMessage(chatId, state.getPromptMessageId());
            }

            // Формируем текст с инструкциями
            StringBuilder text = new StringBuilder();
            text.append("📝 Форма добавления сертификата\n\n");
            text.append("Фото загружено: ✅\n");
            text.append("Путь: ").append(state.getPhotoPath()).append("\n\n");
            text.append("Используйте кнопки для редактирования полей.");

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            // Кнопки для редактирования
            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("🏷️ Бренд: " + (state.getBrand() != null ? state.getBrand() : "не указан"))
                .callbackData("upload_edit_brand")
                .build()));

            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("📝 Название: " + (state.getName() != null ? state.getName() : "не указано"))
                .callbackData("upload_edit_name")
                .build()));

            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("🔢 Индекс: " + (state.getIndexCode() != null ? state.getIndexCode() : "не указан"))
                .callbackData("upload_edit_index")
                .build()));

            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("🔄 Альтернативы: " + (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()
                    ? state.getAlternativeModels().size() + " шт." : "нет"))
                .callbackData("upload_edit_alt")
                .build()));

            // Кнопки действий
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("💾 Сохранить")
                .callbackData("upload_save")
                .build());
            actionRow.add(InlineKeyboardButton.builder()
                .text("✅ Добавить")
                .callbackData("upload_add")
                .build());
            keyboard.add(actionRow);

            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("❌ Закрыть")
                .callbackData("upload_cancel")
                .build()));

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            Message sentMessage = adminBot.execute(message);
            state.setFormMessageId(sentMessage.getMessageId());
            state.setPromptMessageId(null);

            moderationStateManager.setState(chatId, state);

        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы добавления: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при создании формы добавления");
        }
    }

    /**
     * Начинает редактирование поля в форме прямой загрузки (Req 15.4).
     */
    public void handleUploadEditField(Long chatId, String field) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        state.setEditingField(field);

        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
        }

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
            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateManager.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе ввода: " + e.getMessage());
        }
    }

    /**
     * Начинает редактирование альтернатив в форме прямой загрузки.
     */
    public void handleUploadEditAlt(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        state.setEditingField("alt");

        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("✏️ Отправьте альтернативные модели через запятую:\n" +
                "Формат: `название` или `бренд/название`\n" +
                "Пример: `Модель1, Бренд2/Модель2`");
        message.setParseMode("Markdown");

        try {
            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateManager.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе альтернатив: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает ввод в форме прямой загрузки (Req 15.4).
     */
    public void handleUploadFieldInput(Long chatId, String text, Integer userMessageId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null || state.getEditingField() == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        // Удаляем сообщение пользователя
        adminBot.deleteUserMessage(chatId, userMessageId);

        String field = state.getEditingField();

        if ("brand".equals(field)) {
            state.setBrand(text.trim());
        } else if ("name".equals(field)) {
            state.setName(text.trim());
        } else if ("index".equals(field)) {
            state.setIndexCode(text.trim().isEmpty() ? null : text.trim());
        } else if ("alt".equals(field)) {
            AlternativesParser parser = new AlternativesParserImpl(",");
            List<AlternativeEntry> alternatives = parser.parse(text);
            List<String> altList = alternatives.stream()
                .map(alt -> alt.brand() != null ? alt.brand() + " / " + alt.name() : alt.name())
                .collect(Collectors.toList());
            state.setAlternativeModels(altList);
        }

        state.setEditingField(null);

        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }

        sendUploadForm(chatId, state);
    }

    /**
     * Сохраняет данные в submissions_buffer (Req 15.5).
     */
    public void handleUploadSave(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        try {
            SubmissionBuffer submission = new SubmissionBuffer(
                0L,
                "admin",
                state.getName(),
                state.getBrand(),
                state.getPhotoPath()
            );
            submission.setIndex(state.getIndexCode());

            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                String altStr = state.getAlternativeModels().stream()
                    .map(alt -> {
                        String[] parts = alt.split(" / ");
                        if (parts.length == 2) {
                            return parts[0].trim() + "/" + parts[1].trim();
                        } else {
                            return parts[0].trim();
                        }
                    })
                    .collect(Collectors.joining(", "));
                submission.setAlternatives(altStr);
            }

            submission = submissionBufferRepository.save(submission);

            adminBot.sendMessage(chatId, "✅ Данные сохранены в буфере!\n" +
                    "ID заявки: #" + submission.getId() + "\n" +
                    "Ожидает одобрения модератором.");

            moderationStateManager.removeState(chatId);

        } catch (Exception e) {
            logger.severe("Ошибка при сохранении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при сохранении: " + e.getMessage());
        }
    }

    /**
     * Добавляет сертификат напрямую в knives (Req 15.6, 15.7).
     */
    public void handleUploadAdd(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        try {
            Brand brand = brandRepository.findByName(state.getBrand())
                .orElseGet(() -> brandRepository.save(new Brand(state.getBrand())));
            KnifeModel model = knifeModelRepository.findByName(state.getName())
                .orElseGet(() -> knifeModelRepository.save(new KnifeModel(state.getName())));

            Knife knife = new Knife(model, brand, state.getIndexCode(), state.getPhotoPath());
            knife = knifeRepository.save(knife);

            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                for (String altStr : state.getAlternativeModels()) {
                    String[] parts = altStr.split(" / ");
                    String altBrandName = parts.length == 2 ? parts[0].trim() : state.getBrand();
                    String altModelName = parts.length == 2 ? parts[1].trim() : parts[0].trim();

                    Brand altBrand = brandRepository.findByName(altBrandName)
                        .orElseGet(() -> brandRepository.save(new Brand(altBrandName)));
                    KnifeModel altModel = knifeModelRepository.findByName(altModelName)
                        .orElseGet(() -> knifeModelRepository.save(new KnifeModel(altModelName)));

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

            adminBot.sendMessage(chatId, "✅ Сертификат добавлен!\n" +
                    "ID ножа: #" + knife.getId() + "\n" +
                    "Путь к фото: " + state.getPhotoPath());

            mainMenuUpdateService.updateAllUserMenus();
            moderationStateManager.removeState(chatId);

        } catch (Exception e) {
            logger.severe("Ошибка при добавлении: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при добавлении: " + e.getMessage());
        }
    }

    /**
     * Запрашивает подтверждение закрытия формы (Req 15.8).
     */
    public void handleUploadCancelRequest(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        if (state.getFormMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getFormMessageId());
            state.setFormMessageId(null);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите закрыть форму? Все несохраненные данные будут потеряны.");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
            .text("✅ Да, закрыть")
            .callbackData("upload_cancel_confirm")
            .build());
        row.add(InlineKeyboardButton.builder()
            .text("❌ Нет, продолжить")
            .callbackData("upload_cancel_no")
            .build());
        keyboard.add(row);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        try {
            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());
            moderationStateManager.setState(chatId, state);
        } catch (Exception e) {
            logger.severe("Ошибка при запросе подтверждения: " + e.getMessage());
        }
    }

    /**
     * Подтверждает закрытие формы.
     */
    public void handleUploadCancelConfirm(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }

        moderationStateManager.removeState(chatId);
        adminBot.sendMessage(chatId, "✅ Форма закрыта.");
    }

    /**
     * Отменяет закрытие формы.
     */
    public void handleUploadCancelNo(Long chatId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        if (state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }

        sendUploadForm(chatId, state);
    }

    /**
     * Обрабатывает нажатие кнопки фото в форме одобренного сертификата (Req 11.1-11.3).
     */
    public void handleApprovedPhotoRequest(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }

        Knife knife = (Knife) state.getOriginal();
        boolean hasPhoto = knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty();

        state.setApprovedKnifeId(knifeId);

        String promptText = hasPhoto
            ? "📷 Отправьте новое фото для замены существующего.\n\nСтарое фото будет перемещено в архив."
            : "📷 Отправьте фото для установки сертификата.";

        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(promptText);

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            keyboard.add(List.of(InlineKeyboardButton.builder()
                .text("❌ Отмена")
                .callbackData("approved_photo_cancel_" + knifeId)
                .build()));
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());

        } catch (Exception e) {
            logger.severe("Ошибка при запросе фото: " + e.getMessage());
        }
    }

    /**
     * Отменяет ожидание нового фото.
     */
    public void handleApprovedPhotoCancelRequest(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state != null && state.getPromptMessageId() != null) {
            adminBot.deleteMessage(chatId, state.getPromptMessageId());
            state.setPromptMessageId(null);
        }
    }
}
