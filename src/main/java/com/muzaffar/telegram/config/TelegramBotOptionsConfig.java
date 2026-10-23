package com.muzaffar.telegram.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramBotOptionsConfig {

    private final BotConfig botConfig;

    @Bean
    public DefaultBotOptions defaultBotOptions() {
        DefaultBotOptions options = new DefaultBotOptions();

        // 1. Base URL configuration (Cloudflare Worker Reverse Proxy, local Bot API server, etc.)
        if (botConfig.getApi() != null && botConfig.getApi().getBaseUrl() != null) {
            String baseUrl = botConfig.getApi().getBaseUrl().trim();
            if (!baseUrl.isEmpty()) {
                while (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                if (!baseUrl.endsWith("/bot")) {
                    baseUrl = baseUrl + "/bot";
                }
                options.setBaseUrl(baseUrl);
                log.info("🌐 Telegram API Base URL configured: {}", baseUrl);
            }
        }

        // 2. Proxy configuration (SOCKS5 / HTTP Proxy)
        BotConfig.ProxyProperties proxy = botConfig.getProxy();
        if (proxy != null && proxy.isEnabled()) {
            if ("HTTP".equalsIgnoreCase(proxy.getType())) {
                options.setProxyType(DefaultBotOptions.ProxyType.HTTP);
            } else {
                options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
            }
            options.setProxyHost(proxy.getHost());
            options.setProxyPort(proxy.getPort());
            log.info("🛡️ Telegram Proxy configured: {} {}:{}", options.getProxyType(), proxy.getHost(), proxy.getPort());
        }

        return options;
    }
}
