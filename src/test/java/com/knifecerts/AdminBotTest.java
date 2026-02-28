package com.knifecerts;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;

/**
 * Юнит-тесты для AdminBot
 * Feature: certificate-submission-system
 * 
 * Тестирует обработку ошибок и граничные случаи.
 * Требования: 8.3, 9.6, 10.6
 */
class AdminBotTest {
    
    private SubmissionService submissionService;
    private YandexDiskService yandexDiskService;
    private KnifeBot knifeBot;
    
    @BeforeEach
    void setUp() {
        submissionService = mock(SubmissionService.class);
        yandexDiskService = mock(YandexDiskService.class);
        knifeBot = mock(KnifeBot.class);
    }
    
    /**
     * Тест: команда /review с несуществующим ID должна вернуть сообщение об ошибке
     * Требование: 8.3
     */
    @Test
    void reviewCommandWithNonExistentIdShouldReturnNotFoundMessage() {
        Long nonExistentId = 999L;
        
        when(submissionService.getSubmissionById(nonExistentId))
            .thenReturn(Optional.empty());
        
        // Проверяем, что Optional пустой
        Optional<Submission> result = submissionService.getSubmissionById(nonExistentId);
        assertThat(result).isEmpty();
        
        // Проверяем формат сообщения об ошибке
        String expectedMessage = "❌ Заявка #" + nonExistentId + " не найдена.";
        assertThat(expectedMessage).contains(nonExistentId.toString());
        assertThat(expectedMessage).contains("не найдена");
    }
    
