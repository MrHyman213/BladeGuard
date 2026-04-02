package com.knifecerts.service;

import com.knifecerts.model.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.knifecerts.dto.ParsedCaption;

@Service
public class CaptionParserImpl implements CaptionParser {

    @Autowired
    private final SettingsService settingsService;

    public CaptionParserImpl(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public ParsedCaption parse(String caption) {
        if (caption == null || caption.trim().isEmpty()) {
            return new ParsedCaption(null, null, null);
        }
        
        // Разбиваем по разделителю
        String[] parts = caption.split(settingsService.getAlternativeSeparator());
        
        // Trim каждой части
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        
        // Обработка по количеству частей
        if (parts.length == 3) {
            // Бренд / Название / Индекс
            return new ParsedCaption(parts[0], parts[1], parts[2]);
        } else if (parts.length == 2) {
            // Бренд / Название
            return new ParsedCaption(parts[0], parts[1], null);
        } else if (parts.length == 1) {
            // Только Название
            return new ParsedCaption(null, parts[0], null);
        } else {
            // Иначе → пустой результат
            return new ParsedCaption(null, null, null);
        }
    }
    
    @Override
    public String format(ParsedCaption parsed) {
        if (parsed == null || parsed.isEmpty()) {
            return "";
        }
        String separator = settingsService.getAlternativeSeparator();
        // Форматируем в зависимости от того, какие поля заполнены
        if (parsed.brand() != null && parsed.name() != null && parsed.index() != null) {
            return parsed.brand() + separator + parsed.name() + separator + parsed.index();
        } else if (parsed.brand() != null && parsed.name() != null) {
            return parsed.brand() + separator + parsed.name();
        } else if (parsed.name() != null) {
            return parsed.name();
        } else {
            return "";
        }
    }
}
