package com.muzaffar.telegram.config;

import com.muzaffar.telegram.bot.AdminTelegramBot;
import com.muzaffar.telegram.bot.ReferralTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramBotConfiguration {

    private final ReferralTelegramBot telegramBot;
    private final AdminTelegramBot adminBot;

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            try {
                botsApi.registerBot(telegramBot);
                log.info("✅ User bot registered successfully with LONG POLLING mode");
                log.info("User bot username: @{}", telegramBot.getBotUsername());
            } catch (Exception e) {
                log.error("❌ Failed to register User bot", e);
            }

            if (adminBot.getBotToken() != null && !adminBot.getBotToken().isEmpty()) {
                try {
                    botsApi.registerBot(adminBot);
                    log.info("✅ Admin bot registered successfully with LONG POLLING mode");
                    log.info("Admin bot username: @{}", adminBot.getBotUsername());
                } catch (Exception e) {
                    log.error("⚠️ Failed to register Admin bot: {}", e.getMessage());
                }
            } else {
                log.warn("⚠️ Admin bot token not configured, skipping admin bot registration");
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize TelegramBotsApi", e);
        }
    }
}
