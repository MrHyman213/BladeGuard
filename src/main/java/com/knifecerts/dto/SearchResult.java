package com.knifecerts.dto;

import java.util.List;

import com.knifecerts.model.Submission;

/**
 * DTO для результата поиска сертификатов по названию ножа.
 */
public class SearchResult {
    
    private final List<Submission> submissions;
    private final boolean isPrimaryMatch;
    
    public SearchResult(List<Submission> submissions, boolean isPrimaryMatch) {
        this.submissions = submissions;
        this.isPrimaryMatch = isPrimaryMatch;
    }
    
    /**
     * Получить список найденных сертификатов.
     * 
     * @return список сертификатов
     */
    public List<Submission> getSubmissions() {
        return submissions;
    }
    
    /**
     * Проверить, является ли результат точным совпадением (основная модель).
     * 
     * @return true если найден сертификат именно на эту модель, 
     *         false если найдены только альтернативные варианты
     */
    public boolean isPrimaryMatch() {
        return isPrimaryMatch;
    }
    
    /**
     * Проверить, пуст ли результат поиска.
     * 
     * @return true если ничего не найдено
     */
    public boolean isEmpty() {
        return submissions == null || submissions.isEmpty();
    }
}
