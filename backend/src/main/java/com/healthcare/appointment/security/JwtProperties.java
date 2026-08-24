package com.healthcare.appointment.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds app.jwt.* properties from application.yml.
 * JWT_SECRET must be set in the environment — there is intentionally no
 * default so that a misconfigured production deployment fails fast.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private long expirationMs;
}
