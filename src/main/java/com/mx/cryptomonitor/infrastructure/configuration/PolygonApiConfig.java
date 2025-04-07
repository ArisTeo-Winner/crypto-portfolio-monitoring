package com.mx.cryptomonitor.infrastructure.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolygonApiConfig {
    private final Logger logger = LoggerFactory.getLogger(PolygonApiConfig.class);

	@Value("${polygon.api.key}")
	private String polygonApiKey;

    public String getPolygonApiKey() {
    	
        logger.info("=== Ejecutando método getPolygonApiKey() desde PolygonApiConfig ===");

    	
        logger.info("=== Return KEY de Polygon : {} === ",polygonApiKey);

        return polygonApiKey;
    }
}

