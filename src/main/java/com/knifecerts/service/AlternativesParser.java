package com.knifecerts.service;

import java.util.List;

import com.knifecerts.dto.AlternativeEntry;

/**
 * Парсит строку множественных альтернатив через запятую.
 * Каждый элемент может быть в формате:
 * - Бренд{sep}Название (2 части)
 * - Название (1 часть)
 * 
 * Разделитель читается из настройки pattern (по умолчанию "/").
 */
public interface AlternativesParser {
    /**
     * Парсит строку множественных альтернатив.
     * 
     * @param input строка альтернатив через запятую (может быть null или пустая)
     * @return список распарсенных альтернатив, или пустой список если input пуст
     */
    List<AlternativeEntry> parse(String input);
}
