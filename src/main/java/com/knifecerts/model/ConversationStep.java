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
     * Second step - waiting for the user to provide name or skip
     */
    WAITING_FOR_NAME,
    
    /**
     * Third step - waiting for the user to provide brand or skip
     */
    WAITING_FOR_BRAND,
    
    /**
     * Fourth step - waiting for the user to provide index or skip
     */
    WAITING_FOR_INDEX,
    
    /**
     * Fifth step - waiting for the user to provide alternative models or skip
     */
    WAITING_FOR_ALTERNATIVE_MODELS,
    
    /**
     * Form mode - waiting for name input
     */
    FORM_WAITING_NAME,
    
    /**
     * Form mode - waiting for brand input
     */
    FORM_WAITING_BRAND,
    
    /**
     * Form mode - waiting for index input
     */
    FORM_WAITING_INDEX,
    
    /**
     * Form mode - waiting for alternative models input
     */
    FORM_WAITING_ALT,
    
    /**
     * Waiting for search query input
     */
    WAITING_FOR_SEARCH_QUERY
}
