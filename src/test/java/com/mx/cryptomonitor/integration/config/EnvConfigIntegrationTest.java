package com.mx.cryptomonitor.integration.config;



import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;


@SpringBootTest
@Slf4j
class EnvConfigIntegrationTest {
	


	@Test
	void test() {
		
		String msgAssets = "Test";
		
		log.info("Mesanje de test:{}", msgAssets);

	}

}
