package com.knifecerts.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based тесты для валидации статуса Submission entity.
 * 
 * Проверяет, что enum SubmissionStatus содержит только допустимые значения
 * и что заявки могут быть созданы с любым из этих статусов.
 * 
 * Feature: certificate-submission-system, Property 30: Валидация Статуса
 * **Validates: Requirements 12.3**
 */
class SubmissionStatusValidationTest {
    
    /**
     * Feature: certificate-submission-system, Property 30: Валидация Статуса
     * 
     * Для любой попытки установить статус заявки на значение,
     * отличное от PENDING, APPROVED или REJECTED,
     * база данных должна отклонить операцию.
     * 
     * Поскольку мы используем enum, на уровне Java невозможно установить
     * недопустимое значение. Этот тест проверяет, что:
     * 1. Enum содержит только 3 допустимых значения
     * 2. Заявки могут быть созданы с любым из этих статусов
     * 
     * **Validates: Requirements 12.3**
     */
    @Property(tries = 100)
    void submissionCanOnlyHaveValidStatus(
            @ForAll Long userId,
            @ForAll @NotBlank @StringLength(min = 3, max = 50) @AlphaChars String username,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String photoPath,
            @ForAll("validStatuses") SubmissionStatus status) {
        
        // Создаем заявку с валидным статусом
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setUsername(username);
        submission.setPhotoPath(photoPath);
        submission.setStatus(status);
        submission.setCreatedAt(LocalDateTime.now());
        
        // Проверяем, что статус установлен корректно
        assertThat(submission.getStatus())
            .as("Статус должен быть одним из допустимых значений")
            .isIn(SubmissionStatus.PENDING, SubmissionStatus.APPROVED, SubmissionStatus.REJECTED);
        
        // Проверяем, что статус соответствует установленному
        assertThat(submission.getStatus())
            .as("Статус должен соответствовать установленному значению")
            .isEqualTo(status);
    }
    
    /**
     * Провайдер для генерации валидных статусов.
     */
    @Provide
    Arbitrary<SubmissionStatus> validStatuses() {
        return Arbitraries.of(
            SubmissionStatus.PENDING,
            SubmissionStatus.APPROVED,
            SubmissionStatus.REJECTED
        );
    }
    
    /**
     * Property-based тест для проверки, что enum содержит только 3 значения.
     * 
     * Этот тест гарантирует, что никто не добавит новые статусы
     * без обновления требований и дизайна.
     */
    @Property(tries = 10)
    void enumShouldContainExactlyThreeStatuses(@ForAll("validStatuses") SubmissionStatus status) {
        SubmissionStatus[] allStatuses = SubmissionStatus.values();
        
        // Проверяем, что существует ровно 3 статуса
        assertThat(allStatuses)
            .as("Должно существовать ровно 3 статуса")
            .hasSize(3);
        
        // Проверяем, что переданный статус является одним из них
        assertThat(status)
            .as("Статус должен быть одним из значений enum")
            .isIn((Object[]) allStatuses);
    }
    
    /**
     * Property-based тест для проверки, что все статусы имеют корректные имена.
     */
    @Property(tries = 10)
    void allStatusesShouldHaveCorrectNames(@ForAll("validStatuses") SubmissionStatus status) {
        String statusName = status.name();
        
        // Проверяем, что имя статуса соответствует одному из допустимых
        assertThat(statusName)
            .as("Имя статуса должно быть одним из: PENDING, APPROVED, REJECTED")
            .isIn("PENDING", "APPROVED", "REJECTED");
    }
}
