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
    private ApiProperties api = new ApiProperties();
    private ProxyProperties proxy = new ProxyProperties();

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

    @Data
    public static class ApiProperties {
        private String baseUrl = "https://api.telegram.org/bot";
    }

    @Data
    public static class ProxyProperties {
        private boolean enabled = false;
        private String type = "SOCKS5"; // SOCKS5 or HTTP
        private String host = "127.0.0.1";
        private int port = 1080;
    }

}
