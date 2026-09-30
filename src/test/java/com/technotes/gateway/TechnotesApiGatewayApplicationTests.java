package com.technotes.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "eureka.client.enabled=false")
class TechnotesApiGatewayApplicationTests {

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        GatewayTestEnvironment.register(registry);
    }

	@Test
	void contextLoads() {
	}

}