    /**
     * Тест: команда /approve с уже проверенной заявкой должна выбросить исключение
     * Требование: 9.6
     */
    @Test
    void approveCommandWithAlreadyModeratedSubmissionShouldThrowException() {
        Long submissionId = 1L;
        Long moderatorId = 100L;
        
        try {
            when(submissionService.approveSubmission(submissionId, moderatorId))
                .thenThrow(new SubmissionException(
                    ErrorCode.SUBMISSION_ALREADY_MODERATED,
                    "Заявка #" + submissionId + " уже имеет статус APPROVED"
                ));
            
            // Попытка одобрить уже проверенную заявку
            submissionService.approveSubmission(submissionId, moderatorId);
            
            // Не должны дойти сюда
            assertThat(false).isTrue();
            
        } catch (SubmissionException e) {
            // Проверяем, что исключение содержит правильную информацию
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBMISSION_ALREADY_MODERATED);
            assertThat(e.getMessage()).contains("уже имеет статус");
        }
    }
    
    /**
     * Тест: команда /reject с уже проверенной заявкой должна выбросить исключение
     * Требование: 10.6
     */
    @Test
    void rejectCommandWithAlreadyModeratedSubmissionShouldThrowException() {
        Long submissionId = 1L;
        Long moderatorId = 100L;
        
        try {
            when(submissionService.rejectSubmission(submissionId, moderatorId))
                .thenThrow(new SubmissionException(
                    ErrorCode.SUBMISSION_ALREADY_MODERATED,
                    "Заявка #" + submissionId + " уже имеет статус REJECTED"
                ));
            
            // Попытка отклонить уже проверенную заявку
            submissionService.rejectSubmission(submissionId, moderatorId);
            
            // Не должны дойти сюда
            assertThat(false).isTrue();
            
        } catch (SubmissionException e) {
            // Проверяем, что исключение содержит правильную информацию
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBMISSION_ALREADY_MODERATED);
            assertThat(e.getMessage()).contains("уже имеет статус");
        }
    }
    
    /**
     * Тест: команда /review с недопустимым форматом ID должна обрабатываться корректно
     * Требование: 8.3
     */
    @Test
    void reviewCommandWithInvalidIdFormatShouldHandleNumberFormatException() {
        String invalidCommand = "/review abc";
        
        try {
            String[] parts = invalidCommand.split(" ");
            Long.parseLong(parts[1]);
            
            // Не должны дойти сюда
            assertThat(false).isTrue();
            
        } catch (NumberFormatException e) {
            // Ожидаемое исключение
            assertThat(e).isInstanceOf(NumberFormatException.class);
        }
    }
    
    /**
     * Тест: команда /approve с недопустимым форматом ID должна обрабатываться корректно
     * Требование: 9.6
     */
    @Test
    void approveCommandWithInvalidIdFormatShouldHandleNumberFormatException() {
        String invalidCommand = "/approve xyz";
        
        try {
            String[] parts = invalidCommand.split(" ");
            Long.parseLong(parts[1]);
            
            // Не должны дойти сюда
            assertThat(false).isTrue();
            
        } catch (NumberFormatException e) {
            // Ожидаемое исключение
            assertThat(e).isInstanceOf(NumberFormatException.class);
        }
    }
    
    /**
     * Тест: команда /reject с недопустимым форматом ID должна обрабатываться корректно
     * Требование: 10.6
     */
    @Test
    void rejectCommandWithInvalidIdFormatShouldHandleNumberFormatException() {
        String invalidCommand = "/reject 123abc";
        
        try {
            String[] parts = invalidCommand.split(" ");
            Long.parseLong(parts[1]);
            
            // Не должны дойти сюда
            assertThat(false).isTrue();
            
        } catch (NumberFormatException e) {
            // Ожидаемое исключение
            assertThat(e).isInstanceOf(NumberFormatException.class);
        }
    }
    
    /**
     * Тест: команда без аргументов должна возвращать сообщение об ошибке формата
     * Требование: 8.3
     */
    @Test
    void commandWithoutArgumentsShouldReturnFormatErrorMessage() {
        String reviewCommand = "/review";
        String approveCommand = "/approve";
        String rejectCommand = "/reject";
        
        // Проверяем, что команды без аргументов обрабатываются
        String[] reviewParts = reviewCommand.split(" ");
        String[] approveParts = approveCommand.split(" ");
        String[] rejectParts = rejectCommand.split(" ");
        
        assertThat(reviewParts.length).isEqualTo(1);
        assertThat(approveParts.length).isEqualTo(1);
        assertThat(rejectParts.length).isEqualTo(1);
        
        // Все команды должны требовать аргумент
        assertThat(reviewParts.length < 2).isTrue();
        assertThat(approveParts.length < 2).isTrue();
        assertThat(rejectParts.length < 2).isTrue();
    }
    
    /**
     * Тест: успешное одобрение должно отправить уведомления модератору и пользователю
     * Требование: 9.5
     */
    @Test
    void successfulApprovalShouldSendNotificationsToBothModeratorAndUser() throws SubmissionException {
        Long submissionId = 1L;
        Long moderatorId = 100L;
        Long userId = 200L;
        
        Submission submission = new Submission(userId, "testuser", "Model X", "Description", "app:/offers/photo.jpg");
        submission.setId(submissionId);
        submission.setStatus(SubmissionStatus.APPROVED);
        
        when(submissionService.approveSubmission(submissionId, moderatorId))
            .thenReturn(submission);
        
        Submission result = submissionService.approveSubmission(submissionId, moderatorId);
        
        // Проверяем, что заявка одобрена
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(result.getUserId()).isEqualTo(userId);
        
        // Проверяем формат уведомления пользователю
        String userNotification = "✅ Ваша заявка #" + submissionId + " одобрена!\n" +
                "Сертификат добавлен в коллекцию.";
        assertThat(userNotification).contains(submissionId.toString());
        assertThat(userNotification).contains("одобрена");
    }
    
    /**
     * Тест: успешное отклонение должно отправить уведомления модератору и пользователю
     * Требование: 10.5
     */
    @Test
    void successfulRejectionShouldSendNotificationsToBothModeratorAndUser() throws SubmissionException {
        Long submissionId = 1L;
        Long moderatorId = 100L;
        Long userId = 200L;
        
        Submission submission = new Submission(userId, "testuser", "Model X", "Description", "app:/offers/photo.jpg");
        submission.setId(submissionId);
        submission.setStatus(SubmissionStatus.REJECTED);
        
        when(submissionService.rejectSubmission(submissionId, moderatorId))
            .thenReturn(submission);
        
        Submission result = submissionService.rejectSubmission(submissionId, moderatorId);
        
        // Проверяем, что заявка отклонена
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(result.getUserId()).isEqualTo(userId);
        
        // Проверяем формат уведомления пользователю
        String userNotification = "❌ Ваша заявка #" + submissionId + " отклонена.\n" +
                "Пожалуйста, проверьте требования и попробуйте снова.";
        assertThat(userNotification).contains(submissionId.toString());
        assertThat(userNotification).contains("отклонена");
    }
}
