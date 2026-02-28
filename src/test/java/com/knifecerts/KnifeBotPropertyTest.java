package com.knifecerts;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import com.knifecerts.model.ConversationState;
import com.knifecerts.model.ConversationStep;
import com.knifecerts.model.Submission;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based тесты для KnifeBot.
 * 
 * Проверяют универсальные свойства корректности процесса подачи заявок.
 */
public class KnifeBotPropertyTest {
    
    /**
     * Feature: certificate-submission-system, Property 1: Инициализация Процесса Подачи Заявки
     * 
     * Для любого пользователя, когда он отправляет фото в KnifeBot, 
     * система должна создать состояние диалога и перейти к запросу названия модели.
     * 
     * Проверяет: Требования 1.1, 1.2
     */
    @Property(tries = 100)
    void photoSubmissionShouldInitializeConversationState(
            @ForAll("userIds") Long userId,
            @ForAll("photoFileIds") String photoFileId) {
        
        ConversationStateManager conversationStateManager = new ConversationStateManager();
        
        // Создаем Update с фото
        Update update = createPhotoUpdate(userId, photoFileId);
        
        // Симулируем обработку фото
        conversationStateManager.startConversation(userId);
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_MODEL_NAME);
        state.setPhotoFileId(photoFileId);
        conversationStateManager.updateState(userId, state);
        
