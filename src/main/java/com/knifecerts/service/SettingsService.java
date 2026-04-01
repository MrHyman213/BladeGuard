package com.knifecerts.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.Settings;
import com.knifecerts.repository.SettingsRepository;

@Service
@Transactional(readOnly = true)
public class SettingsService {

    private static final String ALTERNATIVE_SEPARATOR_KEY = "alternative_separator";
    private static final String DEFAULT_SEPARATOR = "/";

    private final SettingsRepository settingsRepository;

    @Autowired
    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public String getAlternativeSeparator() {
        return settingsRepository.findByKey(ALTERNATIVE_SEPARATOR_KEY)
                .map(Settings::getValue)
                .orElse(DEFAULT_SEPARATOR);
    }

    @Transactional
    public void setAlternativeSeparator(String separator) {
        Settings settings = settingsRepository.findByKey(ALTERNATIVE_SEPARATOR_KEY)
                .orElse(new Settings(ALTERNATIVE_SEPARATOR_KEY, separator, "Разделитель между колонками в альтернативах (бренд/название)"));
        settings.setValue(separator);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);
    }

    public Optional<Settings> getSettingByKey(String key) {
        return settingsRepository.findByKey(key);
    }

    @Transactional
    public void setSetting(String key, String value, String description) {
        Settings settings = settingsRepository.findByKey(key)
                .orElse(new Settings(key, value, description));
        settings.setValue(value);
        settings.setDescription(description);
        settings.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(settings);
    }
}
