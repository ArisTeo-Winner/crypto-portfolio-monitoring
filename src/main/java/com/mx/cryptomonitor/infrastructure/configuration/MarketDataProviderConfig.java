package com.mx.cryptomonitor.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.infrastructure.api.client.AlphaVantageAdapter;

@Configuration
@EnableConfigurationProperties(AlphaVantageProperties.class)
public class MarketDataProviderConfig {

	 @Bean
	 public AlphaVantageAdapter alphaVantageAdapter(WebClient webClient, AlphaVantageProperties alphaVantageProperties) {
		 
	    	System.out.println(alphaVantageProperties.getBaseUrl());
	    	System.out.println(alphaVantageProperties.getApiKey());
	    	
		 return new AlphaVantageAdapter(webClient, alphaVantageProperties.getBaseUrl(), alphaVantageProperties.getApiKey());
	 }
}
