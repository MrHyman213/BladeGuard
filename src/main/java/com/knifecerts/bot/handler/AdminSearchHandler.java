package com.knifecerts.bot.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.model.Brand;
import com.knifecerts.model.Knife;
import com.knifecerts.model.KnifeModel;
import com.knifecerts.repository.BrandRepository;
import com.knifecerts.repository.KnifeModelRepository;
import com.knifecerts.repository.KnifeRepository;

@Component
public class AdminSearchHandler {

    private static final Logger logger = Logger.getLogger(AdminSearchHandler.class.getName());
    private static final int ITEMS_PER_PAGE = 20;

    private final BrandRepository brandRepository;
    private final KnifeModelRepository knifeModelRepository;
    private final KnifeRepository knifeRepository;
    private final AdminBot adminBot;

    public AdminSearchHandler(BrandRepository brandRepository,
                            KnifeModelRepository knifeModelRepository,
                            KnifeRepository knifeRepository,
                            AdminBot adminBot) {
        this.brandRepository = brandRepository;
        this.knifeModelRepository = knifeModelRepository;
        this.knifeRepository = knifeRepository;
        this.adminBot = adminBot;
    }

    public void handleSearchRequest(Long chatId) {
        adminBot.sendMessage(chatId, "🔍 Введите название бренда для поиска:");
    }

