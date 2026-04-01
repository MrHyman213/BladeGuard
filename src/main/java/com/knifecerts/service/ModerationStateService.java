package com.knifecerts.service;

import com.knifecerts.model.Moderatable;
import com.knifecerts.model.ModerationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModerationStateService {

    private final AlternativesParser alternativesParser;
    private final SettingsService settingsService;
    private final Map<Long, ModerationState> states = new ConcurrentHashMap<>();

    @Autowired
    public ModerationStateService(AlternativesParser alternativesParser, SettingsService settingsService) {
        this.alternativesParser = alternativesParser;
        this.settingsService = settingsService;
    }

    public void initState(Long chatId, Moderatable original) {
        states.put(chatId, new ModerationState(original, alternativesParser, settingsService));
    }

    public ModerationState createEmptyState(Long chatId) {
        ModerationState state = new ModerationState(alternativesParser, settingsService);
        states.put(chatId, state);
        return state;
    }

    public ModerationState getState(Long chatId) {
        return states.get(chatId);
    }

    public void setState(Long chatId, ModerationState state) {
        states.put(chatId, state);
    }

    public boolean hasChanges(Long chatId) {
        ModerationState state = states.get(chatId);
        return state != null && state.hasChanges();
    }

    public void removeState(Long chatId) {
        states.remove(chatId);
    }
}