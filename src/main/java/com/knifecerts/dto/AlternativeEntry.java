package com.knifecerts.dto;

/**
 * Запись об альтернативе, распарсенная из строки множественных альтернатив.
 * Содержит бренд (опционально) и название модели.
 */
public record AlternativeEntry(
    String brand,   // null если не указан
    String name     // никогда не null
) {
}
