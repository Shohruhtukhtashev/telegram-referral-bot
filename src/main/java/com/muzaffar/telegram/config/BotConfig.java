package com.muzaffar.telegram.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram")
public class BotConfig {
    
    private BotProperties bot;
    private BotProperties adminBot;
    private WebhookProperties webhook;

    @Data
    public static class BotProperties {
        private String token;
        private String username;
        private String mode = "polling"; // "polling" or "webhook"
    }
    
    @Data
    public static class WebhookProperties {
        private String url;
        private String path = "/webhook";
    }
}
