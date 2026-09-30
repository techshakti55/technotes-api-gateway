package com.technotes.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.technotes.gateway.config.GatewayEnvironmentValidator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayEnvironmentValidatorTests {
    private MockEnvironment valid() {
        return new MockEnvironment()
                .withProperty("NOTES_SERVICE_URL", "https://notes.example.test")
                .withProperty("USER_OAUTH_SERVICE_URL", "https://auth.example.test")
                .withProperty("UI_ORIGIN", "https://ui.example.test")
                .withProperty("GATEWAY_CONNECT_TIMEOUT_MS", "1000")
                .withProperty("GATEWAY_RESPONSE_TIMEOUT", "5s")
                .withProperty("CORS_MAX_AGE_SECONDS", "60")
                .withProperty("EUREKA_CLIENT_ENABLED", "false")
                .withProperty("EUREKA_URL", "https://discovery.example.test/eureka/");
    }

    @Test
    void acceptsExplicitOriginsAndPositiveTimeouts() {
        assertDoesNotThrow(() -> new GatewayEnvironmentValidator(valid()).afterPropertiesSet());
    }

    @Test
    void rejectsMissingOrigin() {
        MockEnvironment env = new MockEnvironment();
        assertThrows(IllegalStateException.class,
                () -> new GatewayEnvironmentValidator(env).afterPropertiesSet());
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayEnvironmentValidator(
                valid().withProperty("UI_ORIGIN", "*")).afterPropertiesSet());
    }

    @Test
    void rejectsPathAndCredentialsInBackendOrigin() {
        for (String origin : new String[]{"https://notes.example.test/api", "https://user:password@notes.example.test"}) {
            assertThrows(IllegalArgumentException.class, () -> new GatewayEnvironmentValidator(
                    valid().withProperty("NOTES_SERVICE_URL", origin)).afterPropertiesSet());
        }
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayEnvironmentValidator(
                valid().withProperty("GATEWAY_RESPONSE_TIMEOUT", "0s")).afterPropertiesSet());
        assertThrows(IllegalArgumentException.class, () -> new GatewayEnvironmentValidator(
                valid().withProperty("GATEWAY_CONNECT_TIMEOUT_MS", "-1")).afterPropertiesSet());
    }
}
