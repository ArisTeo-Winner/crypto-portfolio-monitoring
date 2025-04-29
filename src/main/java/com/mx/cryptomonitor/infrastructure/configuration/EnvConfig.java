package com.mx.cryptomonitor.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Getter
@Configuration
@PropertySource("classpath:application.properties")
public class EnvConfig {
/*
    @PostConstruct
    public void loadEnv() {
        Dotenv dotenv = Dotenv.load();
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
    }*/

}
