package com.knifecerts.model;

/**
 * Enum representing the current step in the user submission conversation flow.
 * 
 * Validates: Requirements 11.1, 11.3
 */
public enum ConversationStep {
    /**
     * Initial step - waiting for the user to submit a photo
     */
    WAITING_FOR_PHOTO,
    
    /**
     * Second step - waiting for the user to provide model name or skip
     */
    WAITING_FOR_MODEL_NAME,
    
    /**
     * Third step - waiting for the user to provide description or skip
     */
    WAITING_FOR_DESCRIPTION,
    
    /**
     * Fourth step - waiting for the user to provide alternative models or skip
     */
    WAITING_FOR_ALTERNATIVE_MODELS,
    
    /**
     * Form mode - waiting for model name input
     */
    FORM_WAITING_MODEL,
    
    /**
     * Form mode - waiting for description input
     */
    FORM_WAITING_DESC,
    
    /**
     * Form mode - waiting for alternative models input
     */
    FORM_WAITING_ALT
}
