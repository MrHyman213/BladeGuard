package com.knifecerts.service;

import com.knifecerts.dto.ParsedCaption;

/**
 * Парсит caption фото в три формата:
 * - Бренд{sep}Название{sep}Индекс (3 части)
 * - Бренд{sep}Название (2 части)
 * - Название (1 часть)
 * 
 * Разделитель читается из настройки pattern (по умолчанию "/").
 */
public interface CaptionParser {
    /**
     * Парсит caption в структурированный формат.
     * 
     * @param caption текст caption (может быть null)
     * @return ParsedCaption с распарсенными полями, или пустой результат если caption не соответствует формату
     */
    ParsedCaption parse(String caption);
    
    /**
     * Форматирует ParsedCaption обратно в строку для round-trip тестирования.
     * 
     * @param parsed распарсенный caption
     * @return отформатированная строка
     */
    String format(ParsedCaption parsed);
}
