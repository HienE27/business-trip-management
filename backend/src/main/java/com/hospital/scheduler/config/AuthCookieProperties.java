package com.hospital.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthCookieProperties(
        boolean cookieSecure,
        String cookieSameSite
) {
}
