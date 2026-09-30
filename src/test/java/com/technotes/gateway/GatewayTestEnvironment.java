package com.technotes.gateway;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Test-only values override workstation environment variables. */
final class GatewayTestEnvironment {
    private GatewayTestEnvironment() { }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("SERVER_PORT", () -> "0");
        registry.add("NOTES_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("USER_OAUTH_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("UI_ORIGIN", () -> "http://localhost:5173");
        registry.add("GATEWAY_CONNECT_TIMEOUT_MS", () -> "1000");
        registry.add("GATEWAY_RESPONSE_TIMEOUT", () -> "5s");
        registry.add("CORS_MAX_AGE_SECONDS", () -> "60");
        registry.add("EUREKA_CLIENT_ENABLED", () -> "false");
        registry.add("EUREKA_URL", () -> "http://127.0.0.1:1/eureka/");
    }
}
