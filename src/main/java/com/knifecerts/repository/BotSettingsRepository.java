package com.knifecerts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.BotSettings;

@Repository
public interface BotSettingsRepository extends JpaRepository<BotSettings, Long> {
    
    Optional<BotSettings> findBySettingKey(String settingKey);
}
