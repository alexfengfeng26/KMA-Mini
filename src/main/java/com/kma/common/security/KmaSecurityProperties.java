package com.kma.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kma.security")
public class KmaSecurityProperties {
    private String mode = "local";
    private String audience = "kma-api";
    private String localIssuer = "kma-local";
    private String localSecret;
    private int accessTtlMinutes = 15;
    private int refreshTtlDays = 14;
    private int requestsPerMinute = 600;
    private int loginRequestsPerMinute = 20;
    private int idempotencyTtlHours = 24;
    private int idempotencyLeaseSeconds = 300;
    private int idempotencyMaxResponseBytes = 1_048_576;
    private int requestLeaseSeconds = 300;
    private Content content = new Content();
    private Bootstrap bootstrap = new Bootstrap();

    public boolean localEnabled() {
        return "local".equalsIgnoreCase(mode) || "hybrid".equalsIgnoreCase(mode);
    }

    @Data
    public static class Bootstrap {
        private String username = "admin";
        private String password;
        private String displayName = "KMA 管理员";
    }

    @Data
    public static class Content {
        private boolean enabled = true;
        private boolean blockPromptInjection = true;
        private boolean redactBeforeModel = true;
        private boolean redactOutput = true;
        private boolean audit = true;
    }
}
