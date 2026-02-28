package com.knifecerts.repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;

/**
 * Интеграционные тесты для SubmissionRepository.
 * 
 * Проверяет корректность работы методов запросов репозитория
 * с использованием встроенной тестовой базы данных.
 * 
 * Требования: 5.1, 7.1
 */
@DataJpaTest
@ActiveProfiles("test")
class SubmissionRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @BeforeEach
    void setUp() {
        // Очистка базы данных перед каждым тестом
        submissionRepository.deleteAll();
    }
    
    @Test
    void findByStatusOrderByCreatedAtAsc_shouldReturnSubmissionsInCreationOrder() {
        // Given: Создаем три заявки с разными временными метками
        Submission submission1 = new Submission(100L, "user1", "Model A", "Desc A", "path1.jpg");
        Submission submission2 = new Submission(200L, "user2", "Model B", "Desc B", "path2.jpg");
        Submission submission3 = new Submission(300L, "user3", "Model C", "Desc C", "path3.jpg");
        
        entityManager.persist(submission1);
        entityManager.flush();
        
        // Небольшая задержка для обеспечения разных временных меток
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        entityManager.persist(submission2);
        entityManager.flush();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        entityManager.persist(submission3);
        entityManager.flush();
        
        // When: Получаем все PENDING заявки
        List<Submission> pendingSubmissions = submissionRepository.findByStatusOrderByCreatedAtAsc(SubmissionStatus.PENDING);
        
        // Then: Заявки должны быть отсортированы по дате создания
        assertThat(pendingSubmissions).hasSize(3);
        assertThat(pendingSubmissions.get(0).getUserId()).isEqualTo(100L);
        assertThat(pendingSubmissions.get(1).getUserId()).isEqualTo(200L);
        assertThat(pendingSubmissions.get(2).getUserId()).isEqualTo(300L);
    }
    
    @Test
    void findByStatusOrderByCreatedAtAsc_shouldFilterByStatus() {
        // Given: Создаем заявки с разными статусами
        Submission pending = new Submission(100L, "user1", "Model A", "Desc A", "path1.jpg");
        Submission approved = new Submission(200L, "user2", "Model B", "Desc B", "path2.jpg");
        approved.setStatus(SubmissionStatus.APPROVED);
        Submission rejected = new Submission(300L, "user3", "Model C", "Desc C", "path3.jpg");
        rejected.setStatus(SubmissionStatus.REJECTED);
        
        entityManager.persist(pending);
        entityManager.persist(approved);
        entityManager.persist(rejected);
        entityManager.flush();
        
        // When: Получаем только PENDING заявки
        List<Submission> pendingSubmissions = submissionRepository.findByStatusOrderByCreatedAtAsc(SubmissionStatus.PENDING);
        
        // Then: Должна вернуться только одна PENDING заявка
        assertThat(pendingSubmissions).hasSize(1);
        assertThat(pendingSubmissions.get(0).getUserId()).isEqualTo(100L);
        assertThat(pendingSubmissions.get(0).getStatus()).isEqualTo(SubmissionStatus.PENDING);
    }
    
    @Test
    void findByUserId_shouldReturnAllSubmissionsForUser() {
        // Given: Создаем несколько заявок для одного пользователя и одну для другого
        Submission submission1 = new Submission(100L, "user1", "Model A", "Desc A", "path1.jpg");
        Submission submission2 = new Submission(100L, "user1", "Model B", "Desc B", "path2.jpg");
        Submission submission3 = new Submission(200L, "user2", "Model C", "Desc C", "path3.jpg");
        
        entityManager.persist(submission1);
        entityManager.persist(submission2);
        entityManager.persist(submission3);
        entityManager.flush();
        
        // When: Получаем заявки для пользователя 100
        List<Submission> userSubmissions = submissionRepository.findByUserId(100L);
        
        // Then: Должны вернуться две заявки пользователя 100
        assertThat(userSubmissions).hasSize(2);
        assertThat(userSubmissions).allMatch(s -> s.getUserId().equals(100L));
    }
    
    @Test
    void findByUserId_shouldReturnEmptyListForNonExistentUser() {
        // Given: База данных пуста
        
        // When: Получаем заявки для несуществующего пользователя
        List<Submission> userSubmissions = submissionRepository.findByUserId(999L);
        
        // Then: Должен вернуться пустой список
        assertThat(userSubmissions).isEmpty();
    }
    
    @Test
    void countByStatus_shouldReturnCorrectCount() {
        // Given: Создаем заявки с разными статусами
        Submission pending1 = new Submission(100L, "user1", "Model A", "Desc A", "path1.jpg");
        Submission pending2 = new Submission(200L, "user2", "Model B", "Desc B", "path2.jpg");
        Submission approved = new Submission(300L, "user3", "Model C", "Desc C", "path3.jpg");
        approved.setStatus(SubmissionStatus.APPROVED);
        
        entityManager.persist(pending1);
        entityManager.persist(pending2);
        entityManager.persist(approved);
        entityManager.flush();
        
        // When: Подсчитываем заявки по статусам
        long pendingCount = submissionRepository.countByStatus(SubmissionStatus.PENDING);
        long approvedCount = submissionRepository.countByStatus(SubmissionStatus.APPROVED);
        long rejectedCount = submissionRepository.countByStatus(SubmissionStatus.REJECTED);
        
        // Then: Количество должно соответствовать созданным заявкам
        assertThat(pendingCount).isEqualTo(2);
        assertThat(approvedCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(0);
    }
    
    @Test
    void countByStatus_shouldReturnZeroForEmptyDatabase() {
        // Given: База данных пуста
        
        // When: Подсчитываем PENDING заявки
        long count = submissionRepository.countByStatus(SubmissionStatus.PENDING);
        
        // Then: Должен вернуться 0
        assertThat(count).isEqualTo(0);
    }
}
