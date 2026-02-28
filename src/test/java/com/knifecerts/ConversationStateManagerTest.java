package com.knifecerts;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.knifecerts.model.ConversationState;
import com.knifecerts.model.ConversationStep;

/**
 * Юнит-тесты для ConversationStateManager.
 * 
 * Проверяют основную функциональность управления состояниями диалогов:
 * - Создание нового диалога
 * - Получение состояния
 * - Обновление состояния
 * - Очистка состояния
 * - Проверка активности диалога
 */
class ConversationStateManagerTest {
    
    private ConversationStateManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new ConversationStateManager();
    }
    
    @Test
    void startConversation_shouldCreateNewStateWithWaitingForPhotoStep() {
        // Given
        Long userId = 12345L;
        
        // When
        manager.startConversation(userId);
        
        // Then
        ConversationState state = manager.getState(userId);
        assertThat(state).isNotNull();
        assertThat(state.getUserId()).isEqualTo(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_PHOTO);
        assertThat(state.getPhotoFileId()).isNull();
        assertThat(state.getModelName()).isNull();
        assertThat(state.getDescription()).isNull();
    }
    
    @Test
    void getState_shouldReturnNullWhenNoActiveConversation() {
        // Given
        Long userId = 12345L;
        
        // When
        ConversationState state = manager.getState(userId);
        
        // Then
        assertThat(state).isNull();
    }
    
    @Test
    void getState_shouldReturnExistingState() {
        // Given
        Long userId = 12345L;
        manager.startConversation(userId);
        
        // When
        ConversationState state = manager.getState(userId);
        
        // Then
        assertThat(state).isNotNull();
        assertThat(state.getUserId()).isEqualTo(userId);
    }
    
    @Test
    void updateState_shouldReplaceExistingState() {
        // Given
        Long userId = 12345L;
        manager.startConversation(userId);
        
        ConversationState updatedState = new ConversationState(userId, ConversationStep.WAITING_FOR_MODEL_NAME);
        updatedState.setPhotoFileId("file123");
        
        // When
        manager.updateState(userId, updatedState);
        
        // Then
        ConversationState state = manager.getState(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_MODEL_NAME);
        assertThat(state.getPhotoFileId()).isEqualTo("file123");
    }
    
    @Test
    void clearState_shouldRemoveStateFromManager() {
        // Given
        Long userId = 12345L;
        manager.startConversation(userId);
        assertThat(manager.hasActiveConversation(userId)).isTrue();
        
        // When
        manager.clearState(userId);
        
        // Then
        assertThat(manager.hasActiveConversation(userId)).isFalse();
        assertThat(manager.getState(userId)).isNull();
    }
    
    @Test
    void clearState_shouldBeIdempotent() {
        // Given
        Long userId = 12345L;
        
        // When - clearing state that doesn't exist
        manager.clearState(userId);
        
        // Then - should not throw exception
        assertThat(manager.hasActiveConversation(userId)).isFalse();
    }
    
    @Test
    void hasActiveConversation_shouldReturnFalseWhenNoConversation() {
        // Given
        Long userId = 12345L;
        
        // When
        boolean hasActive = manager.hasActiveConversation(userId);
        
        // Then
        assertThat(hasActive).isFalse();
    }
    
    @Test
    void hasActiveConversation_shouldReturnTrueWhenConversationExists() {
        // Given
        Long userId = 12345L;
        manager.startConversation(userId);
        
        // When
        boolean hasActive = manager.hasActiveConversation(userId);
        
        // Then
        assertThat(hasActive).isTrue();
    }
    
    @Test
    void shouldHandleMultipleUsersIndependently() {
        // Given
        Long userId1 = 111L;
        Long userId2 = 222L;
        
        // When
        manager.startConversation(userId1);
        manager.startConversation(userId2);
        
        ConversationState state1 = manager.getState(userId1);
        state1.setPhotoFileId("file111");
        manager.updateState(userId1, state1);
        
        ConversationState state2 = manager.getState(userId2);
        state2.setPhotoFileId("file222");
        manager.updateState(userId2, state2);
        
        // Then
        assertThat(manager.getState(userId1).getPhotoFileId()).isEqualTo("file111");
        assertThat(manager.getState(userId2).getPhotoFileId()).isEqualTo("file222");
        
        // Clear one user's state
        manager.clearState(userId1);
        assertThat(manager.hasActiveConversation(userId1)).isFalse();
        assertThat(manager.hasActiveConversation(userId2)).isTrue();
    }
    
    @Test
    void shouldHandleStateTransitionsThroughAllSteps() {
        // Given
        Long userId = 12345L;
        manager.startConversation(userId);
        
        // When - transition through all steps
        ConversationState state = manager.getState(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_PHOTO);
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_MODEL_NAME);
        state.setPhotoFileId("file123");
        manager.updateState(userId, state);
        
        state = manager.getState(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_MODEL_NAME);
        assertThat(state.getPhotoFileId()).isEqualTo("file123");
        
        state.setCurrentStep(ConversationStep.WAITING_FOR_DESCRIPTION);
        state.setModelName("Victorinox");
        manager.updateState(userId, state);
        
        state = manager.getState(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_DESCRIPTION);
        assertThat(state.getModelName()).isEqualTo("Victorinox");
        
        // Then - finalize by clearing
        manager.clearState(userId);
        assertThat(manager.hasActiveConversation(userId)).isFalse();
    }
    
    // ========== Property-Based Tests ==========
    
    /**
     * Property-Based Test: Свойство 26 - Создание Состояния Диалога
     * 
     * Feature: certificate-submission-system, Property 26: Создание Состояния Диалога
     * 
     * Validates: Requirements 11.1
     * 
     * Для любого пользователя, начинающего процесс подачи заявки, система должна создать 
     * состояние диалога с начальным шагом WAITING_FOR_PHOTO.
     */
    @net.jqwik.api.Property(tries = 100)
    void property_startConversation_shouldAlwaysCreateStateWithWaitingForPhotoStep(
            @net.jqwik.api.ForAll Long userId) {
        // Given
        ConversationStateManager manager = new ConversationStateManager();
        
        // When
        manager.startConversation(userId);
        
        // Then
        ConversationState state = manager.getState(userId);
        assertThat(state).isNotNull();
        assertThat(state.getUserId()).isEqualTo(userId);
        assertThat(state.getCurrentStep()).isEqualTo(ConversationStep.WAITING_FOR_PHOTO);
        assertThat(state.getPhotoFileId()).isNull();
        assertThat(state.getModelName()).isNull();
        assertThat(state.getDescription()).isNull();
        assertThat(manager.hasActiveConversation(userId)).isTrue();
    }
    
    /**
     * Property-Based Test: Свойство 12 - Очистка Состояния после Завершения
     * 
     * Feature: certificate-submission-system, Property 12: Очистка Состояния после Завершения
     * 
     * Validates: Requirements 6.3, 11.2
     * 
     * Для любого пользователя, после успешного завершения заявки или отмены, 
     * состояние диалога должно быть очищено.
     */
    @net.jqwik.api.Property(tries = 100)
    void property_clearState_shouldAlwaysRemoveConversationState(
            @net.jqwik.api.ForAll Long userId,
            @net.jqwik.api.ForAll String photoFileId,
            @net.jqwik.api.ForAll("modelNames") String modelName,
            @net.jqwik.api.ForAll("descriptions") String description) {
        // Given
        ConversationStateManager manager = new ConversationStateManager();
        manager.startConversation(userId);
        
        // Simulate conversation progress
        ConversationState state = manager.getState(userId);
        state.setPhotoFileId(photoFileId);
        state.setModelName(modelName);
        state.setDescription(description);
        state.setCurrentStep(ConversationStep.WAITING_FOR_DESCRIPTION);
        manager.updateState(userId, state);
        
        // Verify state exists before clearing
        assertThat(manager.hasActiveConversation(userId)).isTrue();
        
        // When - simulate completion or cancellation
        manager.clearState(userId);
        
        // Then - state should be completely removed
        assertThat(manager.hasActiveConversation(userId)).isFalse();
        assertThat(manager.getState(userId)).isNull();
    }
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> modelNames() {
        return net.jqwik.api.Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '-', '.')
            .ofMinLength(0)
            .ofMaxLength(100)
            .injectNull(0.3); // 30% chance of null to test optional field
    }
    
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> descriptions() {
        return net.jqwik.api.Arbitraries.strings()
            .ofMinLength(0)
            .ofMaxLength(500)
            .injectNull(0.3); // 30% chance of null to test optional field
    }
}
