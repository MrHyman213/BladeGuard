package com.knifecerts;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.ModerationSession;
import com.knifecerts.repository.ModerationSessionRepository;

/**
 * Сервис для управления сессиями модерации.
 * 
 * Создает токены для доступа к Web App редактору,
 * валидирует токены и управляет жизненным циклом сессий.
 */
@Service
@Transactional
public class ModerationSessionManager {
    
    private static final Logger logger = Logger.getLogger(ModerationSessionManager.class.getName());
    private static final int SESSION_EXPIRY_HOURS = 1;
    
    private final ModerationSessionRepository sessionRepository;
    
    public ModerationSessionManager(ModerationSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }
    
    /**
     * Создает новую сессию модерации.
     * 
     * @param submissionId ID заявки
     * @param moderatorId ID модератора
     * @return созданная сессия с уникальным токеном
     */
    public ModerationSession createSession(Long submissionId, Long moderatorId) {
        ModerationSession session = new ModerationSession();
        session.setToken(UUID.randomUUID().toString());
        session.setSubmissionId(submissionId);
        session.setModeratorId(moderatorId);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS));
        session.setUsed(false);
        
        ModerationSession saved = sessionRepository.save(session);
        logger.info("Создана сессия модерации: token=" + saved.getToken() + 
                   ", submissionId=" + submissionId);
        
        return saved;
    }
    
    /**
     * Валидирует токен сессии.
     * 
     * Проверяет существование, срок действия и использование сессии.
     * 
     * @param token токен сессии
     * @return Optional с сессией, если токен валиден
     */
    public Optional<ModerationSession> validateToken(String token) {
        Optional<ModerationSession> sessionOpt = sessionRepository.findByToken(token);
        
        if (sessionOpt.isEmpty()) {
            logger.warning("Токен не найден: " + token);
            return Optional.empty();
        }
        
        ModerationSession session = sessionOpt.get();
        
        // Проверить срок действия
        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            logger.warning("Токен истек: " + token);
            return Optional.empty();
        }
        
        // Проверить использование
        if (session.isUsed()) {
            logger.warning("Токен уже использован: " + token);
            return Optional.empty();
        }
        
        return Optional.of(session);
    }
    
    /**
     * Помечает сессию как использованную.
     * 
     * @param token токен сессии
     */
    public void markAsUsed(String token) {
        sessionRepository.findByToken(token).ifPresent(session -> {
            session.setUsed(true);
            sessionRepository.save(session);
            logger.info("Сессия помечена как использованная: " + token);
        });
    }
    
    /**
     * Автоматическая очистка истекших сессий.
     * Запускается каждый час.
     */
    @Scheduled(fixedRate = 3600000) // 1 час в миллисекундах
    public void cleanupExpiredSessions() {
        List<ModerationSession> expiredSessions = 
            sessionRepository.findByExpiresAtBefore(LocalDateTime.now());
        
        if (!expiredSessions.isEmpty()) {
            sessionRepository.deleteAll(expiredSessions);
            logger.info("Удалено истекших сессий: " + expiredSessions.size());
        }
    }
}
