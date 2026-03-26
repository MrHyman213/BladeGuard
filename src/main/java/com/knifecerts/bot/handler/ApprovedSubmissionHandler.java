package com.knifecerts.bot.handler;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.manager.ModerationStateManager;
import com.knifecerts.bot.manager.ModerationStateManager.ModerationState;
import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;
import com.knifecerts.service.AlternativesParser;
import com.knifecerts.service.AlternativesParserImpl;
import com.knifecerts.service.KnifeService;
import com.knifecerts.service.TransitiveAlternativesService;
import com.knifecerts.service.YandexDiskService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class ApprovedSubmissionHandler {
    
    private static final Logger logger = Logger.getLogger(ApprovedSubmissionHandler.class.getName());
    
    private final AdminBot adminBot;
    private final KnifeService knifeService;
    private final KnifeRepository knifeRepository;
    private final BrandRepository brandRepository;
    private final KnifeModelRepository knifeModelRepository;
    private final ModerationStateManager moderationStateManager;
    private final YandexDiskService yandexDiskService;
    private final TransitiveAlternativesService transitiveAlternativesService;
    
    public ApprovedSubmissionHandler(
            AdminBot adminBot,
            KnifeService knifeService,
            KnifeRepository knifeRepository,
            BrandRepository brandRepository,
            KnifeModelRepository knifeModelRepository,
            ModerationStateManager moderationStateManager,
            YandexDiskService yandexDiskService,
            TransitiveAlternativesService transitiveAlternativesService) {
        this.adminBot = adminBot;
        this.knifeService = knifeService;
        this.knifeRepository = knifeRepository;
        this.brandRepository = brandRepository;
        this.knifeModelRepository = knifeModelRepository;
        this.moderationStateManager = moderationStateManager;
        this.yandexDiskService = yandexDiskService;
        this.transitiveAlternativesService = transitiveAlternativesService;
    }
    
    public void handleApprovedList(Long chatId, int page) {
        handleApprovedList(chatId, page, null);
    }
    
    public void handleApprovedList(Long chatId, int page, String searchQuery) {
        try {
            List<Knife> approvedKnives;
            
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
                
                adminBot.executeAndTrack(message);
                return;
            }
            
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
            
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                Knife knife = pageItems.get(i);
                String buttonText = knife.getDisplayName();
                
                if (buttonText.length() > 15) {
                    buttonText = buttonText.substring(0, 12) + "...";
                }
                
                currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("view_approved_" + knife.getId())
                    .build());
                
                if (currentRow.size() == 3) {
                    keyboard.add(currentRow);
                    currentRow = new ArrayList<>();
                }
            }
            
            if (!currentRow.isEmpty()) {
                keyboard.add(currentRow);
            }
            
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
            
            List<InlineKeyboardButton> menuRow = new ArrayList<>();
            menuRow.add(InlineKeyboardButton.builder()
                .text("🔙 Главное меню")
                .callbackData("back_to_menu")
                .build());
            keyboard.add(menuRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            adminBot.executeAndTrack(message);
            
        } catch (Exception e) {
            logger.severe("Ошибка при получении списка одобренных сертификатов: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при получении списка сертификатов");
        }
    }
    
    public void handleViewApproved(Long chatId, Long knifeId) {
        try {
            ModerationState existingState = moderationStateManager.getState(chatId);
            if (existingState != null && existingState.getFormMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(existingState.getFormMessageId());
                    adminBot.execute(deleteMsg);
                    logger.info("Deleted previous form message: " + existingState.getFormMessageId());
                } catch (Exception e) {
                    logger.info("Failed to delete previous form: " + e.getMessage());
                }
            }
            
            Optional<Knife> knifeOpt = knifeService.getKnifeById(knifeId);
            
            if (knifeOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Сертификат #" + knifeId + " не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            moderationStateManager.createState(chatId, knife, true);
            sendApprovedForm(chatId, knifeId);
            
        } catch (Exception e) {
            logger.severe("Ошибка при просмотре сертификата: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при просмотре сертификата");
        }
    }
    
    private void sendApprovedForm(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            Knife knife = (Knife) state.getOriginal();
            
            StringBuilder caption = new StringBuilder();
            caption.append("✅ Одобренный сертификат\n\n");
            caption.append("ID: #").append(knife.getId()).append("\n");
            caption.append("Статус: Одобрен\n");
            
            if (knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty()) {
                try {
                    String photoUrl = yandexDiskService.getDownloadUrl(knife.getPhotoPath());
                    SendPhoto photoMessage = new SendPhoto();
                    photoMessage.setChatId(chatId.toString());
                    photoMessage.setPhoto(new InputFile(photoUrl));
                    photoMessage.setCaption(caption.toString());
                    photoMessage.setReplyMarkup(buildApprovedKeyboard(knifeId, state));
                    
                    Message sentMessage = adminBot.execute(photoMessage);
                    state.setFormMessageId(sentMessage.getMessageId());
                    
                } catch (Exception e) {
                    logger.warning("Не удалось загрузить фото: " + e.getMessage());
                    SendMessage textMessage = new SendMessage();
                    textMessage.setChatId(chatId.toString());
                    textMessage.setText(caption.toString() + "\n\n⚠️ Не удалось загрузить фото");
                    textMessage.setReplyMarkup(buildApprovedKeyboard(knifeId, state));
                    
                    Message sentMessage = adminBot.execute(textMessage);
                    state.setFormMessageId(sentMessage.getMessageId());
                }
            } else {
                SendMessage textMessage = new SendMessage();
                textMessage.setChatId(chatId.toString());
                textMessage.setText(caption.toString() + "\n\n⚠️ Фото отсутствует");
                textMessage.setReplyMarkup(buildApprovedKeyboard(knifeId, state));
                
                Message sentMessage = adminBot.execute(textMessage);
                state.setFormMessageId(sentMessage.getMessageId());
            }
            
        } catch (Exception e) {
            logger.severe("Ошибка при отправке формы сертификата: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при отображении сертификата");
        }
    }
    
    private InlineKeyboardMarkup buildApprovedKeyboard(Long knifeId, ModerationState state) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        Knife knife = (Knife) state.getOriginal();
        
        List<InlineKeyboardButton> photoRow = new ArrayList<>();
        if (knife.getPhotoPath() != null && !knife.getPhotoPath().isEmpty()) {
            String photoPathText = knife.getPhotoPath();
            if (photoPathText.length() > 40) {
                photoPathText = "..." + photoPathText.substring(photoPathText.length() - 37);
            }
            photoRow.add(InlineKeyboardButton.builder()
                .text("📷 " + photoPathText)
                .callbackData("approved_photo_" + knifeId)
                .build());
        } else {
            photoRow.add(InlineKeyboardButton.builder()
                .text("📷 Установить фото сертификата")
                .callbackData("approved_photo_" + knifeId)
                .build());
        }
        keyboard.add(photoRow);
        
        String nameText = "🔪 Название: " + (state.getName() != null ? state.getName() : "не указано");
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
            .text(nameText)
            .callbackData("mod_edit_name_" + knifeId)
            .build());
        keyboard.add(row1);
        
        String brandText = "🏷️ Бренд: " + (state.getBrand() != null ? state.getBrand() : "не указан");
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
            .text(brandText)
            .callbackData("mod_edit_brand_" + knifeId)
            .build());
        keyboard.add(row2);
        
        String indexText = "🔢 Индекс: " + (state.getIndexCode() != null ? state.getIndexCode() : "не указан");
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
            .text(indexText)
            .callbackData("mod_edit_index_" + knifeId)
            .build());
        keyboard.add(row3);
        
        List<String> altModels = state.getAlternativeModels();
        String altText = "🔄 Альтернативные: " + (altModels != null && !altModels.isEmpty() ? altModels.size() + " шт." : "нет");
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(InlineKeyboardButton.builder()
            .text(altText)
            .callbackData("mod_edit_alt_" + knifeId)
            .build());
        keyboard.add(row4);
        
        if (state.hasChanges()) {
            List<InlineKeyboardButton> row5 = new ArrayList<>();
            row5.add(InlineKeyboardButton.builder()
                .text("💾 Сохранить изменения")
                .callbackData("approved_save_" + knifeId)
                .build());
            keyboard.add(row5);
        }
        
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(InlineKeyboardButton.builder()
            .text("🗑️ Удалить сертификат")
            .callbackData("approved_delete_" + knifeId)
            .build());
        keyboard.add(row6);
        
        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(InlineKeyboardButton.builder()
            .text("❌ Закрыть")
            .callbackData("approved_cancel_" + knifeId)
            .build());
        keyboard.add(row7);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    public void handleSave(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            adminBot.sendMessage(chatId, "❌ Состояние не найдено");
            return;
        }
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ Вы уверены, что хотите сохранить изменения?");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(InlineKeyboardButton.builder()
            .text("✅ Да, сохранить")
            .callbackData("approved_save_confirm_" + knifeId)
            .build());
        row.add(InlineKeyboardButton.builder()
            .text("❌ Нет")
            .callbackData("approved_save_no_" + knifeId)
            .build());
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            Message sent = adminBot.execute(message);
            state.setPromptMessageId(sent.getMessageId());
        } catch (Exception e) {
            logger.severe("Error sending save confirmation: " + e.getMessage());
        }
    }
    
    public void handleSaveConfirm(Long chatId, Long knifeId) {
        try {
            ModerationState state = moderationStateManager.getState(chatId);
            if (state == null) {
                adminBot.sendMessage(chatId, "❌ Состояние не найдено");
                return;
            }
            
            if (state.getPromptMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getPromptMessageId());
                    adminBot.execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete prompt: " + e.getMessage());
                }
            }
            
            if (state.getFormMessageId() != null) {
                try {
                    DeleteMessage deleteMsg = new DeleteMessage();
                    deleteMsg.setChatId(chatId.toString());
                    deleteMsg.setMessageId(state.getFormMessageId());
                    adminBot.execute(deleteMsg);
                } catch (Exception e) {
                    logger.warning("Failed to delete form: " + e.getMessage());
                }
            }
            
            Knife knife = (Knife) state.getOriginal();
            
            if (state.getName() != null && !state.getName().isEmpty()) {
                knife.getModel().setName(state.getName());
            }
            if (state.getBrand() != null && !state.getBrand().isEmpty()) {
                knife.getBrand().setName(state.getBrand());
            }
            if (state.getIndexCode() != null && !state.getIndexCode().isEmpty()) {
                knife.setIndex(state.getIndexCode());
            }
            
            Set<Long> newAlternativeIds = new HashSet<>();
            if (state.getAlternativeModels() != null && !state.getAlternativeModels().isEmpty()) {
                AlternativesParser parser = new AlternativesParserImpl("/");
                
                for (String altStr : state.getAlternativeModels()) {
                    List<AlternativeEntry> entries = parser.parse(altStr);
                    for (AlternativeEntry entry : entries) {
                        Brand altBrand = findOrCreateBrand(entry.brand());
                        KnifeModel altModel = findOrCreateKnifeModel(entry.name());
                        
                        Optional<Knife> existingKnife = knifeRepository.findByModelAndBrand(altModel, altBrand);
                        Knife altKnife;
                        
                        if (existingKnife.isPresent()) {
                            altKnife = existingKnife.get();
                        } else {
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
            
            knifeRepository.save(knife);
            
            if (!newAlternativeIds.isEmpty()) {
                Set<Knife> transitiveAlternatives = transitiveAlternativesService.findTransitive(knifeId, newAlternativeIds);
                
                if (!transitiveAlternatives.isEmpty()) {
                    state.setTransitiveAlternativeIds(transitiveAlternatives.stream()
                        .map(Knife::getId)
                        .collect(Collectors.toSet()));
                    
                    adminBot.showTransitiveAlternativesProposal(chatId, knifeId, transitiveAlternatives);
                    return;
                }
            }
            
            adminBot.sendMessage(chatId, "✅ Изменения сохранены!");
            moderationStateManager.removeState(chatId);
            handleApprovedList(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Ошибка при сохранении изменений: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при сохранении изменений: " + e.getMessage());
        }
    }
    
    public void handleSaveNo(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                adminBot.execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
    }
    
    public void handleDelete(Long chatId, Long knifeId) {
        try {
            Optional<Knife> knifeOpt = knifeService.getKnifeById(knifeId);
            
            if (knifeOpt.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Сертификат не найден");
                return;
            }
            
            Knife knife = knifeOpt.get();
            
            try {
                if (knife.getPhotoPath() != null) {
                    yandexDiskService.deleteFile(knife.getPhotoPath());
                    logger.info("Файл удален с Яндекс.Диска: " + knife.getPhotoPath());
                }
            } catch (Exception e) {
                logger.warning("Не удалось удалить файл с Яндекс.Диска: " + e.getMessage());
            }
            
            adminBot.sendMessage(chatId, "⚠️ Удаление сертификатов пока не реализовано в новой схеме");
            
            moderationStateManager.removeState(chatId);
            handleApprovedList(chatId, 0);
            
        } catch (Exception e) {
            logger.severe("Ошибка при удалении сертификата: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при удалении сертификата");
        }
    }
    
    public void handleCancel(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.hasChanges()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❓ Вы уверены, что хотите закрыть редактирование?\n\nИзменения не будут сохранены.");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder()
                .text("✅ Да, закрыть")
                .callbackData("approved_cancel_confirm_" + knifeId)
                .build());
            row.add(InlineKeyboardButton.builder()
                .text("❌ Нет")
                .callbackData("approved_cancel_no_" + knifeId)
                .build());
            keyboard.add(row);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            try {
                Message sent = adminBot.execute(message);
                state.setPromptMessageId(sent.getMessageId());
            } catch (Exception e) {
                logger.severe("Error sending cancel confirmation: " + e.getMessage());
            }
        } else {
            handleCancelConfirm(chatId, knifeId);
        }
    }
    
    public void handleCancelConfirm(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        
        if (state != null && state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                adminBot.execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
        
        if (state != null && state.getFormMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getFormMessageId());
                adminBot.execute(deleteMsg);
            } catch (Exception e) {
                logger.warning("Failed to delete form: " + e.getMessage());
            }
        }
        
        moderationStateManager.removeState(chatId);
    }
    
    public void handleCancelNo(Long chatId, Long knifeId) {
        ModerationState state = moderationStateManager.getState(chatId);
        if (state == null) {
            return;
        }
        
        if (state.getPromptMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(state.getPromptMessageId());
                adminBot.execute(deleteMsg);
                state.setPromptMessageId(null);
            } catch (Exception e) {
                logger.warning("Failed to delete prompt: " + e.getMessage());
            }
        }
    }
    
    private Brand findOrCreateBrand(String name) {
        Optional<Brand> existing = brandRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Brand brand = new Brand();
        brand.setName(name);
        return brandRepository.save(brand);
    }
    
    private KnifeModel findOrCreateKnifeModel(String name) {
        Optional<KnifeModel> existing = knifeModelRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        KnifeModel model = new KnifeModel();
        model.setName(name);
        return knifeModelRepository.save(model);
    }
}
