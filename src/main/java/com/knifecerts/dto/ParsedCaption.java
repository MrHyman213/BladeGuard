package com.knifecerts.dto;

/**
 * Результат парсинга caption фото.
 * Содержит распарсенные поля: бренд, название и индекс.
 * Все поля могут быть null.
 */
public record ParsedCaption(
    String brand,   // null если не указан
    String name,    // null если не указан
    String index    // null если не указан
) {
    /**
     * Проверяет, является ли результат пустым (все поля null).
     */
    public boolean isEmpty() {
        return brand == null && name == null && index == null;
    }
}
