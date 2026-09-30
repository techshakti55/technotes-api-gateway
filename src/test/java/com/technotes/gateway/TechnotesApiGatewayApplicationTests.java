package com.technotes.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false")
class TechnotesApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
