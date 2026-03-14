// BotRegistrar.java - новый файл
package com.knifecerts.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.KnifeBot;

import java.util.logging.Logger;

@Component
public class BotRegister {

    private static final Logger logger = Logger.getLogger(BotRegister.class.getName());

    @Autowired
    private KnifeBot knifeBot;

    @Autowired
    private AdminBot adminBot;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBots() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(knifeBot);
            botsApi.registerBot(adminBot);
            logger.info("Боты успешно зарегистрированы!");
        } catch (TelegramApiException e) {
            logger.severe("Ошибка регистрации ботов: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
