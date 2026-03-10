package com.knifecerts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class BotConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "bot.version", havingValue = "new", matchIfMissing = false)
    public KnifeBotNew knifeBotNew() {
        return new KnifeBotNew();
    }

    @Bean
    @ConditionalOnProperty(name = "bot.version", havingValue = "old", matchIfMissing = true)
    public KnifeBot knifeBot() {
        return new KnifeBot();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "admin.bot.version", havingValue = "new", matchIfMissing = false)
    public AdminBotNew adminBotNew() {
        return new AdminBotNew();
    }

    @Bean
    @ConditionalOnProperty(name = "admin.bot.version", havingValue = "old", matchIfMissing = true)
    public AdminBot adminBot() {
        return new AdminBot();
    }
}