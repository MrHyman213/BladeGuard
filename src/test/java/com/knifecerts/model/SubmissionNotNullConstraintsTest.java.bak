package com.knifecerts.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.hibernate.PropertyValueException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import jakarta.validation.ConstraintViolationException;

/**
 * Тесты для проверки ограничений NOT NULL в Submission entity.
 * 
 * Проверяет, что база данных отклоняет попытки создать записи
 * с null значениями в обязательных полях.
 * 
 * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
 * **Validates: Requirements 12.2, 12.4**
 */
@DataJpaTest
@ActiveProfiles("test")
class SubmissionNotNullConstraintsTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    /**
     * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
     * 
     * Для любой попытки создать запись заявки с null userId,
     * база данных должна отклонить операцию.
     * 
     * **Validates: Requirements 12.2**
     */
    @Test
    void submissionWithNullUserIdShouldBeRejectedByDatabase() {
        // Создаем заявку с null userId
        Submission submission = new Submission();
        submission.setUserId(null);
        submission.setUsername("testuser");
        submission.setPhotoPath("app:/offers/photo.jpg");
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(java.time.LocalDateTime.now());
        
        // Попытка сохранить должна выбросить исключение
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(submission);
        })
        .as("База данных должна отклонить заявку с null userId")
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class, PropertyValueException.class);
    }
    
    /**
     * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
     * 
     * Для любой попытки создать запись заявки с null photoPath,
     * база данных должна отклонить операцию.
     * 
     * **Validates: Requirements 12.4**
     */
    @Test
    void submissionWithNullPhotoPathShouldBeRejectedByDatabase() {
        // Создаем заявку с null photoPath
        Submission submission = new Submission();
        submission.setUserId(12345L);
        submission.setUsername("testuser");
        submission.setPhotoPath(null);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(java.time.LocalDateTime.now());
        
        // Попытка сохранить должна выбросить исключение
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(submission);
        })
        .as("База данных должна отклонить заявку с null photoPath")
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class, PropertyValueException.class);
    }
    
    /**
     * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
     * 
     * Для любой попытки создать запись заявки с null username,
     * база данных должна отклонить операцию.
     * 
     * **Validates: Requirements 12.2**
     */
    @Test
    void submissionWithNullUsernameShouldBeRejectedByDatabase() {
        // Создаем заявку с null username
        Submission submission = new Submission();
        submission.setUserId(12345L);
        submission.setUsername(null);
        submission.setPhotoPath("app:/offers/photo.jpg");
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(java.time.LocalDateTime.now());
        
        // Попытка сохранить должна выбросить исключение
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(submission);
        })
        .as("База данных должна отклонить заявку с null username")
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class, PropertyValueException.class);
    }
    
    /**
     * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
     * 
     * Для любой попытки создать запись заявки с null status,
     * база данных должна отклонить операцию.
     * 
     * **Validates: Requirements 12.3**
     */
    @Test
    void submissionWithNullStatusShouldBeRejectedByDatabase() {
        // Создаем заявку с null status
        Submission submission = new Submission();
        submission.setUserId(12345L);
        submission.setUsername("testuser");
        submission.setPhotoPath("app:/offers/photo.jpg");
        submission.setStatus(null);
        submission.setCreatedAt(java.time.LocalDateTime.now());
        
        // Попытка сохранить должна выбросить исключение
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(submission);
        })
        .as("База данных должна отклонить заявку с null status")
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class, PropertyValueException.class);
    }
    
    /**
     * Feature: certificate-submission-system, Property 29: Ограничения NOT NULL
     * 
     * Для любой попытки создать запись заявки с null createdAt,
     * база данных должна отклонить операцию.
     * 
     * **Validates: Requirements 12.5**
     */
    @Test
    void submissionWithNullCreatedAtShouldBeRejectedByDatabase() {
        // Создаем заявку с null createdAt
        Submission submission = new Submission();
        submission.setUserId(12345L);
        submission.setUsername("testuser");
        submission.setPhotoPath("app:/offers/photo.jpg");
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(null);
        
        // Попытка сохранить должна выбросить исключение
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(submission);
        })
        .as("База данных должна отклонить заявку с null createdAt")
        .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class, PropertyValueException.class);
    }
}
