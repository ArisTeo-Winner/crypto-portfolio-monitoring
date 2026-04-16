package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CoinMarketCapProperties.class, CoinGeckoProperties.class})
public class ProvidersPropertiesConfig {}