    public void handleSearchPage(Long chatId, int page, String type, String searchQuery) {
        try {
            List<?> results;
            if ("brand".equals(type)) {
                results = new ArrayList<>(brandRepository.searchByName(searchQuery));
            } else {
                results = new ArrayList<>(knifeModelRepository.searchByName(searchQuery));
            }
            
            showSearchResults(chatId, results, type, page, searchQuery);
        } catch (Exception e) {
            logger.severe("Error handling search page: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }

    public void handleBrandSelect(Long chatId, String brandName) {
        try {
            List<KnifeModel> models = knifeModelRepository.findByBrand(brandName);
            if (models.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Моделей не найдено");
            } else {
                showBrandModels(chatId, brandName, new ArrayList<>(models), 0);
            }
        } catch (Exception e) {
            logger.severe("Error selecting brand: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при выборе бренда");
        }
    }

    public void handleModelsSearchRequest(Long chatId, String brandName) {
        adminBot.sendMessage(chatId, "🔍 Введите название модели для поиска:");
    }

    public void handleModelsSearchInput(Long chatId, String searchQuery, String brandName) {
        try {
            List<KnifeModel> allModels = knifeModelRepository.findByBrand(brandName);
            List<KnifeModel> filteredModels = allModels.stream()
                .filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .collect(Collectors.toList());
            
            if (filteredModels.isEmpty()) {
                adminBot.sendMessage(chatId, "❌ Моделей не найдено");
            } else {
                showSearchModelResults(chatId, brandName, filteredModels, 0, searchQuery);
            }
        } catch (Exception e) {
            logger.severe("Error handling search models input: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }

    public void handleBrandModelsPage(Long chatId, int page, String brandName) {
        try {
            List<KnifeModel> models = knifeModelRepository.findByBrand(brandName);
            showBrandModels(chatId, brandName, models, page);
        } catch (Exception e) {
            logger.severe("Error handling brand models page: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при отображении моделей");
        }
    }

    public void handleSearchModelsPage(Long chatId, int page, String brandName, String searchQuery) {
        try {
            List<KnifeModel> allModels = knifeModelRepository.findByBrand(brandName);
            List<KnifeModel> filteredModels = allModels.stream()
                .filter(m -> m.getName().toLowerCase().contains(searchQuery.toLowerCase()))
                .collect(Collectors.toList());
            
            showSearchModelResults(chatId, brandName, filteredModels, page, searchQuery);
        } catch (Exception e) {
            logger.severe("Error handling search models page: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при поиске");
        }
    }

    public void handleModelSelect(Long chatId, Long modelId) {
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
                
                adminBot.sendMessage(chatId, sb.toString());
            } else {
                adminBot.sendMessage(chatId, "❌ Модель не найдена");
            }
        } catch (Exception e) {
            logger.severe("Error selecting model: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при выборе модели");
        }
    }

    private void showBrandModels(Long chatId, String brandName, List<KnifeModel> models, int page) {
        try {
            int totalPages = (int) Math.ceil((double) models.size() / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, models.size());
            List<KnifeModel> pageModels = models.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🏷️ " + brandName + "\n\nВсего: " + models.size() + " моделей");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (KnifeModel model : pageModels) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                String name = model.getName();
                if (name.length() > 40) {
                    name = name.substring(0, 37) + "...";
                }
                
                row.add(InlineKeyboardButton.builder()
                    .text(name)
                    .callbackData("admin_search_model_" + model.getId())
                    .build());
                keyboard.add(row);
            }
            
            if (totalPages > 1) {
                keyboard.add(buildPaginationRow(page, totalPages, 
                    "admin_brand_models_page_", "_" + brandName));
            }
            
            List<InlineKeyboardButton> actionRow = new ArrayList<>();
            actionRow.add(InlineKeyboardButton.builder()
                .text("🔍 Поиск")
                .callbackData("admin_search_models_" + brandName)
                .build());
            keyboard.add(actionRow);
            
            keyboard.add(buildBackButton("admin_search_back"));
            keyboard.add(buildMenuButton());
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            adminBot.execute(message);
            
        } catch (Exception e) {
            logger.severe("Error showing brand models: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при отображении моделей");
        }
    }

    private void showSearchModelResults(Long chatId, String brandName, List<KnifeModel> models, int page, String searchQuery) {
        try {
            int totalPages = (int) Math.ceil((double) models.size() / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, models.size());
            List<KnifeModel> pageModels = models.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔍 Результаты поиска (" + models.size() + " найдено)");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (KnifeModel model : pageModels) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                String name = model.getName();
                if (name.length() > 40) {
                    name = name.substring(0, 37) + "...";
                }
                
                row.add(InlineKeyboardButton.builder()
                    .text(name)
                    .callbackData("admin_search_model_" + model.getId())
                    .build());
                keyboard.add(row);
            }
            
            if (totalPages > 1) {
                keyboard.add(buildPaginationRow(page, totalPages,
                    "admin_search_models_page_", "_" + brandName + "_" + searchQuery));
            }
            
            keyboard.add(buildBackButton("admin_search_back"));
            keyboard.add(buildMenuButton());
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            adminBot.execute(message);
            
        } catch (Exception e) {
            logger.severe("Error showing search model results: " + e.getMessage());
            adminBot.sendMessage(chatId, "❌ Ошибка при отображении результатов");
        }
    }

    private void showSearchResults(Long chatId, List<?> results, String type, int page, String searchQuery) {
        try {
            int totalPages = (int) Math.ceil((double) results.size() / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            
            page = ((page % totalPages) + totalPages) % totalPages;
            
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, results.size());
            List<?> pageResults = results.subList(startIndex, endIndex);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🔍 Результаты поиска (" + results.size() + " найдено)");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (Object result : pageResults) {
                List<InlineKeyboardButton> row = new ArrayList<>();
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
                
                if (name.length() > 40) {
                    name = name.substring(0, 37) + "...";
                }
                
                row.add(InlineKeyboardButton.builder()
                    .text(name)
                    .callbackData(callbackData)
                    .build());
                keyboard.add(row);
            }
            
            if (totalPages > 1) {
                keyboard.add(buildPaginationRow(page, totalPages,
                    "admin_search_page_", "_" + type + "_" + searchQuery));
            }
            
            keyboard.add(buildBackButton("admin_search_back"));
            keyboard.add(buildMenuButton());
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            adminBot.execute(message);
            
        } catch (Exception e) {
            logger.severe("Error showing search results: " + e.getMessage());
        }
    }

    private List<InlineKeyboardButton> buildPaginationRow(int page, int totalPages, String prefix, String suffix) {
        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        int prevPage = ((page - 1) % totalPages + totalPages) % totalPages;
        int nextPage = (page + 1) % totalPages;
        
        paginationRow.add(InlineKeyboardButton.builder()
            .text("⬅️")
            .callbackData(prefix + prevPage + suffix)
            .build());
        paginationRow.add(InlineKeyboardButton.builder()
            .text(String.format("%d/%d", page + 1, totalPages))
            .callbackData("admin_search_current_page")
            .build());
        paginationRow.add(InlineKeyboardButton.builder()
            .text("➡️")
            .callbackData(prefix + nextPage + suffix)
            .build());
        
        return paginationRow;
    }

    private List<InlineKeyboardButton> buildBackButton(String callbackData) {
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(InlineKeyboardButton.builder()
            .text("🔙 Назад")
            .callbackData(callbackData)
            .build());
        return backRow;
    }

    private List<InlineKeyboardButton> buildMenuButton() {
        List<InlineKeyboardButton> menuRow = new ArrayList<>();
        menuRow.add(InlineKeyboardButton.builder()
            .text("🔙 Главное меню")
            .callbackData("back_to_menu")
            .build());
        return menuRow;
    }
}
