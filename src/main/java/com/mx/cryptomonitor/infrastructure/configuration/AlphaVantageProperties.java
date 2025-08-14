package com.mx.cryptomonitor.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "marketdata.alphavantage")
public class AlphaVantageProperties {
	
	@NotBlank
	private String baseUrl;
	
	@NotBlank
	private String apiKey;

}
