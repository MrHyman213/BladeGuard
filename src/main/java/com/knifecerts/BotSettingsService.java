package com.knifecerts;

import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.knifecerts.model.BotSettings;
import com.knifecerts.repository.BotSettingsRepository;

@Service
@Transactional
public class BotSettingsService {
    
    private static final Logger logger = Logger.getLogger(BotSettingsService.class.getName());
    
    private static final String SEPARATOR_KEY = "alternative_separator";
    private static final String DEFAULT_SEPARATOR = "/";
    
    private final BotSettingsRepository botSettingsRepository;
    
    public BotSettingsService(BotSettingsRepository botSettingsRepository) {
        this.botSettingsRepository = botSettingsRepository;
    }
    
    public String getSeparator() {
        Optional<BotSettings> setting = botSettingsRepository.findBySettingKey(SEPARATOR_KEY);
        if (setting.isPresent()) {
            return setting.get().getSettingValue();
        }
        return DEFAULT_SEPARATOR;
    }
    
    public void setSeparator(String separator) {
        logger.info("Setting separator to: " + separator);
        
        Optional<BotSettings> settingOpt = botSettingsRepository.findBySettingKey(SEPARATOR_KEY);
        BotSettings setting;
        
        if (settingOpt.isPresent()) {
            setting = settingOpt.get();
            setting.setSettingValue(separator);
        } else {
            setting = new BotSettings();
            setting.setSettingKey(SEPARATOR_KEY);
            setting.setSettingValue(separator);
        }
        
        botSettingsRepository.save(setting);
        logger.info("Separator updated");
    }
}
