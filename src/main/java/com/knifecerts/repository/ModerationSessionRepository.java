package com.knifecerts.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.ModerationSession;

/**
 * Repository для работы с сессиями модерации.
 */
@Repository
public interface ModerationSessionRepository extends JpaRepository<ModerationSession, Long> {
    
    /**
     * Найти сессию по токену.
     */
    Optional<ModerationSession> findByToken(String token);
    
    /**
     * Найти все истекшие сессии.
     */
    List<ModerationSession> findByExpiresAtBefore(LocalDateTime dateTime);
}
