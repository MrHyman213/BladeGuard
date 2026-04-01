package com.knifecerts.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.knifecerts.dto.AlternativeEntry;

/**
 * Реализация парсера множественных альтернатив.
 * Разделитель читается из базы данных через SettingsService (по умолчанию "/").
 */
@Service
public class AlternativesParserImpl implements AlternativesParser {

    private final SettingsService settingsService;

    public AlternativesParserImpl(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
    
    @Override
    public List<AlternativeEntry> parse(String input) {
        List<AlternativeEntry> result = new ArrayList<>();

        // Null или пустая строка → пустой список
        if (input == null || input.trim().isEmpty())
            return result;

        String separator = settingsService.getAlternativeSeparator();

        // Разбиваем по запятой
        String[] elements = input.split(",");
        for (String element : elements) {
            String trimmed = element.trim();

            if (trimmed.isEmpty())
                continue;

            // Разбиваем каждый элемент по разделителю (с учетом пробелов вокруг)
            // Используем Pattern.quote для корректной обработки специальных символов
            String[] parts = trimmed.split(Pattern.quote(separator));

            // Trim каждой части
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }

            // Обработка по количеству частей
            if (parts.length == 2) {
                // Бренд / Название
                result.add(new AlternativeEntry(parts[0], parts[1]));
            } else if (parts.length == 1) {
                // Только Название
                result.add(new AlternativeEntry(null, parts[0]));
            }
            // Иначе пропускаем некорректный элемент
        }

        return result;
    }
}
