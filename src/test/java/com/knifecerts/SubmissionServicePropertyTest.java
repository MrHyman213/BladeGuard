package com.knifecerts;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.SubmissionRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для SubmissionService
 * Feature: certificate-submission-system
 */
class SubmissionServicePropertyTest {
    
    /**
     * Feature: certificate-submission-system, Property 10: Полнота Данных Заявки
     * **Validates: Requirements 5.3**
     * 
     * Для любой созданной записи заявки, все обязательные поля (user_id, username, 
     * photo_path, status, created_at) должны быть заполнены, а опциональные поля 
     * (model_name, description) могут быть null.
     */
    @Property(tries = 100)
    void createdSubmissionShouldHaveAllRequiredFieldsPopulated(
            @ForAll("userIds") Long userId,
            @ForAll("usernames") String username,
            @ForAll("modelNames") String modelName,
            @ForAll("descriptions") String description,
            @ForAll("photoPaths") String photoPath) {
        
        // Создаем моки для каждого теста
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        YandexDiskService yandexDiskService = mock(YandexDiskService.class);
        SubmissionService submissionService = new SubmissionService(submissionRepository, yandexDiskService);
        
        // Настраиваем мок для возврата заявки с ID
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(1L);
            return submission;
        });
        
        // Создаем заявку
        Submission result = submissionService.createSubmission(
            userId, username, photoPath, modelName, description, null);
        
        // Проверяем обязательные поля
        assertThat(result.getUserId()).isNotNull().isEqualTo(userId);
        assertThat(result.getUsername()).isNotNull().isEqualTo(username);
        assertThat(result.getPhotoPath()).isNotNull().isEqualTo(photoPath);
        assertThat(result.getStatus()).isNotNull().isEqualTo(SubmissionStatus.PENDING);
        assertThat(result.getCreatedAt()).isNotNull();
        
        // Опциональные поля могут быть null или иметь значение
        if (modelName != null) {
            assertThat(result.getModelName()).isEqualTo(modelName);
        }
        if (description != null) {
            assertThat(result.getDescription()).isEqualTo(description);
        }
        
        // Поля модерации должны быть null для новой заявки
        assertThat(result.getModeratedAt()).isNull();
        assertThat(result.getModeratedBy()).isNull();
    }
    
    /**
     * Feature: certificate-submission-system, Property 13: Фильтрация Ожидающих Заявок
     * **Validates: Requirements 7.1**
     * 
     * Для любого запроса списка ожидающих заявок, система должна возвращать только 
     * заявки со статусом PENDING, отсортированные по дате создания.
     */
    @Property(tries = 100)
    void getPendingSubmissionsShouldReturnOnlyPendingSubmissionsOrderedByCreatedAt(
            @ForAll("submissionLists") List<Submission> allSubmissions) {
        
        // Создаем моки для каждого теста
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        YandexDiskService yandexDiskService = mock(YandexDiskService.class);
        SubmissionService submissionService = new SubmissionService(submissionRepository, yandexDiskService);
        
        // Фильтруем только PENDING заявки и сортируем по дате создания
        List<Submission> expectedPending = allSubmissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.PENDING)
                .sorted((s1, s2) -> s1.getCreatedAt().compareTo(s2.getCreatedAt()))
                .toList();
        
        // Настраиваем мок
        when(submissionRepository.findByStatusOrderByCreatedAtAsc(SubmissionStatus.PENDING))
                .thenReturn(expectedPending);
        
        // Получаем ожидающие заявки
        List<Submission> result = submissionService.getPendingSubmissions();
        
        // Проверяем, что все заявки имеют статус PENDING
        assertThat(result).allMatch(s -> s.getStatus() == SubmissionStatus.PENDING);
        
        // Проверяем сортировку по дате создания (по возрастанию)
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt())
                    .isBeforeOrEqualTo(result.get(i + 1).getCreatedAt());
        }
        
        // Проверяем, что результат соответствует ожидаемому
        assertThat(result).isEqualTo(expectedPending);
    }
    
    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().greaterOrEqual(1L);
    }
    
    @Provide
    Arbitrary<String> usernames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> modelNames() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(100)
                .injectNull(0.3); // 30% вероятность null
    }
    
    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(500)
                .injectNull(0.3); // 30% вероятность null
    }
    
    @Provide
    Arbitrary<String> photoPaths() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('/', '.', '_', '-')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
    
    @Provide
    Arbitrary<List<Submission>> submissionLists() {
        return Arbitraries.of(SubmissionStatus.values())
                .flatMap(status -> {
                    return Arbitraries.longs().greaterOrEqual(1L)
                            .flatMap(userId -> {
                                return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                                        .flatMap(username -> {
                                            return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                                                    .map(photoPath -> {
                                                        Submission submission = new Submission(
                                                                userId, username, null, null, photoPath);
                                                        submission.setId(userId);
                                                        submission.setStatus(status);
                                                        return submission;
                                                    });
                                        });
                            });
                })
                .list()
                .ofMinSize(0)
                .ofMaxSize(20);
    }
}
