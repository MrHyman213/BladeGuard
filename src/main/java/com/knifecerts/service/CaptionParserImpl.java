package com.knifecerts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.knifecerts.dto.ParsedCaption;

import java.util.regex.Pattern;

@Service
public class CaptionParserImpl implements CaptionParser {

    @Autowired
    private final SettingsService settingsService;

    public CaptionParserImpl(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public ParsedCaption parse(String caption) {
        if (caption == null || caption.trim().isEmpty())
            return new ParsedCaption(null, null, null);

        String separator = settingsService.getAlternativeSeparator();
        String[] parts = Pattern.quote(separator).isEmpty()
                ? new String[]{caption}
                : caption.split(Pattern.quote(separator));

        for (int i = 0; i < parts.length; i++)
            parts[i] = parts[i].trim();

        if (parts.length == 3)
            return new ParsedCaption(parts[0], parts[1], parts[2]);
        else if (parts.length == 2)
            return new ParsedCaption(parts[0], parts[1], null);
        else if (parts.length == 1)
            return new ParsedCaption(null, parts[0], null);
        else
            return new ParsedCaption(null, null, null);
    }
    
    @Override
    public String format(ParsedCaption parsed) {
        if (parsed == null || parsed.isEmpty())
            return "";
        String separator = settingsService.getAlternativeSeparator();

        if (parsed.brand() != null && parsed.name() != null && parsed.index() != null)
            return parsed.brand() + separator + parsed.name() + separator + parsed.index();
        else if (parsed.brand() != null && parsed.name() != null)
            return parsed.brand() + separator + parsed.name();
        else if (parsed.name() != null)
            return parsed.name();
        else
            return "";
    }
}
