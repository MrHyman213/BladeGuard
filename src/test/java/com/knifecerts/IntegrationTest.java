package com.knifecerts;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;
import com.knifecerts.repository.SubmissionRepository;

/**
 * Интеграционные тесты для полного потока системы подачи сертификатов.
 * 
 * Проверяют взаимодействие между компонентами системы:
 * - Создание заявки
 * - Модерация (одобрение и отклонение)
 * - Взаимодействие с Yandex.Disk
 * 
 * Требования: 1.1, 2.1, 4.1, 5.1, 6.1, 9.1, 10.1
 */
public class IntegrationTest {
    
    private SubmissionRepository submissionRepository;
    private YandexDiskService yandexDiskService;
    private SubmissionService submissionService;
    
    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        yandexDiskService = mock(YandexDiskService.class);
        submissionService = new SubmissionService(submissionRepository, yandexDiskService);
    }
    
    /**
     * Тест полного потока подачи заявки: создание заявки.
     * 
     * Проверяет, что заявка создается с правильными данными и статусом PENDING.
     */
    @Test
    void testFullSubmissionFlow_Creation() {
        // Arrange
        Long userId = 12345L;
        String username = "testuser";
        String modelName = "Victorinox Swiss Army";
        String description = "Классический швейцарский нож";
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission mockSubmission = new Submission(userId, username, modelName, description, photoPath);
        mockSubmission.setId(1L);
        
        when(submissionRepository.save(any(Submission.class))).thenReturn(mockSubmission);
        
        // Act
        Submission result = submissionService.createSubmission(userId, username, photoPath, modelName, description, null);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getUsername()).isEqualTo(username);
        assertThat(result.getModelName()).isEqualTo(modelName);
        assertThat(result.getDescription()).isEqualTo(description);
        assertThat(result.getPhotoPath()).isEqualTo(photoPath);
        assertThat(result.getModeratedAt()).isNull();
        assertThat(result.getModeratedBy()).isNull();
        
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }
    
    /**
     * Тест полного потока модерации: одобрение заявки.
     * 
     * Проверяет, что при одобрении:
     * - Файл перемещается из offers в certificates
     * - Статус обновляется на APPROVED
     * - Поля модерации заполняются
     */
    @Test
    void testFullModerationFlow_Approval() throws Exception {
        // Arrange
        Long submissionId = 1L;
        Long moderatorId = 999L;
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission submission = new Submission(12345L, "testuser", "Model", "Description", photoPath);
        submission.setId(submissionId);
        
        when(submissionRepository.findById(submissionId)).thenReturn(java.util.Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        Submission result = submissionService.approveSubmission(submissionId, moderatorId);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(result.getModeratedAt()).isNotNull();
        assertThat(result.getModeratedBy()).isEqualTo(moderatorId);
        assertThat(result.getPhotoPath()).contains("certificates");
        
        verify(yandexDiskService, times(1)).moveFile(anyString(), anyString());
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }
    
    /**
     * Тест полного потока модерации: отклонение заявки.
     * 
     * Проверяет, что при отклонении:
     * - Файл удаляется из offers
     * - Статус обновляется на REJECTED
     * - Поля модерации заполняются
     */
    @Test
    void testFullModerationFlow_Rejection() throws Exception {
        // Arrange
        Long submissionId = 1L;
        Long moderatorId = 999L;
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission submission = new Submission(12345L, "testuser", "Model", "Description", photoPath);
        submission.setId(submissionId);
        
        when(submissionRepository.findById(submissionId)).thenReturn(java.util.Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        Submission result = submissionService.rejectSubmission(submissionId, moderatorId);
        
        // Assert
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(result.getModeratedAt()).isNotNull();
        assertThat(result.getModeratedBy()).isEqualTo(moderatorId);
        
        verify(yandexDiskService, times(1)).deleteFile(photoPath);
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }
    
    /**
     * Тест полного потока: создание заявки с опциональными полями null.
     * 
     * Проверяет, что система корректно обрабатывает заявки без названия модели и описания.
     */
    @Test
    void testFullSubmissionFlow_WithNullOptionalFields() {
        // Arrange
        Long userId = 12345L;
        String username = "testuser";
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission mockSubmission = new Submission(userId, username, null, null, photoPath);
        mockSubmission.setId(1L);
        
        when(submissionRepository.save(any(Submission.class))).thenReturn(mockSubmission);
        
        // Act
        Submission result = submissionService.createSubmission(userId, username, photoPath, null, null, null);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getModelName()).isNull();
        assertThat(result.getDescription()).isNull();
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }
    
    /**
     * Тест полного потока: попытка модерации уже проверенной заявки.
     * 
     * Проверяет, что система отклоняет попытки повторной модерации.
     */
    @Test
    void testFullModerationFlow_AlreadyModerated() throws Exception {
        // Arrange
        Long submissionId = 1L;
        Long moderatorId = 999L;
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission submission = new Submission(12345L, "testuser", "Model", "Description", photoPath);
        submission.setId(submissionId);
        submission.setStatus(SubmissionStatus.APPROVED); // Уже одобрена
        
        when(submissionRepository.findById(submissionId)).thenReturn(java.util.Optional.of(submission));
        
        // Act & Assert
        try {
            submissionService.approveSubmission(submissionId, moderatorId);
            assertThat(false).isTrue(); // Не должно дойти сюда
        } catch (SubmissionException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBMISSION_ALREADY_MODERATED);
        }
    }
    
    /**
     * Тест полного потока: обработка ошибки Yandex.Disk при одобрении.
     * 
     * Проверяет, что при ошибке перемещения файла выбрасывается исключение
     * и транзакция откатывается.
     */
    @Test
    void testFullModerationFlow_YandexDiskError() throws Exception {
        // Arrange
        Long submissionId = 1L;
        Long moderatorId = 999L;
        String photoPath = "app:/offers/photo_20240226_143022_abc123.jpg";
        
        Submission submission = new Submission(12345L, "testuser", "Model", "Description", photoPath);
        submission.setId(submissionId);
        
        when(submissionRepository.findById(submissionId)).thenReturn(java.util.Optional.of(submission));
        
        try {
            org.mockito.Mockito.doThrow(new IOException("Network error"))
                    .when(yandexDiskService).moveFile(anyString(), anyString());
        } catch (IOException e) {
            // Настройка мока
        }
        
        // Act & Assert
        try {
            submissionService.approveSubmission(submissionId, moderatorId);
            assertThat(false).isTrue(); // Не должно дойти сюда
        } catch (SubmissionException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.YANDEX_DISK_MOVE_FAILED);
        }
        
        // Проверяем, что save не был вызван (транзакция откатилась)
        verify(submissionRepository, times(0)).save(any(Submission.class));
    }
}
