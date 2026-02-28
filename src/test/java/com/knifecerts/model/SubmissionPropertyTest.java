package com.knifecerts.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based тесты для Submission entity.
 * 
 * Использует jqwik для проверки универсальных свойств корректности
 * на множестве сгенерированных входных данных.
 * 
 * Feature: certificate-submission-system
 */
class SubmissionPropertyTest {
    
    /**
     * Feature: certificate-submission-system, Property 9: Инварианты Новой Заявки
     * 
     * Для любой созданной записи заявки, статус должен быть PENDING,
     * поля moderated_at и moderated_by должны быть null,
     * а created_at должен быть установлен автоматически.
     * 
     * **Validates: Requirements 5.2, 5.4, 12.5**
     */
    @Property(tries = 100)
    void newSubmissionShouldHavePendingStatusAndNullModerationFields(
            @ForAll Long userId,
            @ForAll @NotBlank @StringLength(min = 3, max = 50) @AlphaChars String username,
            @ForAll @StringLength(max = 100) String modelName,
            @ForAll @StringLength(max = 500) String description,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String photoPath) {
        
        // Создаем новую заявку через конструктор
        Submission submission = new Submission(userId, username, modelName, description, photoPath);
        
        // Проверяем инварианты новой заявки
        assertThat(submission.getStatus())
                .as("Новая заявка должна иметь статус PENDING")
                .isEqualTo(SubmissionStatus.PENDING);
        
        assertThat(submission.getModeratedAt())
                .as("Поле moderated_at должно быть null для новой заявки")
                .isNull();
        
        assertThat(submission.getModeratedBy())
                .as("Поле moderated_by должно быть null для новой заявки")
                .isNull();
        
        assertThat(submission.getCreatedAt())
                .as("Поле created_at должно быть установлено автоматически")
                .isNotNull();
    }
}
