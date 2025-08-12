package com.mx.cryptomonitor.infrastructure.api.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.domain.ports.MarketDataProvider;
import com.mx.cryptomonitor.infrastructure.configuration.AlphaVantageProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlphaVantageAdapter implements MarketDataProvider{
	
	private final WebClient webClient;
	
	//private final AlphaVantageProperties alphaVantageProperties;
	
    private final String alphaVantageBaseUrl;
	private final String alphaVantageApiKey;
			
	/*
    @Value("${API_ALPHAVANTAGE_BASE_URL}")
    private String alphaVantageBaseUrl;

    @Value("${API_ALPHAVANTAGE_API_KEY}")
    private String alphaVantageApiKey;
    */
 
    public void printConfig() {
        System.out.println(alphaVantageBaseUrl);
        System.out.println(alphaVantageApiKey);
    }
	
	@Override
	public Optional<BigDecimal> getLatest(String symbol) {
		// TODO Auto-generated method stub		
		String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s", alphaVantageBaseUrl, symbol,alphaVantageApiKey);
        log.info("🔗 Llamada AlphaVantage getLatestPrice: {}", url);

        return webClient.get()
        		.uri(url)
        		.retrieve()
        		.bodyToMono(Map.class)
        		.map(this::extractGlobalQuote)
        		.onErrorResume(ex -> {
        			log.error("Error AlphaVantage GLOBAL_QUOTE", ex);
        			return Mono.just(Optional.<BigDecimal>empty());
        		})
        		.block();
        
		//return Optional.empty();
	}
	
	private Optional<BigDecimal> extractGlobalQuote(Map<String, Object> json){
		if (!json.containsKey("Global Quote")) {
			return Optional.empty();
		}
		
		Map<String, String> quote = (Map) json.get("Global Quote");
		String priceStr = quote.get("05. price");
				
		return Optional.ofNullable(priceStr).map(BigDecimal::new);
	}

	@Override
	public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
		// TODO Auto-generated method stub
		return Optional.empty();
	}
	

}
