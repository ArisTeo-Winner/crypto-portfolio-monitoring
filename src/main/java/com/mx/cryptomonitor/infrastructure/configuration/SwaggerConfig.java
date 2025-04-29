package com.mx.cryptomonitor.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI customOpenAPI() {
		
		return new OpenAPI()
				.info(new Info()
						.title("Crypto portfolio monitorig API")
						.version("1.0")
						.description("Documentación de la API para el monitoreo de carteras de criptomonedas")
						.license(new License().name("Apache 2.0").url("https://springdoc.org")));
	}
}
