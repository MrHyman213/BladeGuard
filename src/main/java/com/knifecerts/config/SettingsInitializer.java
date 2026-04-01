package com.knifecerts.config;

import com.knifecerts.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SettingsInitializer implements CommandLineRunner {

    private final SettingsService settingsService;

    @Autowired
    public SettingsInitializer(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void run(String... args) {
        if (settingsService.getSettingByKey("alternative_separator").isEmpty()) {
            settingsService.setAlternativeSeparator("/");
        }
    }
}