package com.mx.cryptomonitor.infrastructure.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.mx.cryptomonitor.application.controllers.PortfolioController;
import com.mx.cryptomonitor.infrastructure.exceptions.ExternalApiException;
import com.mx.cryptomonitor.infrastructure.exceptions.MarketDataException;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Getter
@Service
public class MarketDataService {
	
    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);

	
    private final RestTemplate restTemplate;
    
    @Value("${api.coinmarketcap.base-url}")
    private String coinMarketCapBaseUrl;
    
    @Value("${api.coinmarketcap.api-key}")
    private String coinMarketCapApiKey;

    @Value("${api.alphavantage.base-url}")
    private String alphaVantageBaseUrl;

	@Value("${api.alphavantage.api-key}")
    private String alphaVantageApiKey;



	@Autowired
    public MarketDataService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @PostConstruct
    public void init() {
    	logger.info("📌 API CoinMarketCap Base URL: " + coinMarketCapBaseUrl);
    	logger.info("📌 API CoinMarketCap Key: " + (coinMarketCapApiKey == null ? "NO CARGADA" : "CARGADA"));
        logger.info("📌 API AlphaVantage Base URL: {}", alphaVantageBaseUrl);
        logger.info("📌 API AlphaVantage Key: {}", alphaVantageApiKey == null ? "NO CARGADA" : "CARGADA");
    }

    
    public BigDecimal getCryptoPrice(String symbol) {
        String url = coinMarketCapBaseUrl + "cryptocurrency/quotes/latest?symbol=" + symbol;

        // Crear headers con la API Key
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-CMC_PRO_API_KEY", coinMarketCapApiKey);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Realizar la petición con headers
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        // Convertir la respuesta en JSON
        JSONObject jsonResponse = new JSONObject(response.getBody());
        
        // Extraer el precio actual del activo en USD
        double priceDouble = jsonResponse.getJSONObject("data")
                .getJSONObject(symbol)
                .getJSONObject("quote")
                .getJSONObject("USD")
                .getDouble("price");

        return BigDecimal.valueOf(priceDouble);
    }
    
   /* public BigDecimal getCryptoPrice(String symbol) {
        String url = coinMarketCapBaseUrl + "cryptocurrency/quotes/latest?symbol=" + symbol + "&convert=USD";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-CMC_PRO_API_KEY", coinMarketCapApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return extractPrice(response.getBody(), symbol);
    }
*/

    /**
     * Obtiene el precio de la última transacción de una acción en Alpha Vantage
     * 
     * @param symbol Símbolo del activo (Ej: AMZN)
     * @return Optional<BigDecimal> con el precio de cierre más reciente
     */
    public Optional<BigDecimal> getStockPrice(String symbol) {
        String url = alphaVantageBaseUrl + "/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + alphaVantageApiKey;

        logger.info("📌 API Key utilizada: {}", alphaVantageApiKey);
        logger.info("🔗 URL generada: {}", url);

        try {
            // 🔥 Realizar la solicitud a la API
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            logger.info("📥 Respuesta de la API: {}", response);

            if (response == null || !response.containsKey("Time Series (Daily)")) {
                logger.warn("⚠ No se encontró 'Time Series (Daily)' en la respuesta.");
                return Optional.empty();
            }

            // Extraer los datos de la serie de tiempo
            Map<String, Object> timeSeries = (Map<String, Object>) response.get("Time Series (Daily)");
            String lastDate = timeSeries.keySet().stream().findFirst().orElse(null);

            if (lastDate == null) {
                logger.warn("⚠ No se encontró una fecha válida en la respuesta.");
                return Optional.empty();
            }

            // Extraer el precio de cierre de la fecha más reciente
            LinkedHashMap<String, Object> lastData = (LinkedHashMap<String, Object>) timeSeries.get(lastDate);
            if (!lastData.containsKey("4. close")) {
                logger.warn("⚠ No se encontró el precio de cierre para la fecha: {}", lastDate);
                return Optional.empty();
            }

            Object closePriceObj = lastData.get("4. close");
            if (closePriceObj == null) {
                logger.warn("⚠ El precio de cierre es nulo.");
                return Optional.empty();
            }

            BigDecimal closePrice = new BigDecimal(closePriceObj.toString());
            logger.info("✅ Último precio de cierre de {}: {}", symbol, closePrice);
            return Optional.of(closePrice);

        } catch (RestClientException e) {
            logger.error("❌ Error al comunicarse con la API de Alpha Vantage: {}", e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("❌ Error inesperado en getStockPrice(): {}", e.getMessage(), e);
            return Optional.empty();
        }
    }


    private BigDecimal extractPrice(Map<String, Object> response, String symbol) {
        try {
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map)) {
                throw new IllegalStateException("❌ Formato de respuesta inesperado: 'data' no es un Map.");
            }

            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object symbolObj = data.get(symbol);
            if (!(symbolObj instanceof Map)) {
                throw new IllegalStateException("❌ Formato de respuesta inesperado: símbolo '" + symbol + "' no es un Map.");
            }

            Map<String, Object> symbolData = (Map<String, Object>) symbolObj;
            Object quoteObj = symbolData.get("quote");
            if (!(quoteObj instanceof Map)) {
                throw new IllegalStateException("❌ Formato de respuesta inesperado: 'quote' no es un Map.");
            }

            Map<String, Object> quote = (Map<String, Object>) quoteObj;
            Object usdObj = quote.get("USD");
            if (!(usdObj instanceof Map)) {
                throw new IllegalStateException("❌ Formato de respuesta inesperado: 'USD' no es un Map.");
            }

            Map<String, Object> usdData = (Map<String, Object>) usdObj;
            return new BigDecimal(usdData.get("price").toString());

        } catch (Exception e) {
            throw new RuntimeException("❌ Error al extraer el precio de " + symbol, e);
        }
    }


    private BigDecimal extractStockPrice(Map<String, Object> response) {
        return new BigDecimal(((Map<String, Object>) response.get("Global Quote")).get("05. price").toString());
    }
    


    public BigDecimal getHistoricalPrice(String symbol, LocalDate date) {
        String url = alphaVantageBaseUrl + "?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + alphaVantageApiKey;
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        Map<String, Object> timeSeries = (Map<String, Object>) response.getBody().get("Time Series (Daily)");
        if (timeSeries == null || timeSeries.isEmpty()) {
            throw new RuntimeException("❌ No se encontró precio para la fecha: " + date);
        }

        Map<String, Object> historicalData = (Map<String, Object>) timeSeries.get(date.toString());
        if (historicalData == null || historicalData.isEmpty() || !historicalData.containsKey("4. close")) {
            throw new RuntimeException("❌ No se encontró precio para " + symbol + " en " + date);
        }

        return new BigDecimal(historicalData.get("4. close").toString());
    }
    
    /**
     * Obtiene el precio de cierre histórico de una acción para la fecha especificada.
     * Se espera que la respuesta de Alpha Vantage contenga la sección "Time Series (Daily)".
     *
     * @param symbol Símbolo de la acción (por ejemplo, "AMZN")
     * @param date   Fecha histórica en formato yyyy-MM-dd
     * @return Optional con el precio de cierre, o Optional.empty() si no se encuentran datos
     */
    public Optional<BigDecimal> getHistoricalStockPrice(String symbol, LocalDate date) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("El símbolo de la acción no debe ser nulo ni vacío.");
        }
        if (date == null) {
            throw new IllegalArgumentException("La fecha no debe ser nula.");
        }
        // Normalizar la base URL para asegurarnos de incluir "/query"
        String baseUrl = alphaVantageBaseUrl.endsWith("/query") ? alphaVantageBaseUrl : alphaVantageBaseUrl + "/query";
        String url = String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                baseUrl, symbol, alphaVantageApiKey);
        logger.info("🔗 URL generada para precio histórico: {}", url);
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            logger.info("📥 Respuesta de la API: {}", response);
            if (response == null || !response.containsKey("Time Series (Daily)")) {
                logger.warn("⚠ No se encontró 'Time Series (Daily)' en la respuesta.");
                return Optional.empty();
            }
            Map<String, Object> timeSeries = (Map<String, Object>) response.get("Time Series (Daily)");
            String dateKey = date.toString();  // Se espera que la clave sea en formato yyyy-MM-dd
            if (!timeSeries.containsKey(dateKey)) {
                logger.warn("⚠ No se encontraron datos para la fecha: {}", dateKey);
                return Optional.empty();
            }
            Map<String, Object> dailyData = (Map<String, Object>) timeSeries.get(dateKey);
            if (dailyData == null || !dailyData.containsKey("4. close")) {
                logger.warn("⚠ No se encontró precio de cierre para la fecha: {}", dateKey);
                return Optional.empty();
            }
            Object closePriceObj = dailyData.get("4. close");
            BigDecimal closePrice = new BigDecimal(closePriceObj.toString());
            logger.info("✅ Precio histórico de cierre de {} en {}: {}", symbol, dateKey, closePrice);
            return Optional.of(closePrice);
        } catch (RestClientException e) {
            logger.error("❌ Error al comunicarse con la API de Alpha Vantage: {}", e.getMessage(), e);
            throw new ExternalApiException("Error al comunicarse con la API de Alpha Vantage", e);
        } catch (Exception e) {
            logger.error("❌ Error inesperado en getHistoricalStockPrice(): {}", e.getMessage(), e);
            throw new MarketDataException("Error inesperado en MarketDataService (historical)", e);
        }
    }
        
    public Optional<String> getHistoricalStockDataAsJson(String symbol, LocalDate date) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("El símbolo de la acción no debe ser nulo ni vacío.");
        }
        if (date == null) {
            throw new IllegalArgumentException("La fecha no debe ser nula.");
        }
        
        // Normalizar la base URL para asegurarnos de incluir "/query"
        String baseUrl = alphaVantageBaseUrl.endsWith("/query") ? alphaVantageBaseUrl : alphaVantageBaseUrl + "/query";
        String url = String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                baseUrl, symbol, alphaVantageApiKey);
        logger.info("🔗 URL generada para datos históricos: {}", url);
        
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            logger.info("📥 Respuesta de la API: {}", response);
            if (response == null || !response.containsKey("Time Series (Daily)")) {
                logger.warn("⚠ No se encontró 'Time Series (Daily)' en la respuesta.");
                return Optional.empty();
            }
            Map<String, Object> timeSeries = (Map<String, Object>) response.get("Time Series (Daily)");
            String dateKey = date.toString();  // Se espera que la clave sea en formato yyyy-MM-dd
            if (!timeSeries.containsKey(dateKey)) {
                logger.warn("⚠ No se encontraron datos para la fecha: {}", dateKey);
                return Optional.empty();
            }

            Map<String, Object> dailyData = (Map<String, Object>) timeSeries.get(dateKey);
            //
            
            if (dailyData == null || !dailyData.containsKey("4. close")) {
                logger.warn("⚠ No se encontró precio de cierre para la fecha: {}", dateKey);
                return Optional.empty();
            }
            Object closePriceObj = dailyData.get("4. close");
            BigDecimal closePrice = new BigDecimal(closePriceObj.toString());
            logger.info("✅ Precio histórico de cierre de {} en {}: {}", symbol, dateKey, closePrice);
            
            // Creamos un nuevo JSONObject que solo contenga el registro de la fecha deseada
            JSONObject resultadoFiltrado = new JSONObject();
            resultadoFiltrado.put(dateKey, new JSONObject(dailyData));
            String jsonConIndentacion = resultadoFiltrado.toString(4);
            logger.info("✅ Datos históricos de {} en {}:\n{}", symbol, dateKey, jsonConIndentacion);
            //return Optional.of(jsonConIndentacion);
            return Optional.of(closePrice.toString());
        } catch (Exception e) {
            logger.error("❌ Error inesperado en getHistoricalStockDataAsJson(): {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
