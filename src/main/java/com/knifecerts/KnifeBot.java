package com.knifecerts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.logging.Logger;

@Component
public class KnifeBot extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(KnifeBot.class.getName());

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Autowired
    private YandexDiskService yandexDiskService;

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
        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            
            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();

                if (messageText.equals("/start")) {
                    sendMessage(chatId, "Привет! Я бот для работы с сертификатами на ножи.\n\n" +
                            "Команды:\n" +
                            "/start - Показать это сообщение\n" +
                            "/list - Показать список фотографий\n" +
                            "/get <имя_файла> - Получить фотографию\n\n" +
                            "Отправьте мне фото, и я загружу его на Яндекс.Диск.");
                } else if (messageText.equals("/list")) {
                    handleListPhotos(chatId);
                } else if (messageText.startsWith("/get ")) {
                    String fileName = messageText.substring(5).trim();
                    handleGetPhoto(chatId, fileName);
                } else {
                    sendMessage(chatId, "Вы написали: " + messageText);
                }
            } else if (update.getMessage().hasPhoto()) {
                handlePhoto(update);
            }
        }
    }

    private void handlePhoto(Update update) {
        Long chatId = update.getMessage().getChatId();
        
        try {
            PhotoSize photo = update.getMessage().getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            
            if (photo == null) {
                sendMessage(chatId, "Ошибка: не удалось получить фото");
                return;
            }

            sendMessage(chatId, "Загружаю фото на Яндекс.Диск...");

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
            for (StackTraceElement element : e.getStackTrace()) {
                logger.severe("  at " + element.toString());
            }
            if (e.getCause() != null) {
                logger.severe("Caused by: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
            }
            sendMessage(chatId, "❌ Ошибка при загрузке фото. Попробуйте еще раз.");
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


    private void handleListPhotos(Long chatId) {
        try {
            sendMessage(chatId, "Получаю список фотографий...");

            JsonNode response = yandexDiskService.listPhotos();
            JsonNode items = response.get("_embedded").get("items");

            if (items == null || items.size() == 0) {
                sendMessage(chatId, "Папка пуста");
                return;
            }

            StringBuilder message = new StringBuilder("📁 Фотографии на Яндекс.Диске:\n\n");
            int count = 0;

            for (JsonNode item : items) {
                String name = item.get("name").asText();
                message.append("📷 ").append(name).append("\n");
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
            sendMessage(chatId, "❌ Ошибка при получении списка фотографий");
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
                sendPhoto.setCaption("📷 " + fileName);

                execute(sendPhoto);
            }

        } catch (Exception e) {
            logger.severe("Error getting photo: " + e.getMessage());
            sendMessage(chatId, "❌ Ошибка при получении фотографии. Проверьте имя файла.");
        }
    }

}
