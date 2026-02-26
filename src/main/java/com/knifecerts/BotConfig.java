package com.knifecerts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.logging.Logger;

@Configuration
public class BotConfig {

    private static final Logger logger = Logger.getLogger(BotConfig.class.getName());

    @Bean
    public TelegramBotsApi telegramBotsApi(KnifeBot knifeBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(knifeBot);
        logger.info("Bot registered successfully");
        return api;
    }
}
