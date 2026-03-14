package com.knifecerts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.knifecerts.bot.AdminBot;
import com.knifecerts.bot.KnifeBot;

@Configuration
public class BotConfig {

    @Bean
    public KnifeBot knifeBot() {
        return new KnifeBot();
    }

    @Bean
    public AdminBot adminBot() {
        return new AdminBot();
    }
}