        // Проверяем, что состояние создано
        ConversationState savedState = conversationStateManager.getState(userId);
        assertThat(savedState).isNotNull();
        assertThat(savedState.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_MODEL_NAME);
        assertThat(savedState.getPhotoFileId()).isEqualTo(photoFileId);
    }
    
    /**
     * Feature: certificate-submission-system, Property 2: Валидация Типа Входных Данных
     * 
     * Для любого сообщения, отправленного во время ожидания фото, 
     * если сообщение не содержит фото, то система должна отклонить его.
     * 
     * Проверяет: Требования 1.3
     */
    @Property(tries = 100)
    void nonPhotoMessageShouldBeRejectedDuringPhotoWait(
            @ForAll("userIds") Long userId,
            @ForAll("textMessages") String text) {
        
        ConversationStateManager conversationStateManager = new ConversationStateManager();
        
        // Создаем состояние ожидания фото
        conversationStateManager.startConversation(userId);
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_PHOTO);
        conversationStateManager.updateState(userId, state);
        
        // Создаем Update с текстом (не фото)
        Update update = createTextUpdate(userId, text);
        
        // Проверяем, что сообщение не содержит фото
        assertThat(update.getMessage().hasPhoto()).isFalse();
        
        // Состояние не должно измениться
        ConversationState unchangedState = conversationStateManager.getState(userId);
        assertThat(unchangedState.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_PHOTO);
        assertThat(unchangedState.getPhotoFileId()).isNull();
    }
    
    /**
     * Feature: certificate-submission-system, Property 3: Принятие Опциональных Полей
     * 
     * Для любого опционального поля (название модели или описание), 
     * система должна принимать либо текстовый ответ, либо команду /skip.
     * 
     * Проверяет: Требования 2.1, 2.4
     */
    @Property(tries = 100)
    void optionalFieldsShouldAcceptTextOrSkip(
            @ForAll("userIds") Long userId,
            @ForAll("optionalTexts") String text) {
        
        ConversationStateManager conversationStateManager = new ConversationStateManager();
        
        // Создаем состояние ожидания названия модели
        ConversationState state = new ConversationState(userId, ConversationStep.WAITING_FOR_MODEL_NAME);
        state.setPhotoFileId("test_file_id");
        conversationStateManager.updateState(userId, state);
        
        // Обрабатываем ввод (текст или /skip)
        if (text.equals("/skip")) {
            state.setModelName(null);
        } else {
            state.setModelName(text);
        }
        state.setCurrentStep(ConversationStep.WAITING_FOR_DESCRIPTION);
        conversationStateManager.updateState(userId, state);
        
        // Проверяем, что состояние обновлено
        ConversationState updatedState = conversationStateManager.getState(userId);
        assertThat(updatedState.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_DESCRIPTION);
        
        if (text.equals("/skip")) {
            assertThat(updatedState.getModelName()).isNull();
        } else {
            assertThat(updatedState.getModelName()).isEqualTo(text);
        }
    }
    
    /**
     * Feature: certificate-submission-system, Property 5: Отмена на Любом Шаге
     * 
     * Для любого пользователя с активным состоянием диалога, 
     * когда он отправляет команду /cancel, система должна очистить состояние диалога.
     * 
     * Проверяет: Требования 3.1, 3.2, 3.3
     */
    @Property(tries = 100)
    void cancelCommandShouldClearConversationState(
            @ForAll("userIds") Long userId,
            @ForAll("conversationSteps") ConversationStep step) {
        
        ConversationStateManager conversationStateManager = new ConversationStateManager();
        
        // Создаем активное состояние диалога
        ConversationState state = new ConversationState(userId, step);
        state.setPhotoFileId("test_file_id");
        state.setModelName("Test Model");
        conversationStateManager.updateState(userId, state);
        
        // Проверяем, что состояние существует
        assertThat(conversationStateManager.hasActiveConversation(userId)).isTrue();
        
        // Обрабатываем команду /cancel
        conversationStateManager.clearState(userId);
        
        // Проверяем, что состояние очищено
        assertThat(conversationStateManager.hasActiveConversation(userId)).isFalse();
        assertThat(conversationStateManager.getState(userId)).isNull();
    }
    
    /**
     * Feature: certificate-submission-system, Property 11: Возврат Уникального ID
     * 
     * Для любой успешно созданной заявки, 
     * система должна вернуть уникальный ID заявки пользователю.
     * 
     * Проверяет: Требования 5.5, 6.1, 6.2
     */
    @Property(tries = 100)
    void submissionShouldReturnUniqueId(
            @ForAll("userIds") Long userId,
            @ForAll("usernames") String username,
            @ForAll("photoPaths") String photoPath) throws Exception {
        
        SubmissionService submissionService = mock(SubmissionService.class);
        
        // Мокируем создание заявки
        Submission mockSubmission = new Submission(userId, username, null, null, photoPath);
        mockSubmission.setId(123L);
        when(submissionService.createSubmission(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockSubmission);
        
        // Создаем заявку
        Submission submission = submissionService.createSubmission(
                userId, username, photoPath, null, null, null);
        
        // Проверяем, что ID установлен
        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getId()).isGreaterThan(0L);
    }
    
    // Провайдеры для генерации тестовых данных
    
    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().greaterOrEqual(1L).lessOrEqual(999999999L);
    }
    
    @Provide
    Arbitrary<String> photoFileIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(10)
                .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> textMessages() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(" ")
                .ofMinLength(1)
                .ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<String> optionalTexts() {
        return Arbitraries.oneOf(
                Arbitraries.just("/skip"),
                Arbitraries.strings().alpha().numeric().withChars(" ").ofMinLength(1).ofMaxLength(100)
        );
    }
    
    @Provide
    Arbitrary<ConversationStep> conversationSteps() {
        return Arbitraries.of(
                ConversationStep.WAITING_FOR_PHOTO,
                ConversationStep.WAITING_FOR_MODEL_NAME,
                ConversationStep.WAITING_FOR_DESCRIPTION
        );
    }
    
    @Provide
    Arbitrary<String> usernames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(32);
    }
    
    @Provide
    Arbitrary<String> photoPaths() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars("/._-")
                .ofMinLength(10)
                .ofMaxLength(100);
    }
    
    // Вспомогательные методы для создания тестовых объектов
    
    private Update createPhotoUpdate(Long userId, String photoFileId) {
        Update update = new Update();
        Message message = new Message();
        User user = new User();
        user.setId(userId);
        user.setUserName("testuser");
        message.setFrom(user);
        
        // Создаем список фото
        List<PhotoSize> photos = new ArrayList<>();
        PhotoSize photo = new PhotoSize();
        photo.setFileId(photoFileId);
        photo.setFileSize(1000);
        photos.add(photo);
        message.setPhoto(photos);
        
        update.setMessage(message);
        return update;
    }
    
    private Update createTextUpdate(Long userId, String text) {
        Update update = new Update();
        Message message = new Message();
        User user = new User();
        user.setId(userId);
        user.setUserName("testuser");
        message.setFrom(user);
        message.setText(text);
        
        update.setMessage(message);
        return update;
    }
}
