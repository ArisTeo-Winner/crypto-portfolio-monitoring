package com.mx.cryptomonitor.infrastructure.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Configuration
@Getter
public class PolygonApiConfig {
	/*
	@Value("${polygon.api.key}")
	private String polygonKey;
	
    @Value("${spring.datasource.url}")
	private String dataSourceUrl;
    
    @Value("${spring.datasource.username}")
    private String dataSourceUsername;
    
    @Value("${spring.datasource.password}")
    private String dataSourcePassord;
    
    @Value("${posgresql.url}")
    private String posgresqlUrl;
*/    
    @Value("${msg.assets}")
    private String msgAssets;
        
}
