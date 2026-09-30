package com.technotes.gateway.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail startup on unsafe or incomplete environment configuration. */
@Component
public class GatewayEnvironmentValidator implements InitializingBean {
    private final Environment environment;

    public GatewayEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        origin("NOTES_SERVICE_URL");
        origin("USER_OAUTH_SERVICE_URL");
        origin("UI_ORIGIN");
        positive("GATEWAY_CONNECT_TIMEOUT_MS");
        positive("CORS_MAX_AGE_SECONDS");
        Duration timeout = DurationStyle.detectAndParse(required("GATEWAY_RESPONSE_TIMEOUT"));
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("GATEWAY_RESPONSE_TIMEOUT must be positive");
        }
        String discovery = required("EUREKA_CLIENT_ENABLED");
        if (!"true".equalsIgnoreCase(discovery) && !"false".equalsIgnoreCase(discovery)) {
            throw new IllegalArgumentException("EUREKA_CLIENT_ENABLED must be true or false");
        }
        URI eureka = URI.create(required("EUREKA_URL"));
        if (!isHttp(eureka) || eureka.getHost() == null || eureka.getUserInfo() != null
                || eureka.getQuery() != null || eureka.getFragment() != null) {
            throw new IllegalArgumentException("EUREKA_URL must be an HTTP(S) URL without credentials");
        }
    }

    private void origin(String key) {
        URI uri = URI.create(required(key));
        if (!isHttp(uri) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().isEmpty() || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException(key + " must be one exact HTTP(S) origin without a path or credentials");
        }
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private void positive(String key) {
        if (Integer.parseInt(required(key)) <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
    }

    private String required(String key) {
        String value = environment.getRequiredProperty(key);
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }
}
