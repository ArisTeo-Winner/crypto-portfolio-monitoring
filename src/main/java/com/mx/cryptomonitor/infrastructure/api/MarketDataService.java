package com.mx.cryptomonitor.infrastructure.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.application.controllers.PortfolioController;
import com.mx.cryptomonitor.infrastructure.exceptions.ExternalApiException;
import com.mx.cryptomonitor.infrastructure.exceptions.MarketDataException;
import com.mx.cryptomonitor.infrastructure.exceptions.NoMarketDataFoundException;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Service
public class MarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;
    
    @Value("${API_COINMARKETCAP_BASE_URL}")
    private String coinMarketCapBaseUrl;

    @Value("${API_COINMARKETCAP_API_KEY}")
    private String coinMarketCapApiKey;

    @Value("${API_ALPHAVANTAGE_BASE_URL}")
    private String alphaVantageBaseUrl;

    @Value("${API_ALPHAVANTAGE_API_KEY}")
    private String alphaVantageApiKey;

    
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


    /**
     * Obtiene el precio de cierre de una criptomoneda utilizando la API de
     * CoinMarketCap Pro.
     * Se espera que la respuesta tenga la siguiente estructura:
     * {
     * "status": { ... },
     * "data": {
     * "BTC": [ { "quote": { "USD": { "price": 83665.94559433253, ... } }, ... ]
     * }
     * }
     *
     * La API de CoinMarketCap Pro requiere enviar la API key en el encabezado
     * "X-CMC_PRO_API_KEY".
     *
     * @param symbol Símbolo de la criptomoneda (por ejemplo, "BTC")
     * @return Optional con el precio de cierre en USD, o Optional.empty() si no se
     *         encuentran datos.
     */
    @Cacheable(value = "cryptoPrices", key = "#symbol", cacheManager = "cacheManager")
    public Optional<BigDecimal> getCryptoPrice(String symbol) {

        logger.info("=== Ejecutando método getCryptoPrice() desde MarketDataService ===");

        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("El símbolo de la criptomoneda no debe ser nulo ni vacío.");
        }

        logger.info("📌 API Key utilizada: {}", coinMarketCapApiKey);

        // Construir la URL de la API.
        // Ejemplo:
        // https://pro-api.coinmarketcap.com/v2/cryptocurrency/quotes/latest?symbol=BTC
        String url = String.format("%s/v2/cryptocurrency/quotes/latest?symbol=%s", coinMarketCapBaseUrl, symbol);
        logger.info("🔗 URL generada para criptomoneda: {}", url);

        try {
            // Configurar los encabezados para enviar la API key
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-CMC_PRO_API_KEY", coinMarketCapApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Realizar la solicitud GET con exchange para incluir headers
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String responseBody = responseEntity.getBody();
            //logger.info("📥 Respuesta de la API de CoinMarketCap: {}", responseBody);

            // Convertir la respuesta JSON a un Map
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);

            if (responseMap == null || !responseMap.containsKey("data")) {
                logger.warn("⚠ No se encontró 'data' en la respuesta de CoinMarketCap.");
                return Optional.empty();
            }
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            // Suponemos que la clave es el símbolo
            Object tickerDataObj = data.get(symbol.toUpperCase());
            if (tickerDataObj == null) {
                logger.warn("⚠ No se encontraron datos para la criptomoneda: {}", symbol);
                return Optional.empty();
            }
            // El tickerData es una lista
            if (!(tickerDataObj instanceof Iterable)) {
                logger.warn("⚠ La estructura de datos para la criptomoneda es inesperada.");
                return Optional.empty();
            }
            Iterable<?> tickerData = (Iterable<?>) tickerDataObj;
            Object firstElement = tickerData.iterator().hasNext() ? tickerData.iterator().next() : null;
            if (firstElement == null || !(firstElement instanceof Map)) {
                logger.warn("⚠ No se pudo interpretar la información del ticker.");
                return Optional.empty();
            }
            Map<String, Object> tickerInfo = (Map<String, Object>) firstElement;
            if (!tickerInfo.containsKey("quote")) {
                logger.warn("⚠ No se encontró 'quote' en los datos del ticker.");
                return Optional.empty();
            }
            Map<String, Object> quote = (Map<String, Object>) tickerInfo.get("quote");
            if (!quote.containsKey("USD")) {
                logger.warn("⚠ No se encontró información en USD para el ticker.");
                return Optional.empty();
            }
            Map<String, Object> usdData = (Map<String, Object>) quote.get("USD");
            if (!usdData.containsKey("price")) {
                logger.warn("⚠ No se encontró el precio en los datos USD.");
                return Optional.empty();
            }
            Object priceObj = usdData.get("price");
            BigDecimal price = new BigDecimal(priceObj.toString());
            logger.info("✅ Último precio de cierre de {}: {}", symbol, price);
            return Optional.of(price);

        } catch (RestClientException e) {
            logger.error("❌ Error al comunicarse con la API de CoinMarketCap: {}", e.getMessage(), e);
            throw new ExternalApiException("Error al comunicarse con la API de CoinMarketCap", e);
        } catch (Exception e) {
            logger.error("❌ Error inesperado en getCryptoPrice(): {}", e.getMessage(), e);
            throw new MarketDataException("Error inesperado en MarketDataService (crypto)", e);
        }
    }

    /**
     * Obtiene el precio de la última transacción de una acción en Alpha Vantage
     * 
     * @param symbol Símbolo del activo (Ej: AMZN)
     * @return Optional<BigDecimal> con el precio de cierre más reciente
     */
    @Cacheable(value = "stockPrices", key = "#symbol")
    public Optional<BigDecimal> getStockPrice(String symbol) {
        String url = alphaVantageBaseUrl + "/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey="
                + alphaVantageApiKey;

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

    @Cacheable(value = "stockPrices", key = "#symbol")
    public Optional<BigDecimal>  getStockQuote(String symbol){    	
    	logger.info("Executing MarketDataService: getStockQuote"); 
    	
    	String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",alphaVantageBaseUrl, symbol, alphaVantageApiKey);    	
    	logger.debug("URL AlphaVantage : {}",url);  
    	
    	Map response  = restTemplate.getForObject(url, Map.class);    	
    		    	    	
    	logger.debug("Response from AlphaVantage: {}",response);
    	
    	if (response !=null && response.containsKey("Global Quote")) {
		    	Map<String, String> globalQuote = (Map<String, String>) response.get("Global Quote");
    	
		    	BigDecimal closePrice = new BigDecimal(globalQuote.get("05. price"));
    	
		    	logger.info("✅ Último precio de cierre de {}: {}", symbol, closePrice);
    	
		    	return Optional.of(closePrice);	
		}
    	
    	logger.warn("⚠️ Price data not available for symbol: {}",symbol);
    	
    	return Optional.empty();
    	
    }
    /**/    
    public Optional<BigDecimal> getClosePriceForDate(String symbol, LocalDate targetDate){
    	logger.info("Exeuting MarketDataService: getClosePriceForDate");
    	 
        try {
            String url = String.format("%s/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                alphaVantageBaseUrl, symbol, alphaVantageApiKey);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Map<String, String>> timeSeries = (Map<String, Map<String, String>>) response.get("Time Series (Daily)");

            if (timeSeries == null || timeSeries.isEmpty()) {
            	logger.warn("⚠️ No hay datos de precios disponibles.");
                return Optional.empty();
            }

            // Buscar la fecha disponible más cercana (hasta 10 días atrás)
            LocalDate currentDate = targetDate;
            for (int i = 0; i < 10; i++) {
                String dateKey = currentDate.toString();
                if (timeSeries.containsKey(dateKey)) {
                    String closePrice = timeSeries.get(dateKey).get("4. close");
                    
                    logger.info("Found close price for {} on {}: {}", symbol, dateKey, closePrice);

                    return Optional.of(new BigDecimal(closePrice));
                }
                currentDate = currentDate.minusDays(1);
            }

            logger.warn("⚠️ No se encontró precio en los últimos 10 días hábiles.");
        } catch (Exception e) {
        	logger.error("❌ Error al consultar precio histórico de {} para {}: {}", symbol, targetDate, e.getMessage());
        }
        return Optional.empty();

    }

    /**
     * Obtiene el precio de cierre histórico de una acción para la fecha
     * especificada.
     * Se espera que la respuesta de Alpha Vantage contenga la sección "Time Series
     * (Daily)".
     *
     * @param symbol Símbolo de la acción (por ejemplo, "AMZN")
     * @param date   Fecha histórica en formato yyyy-MM-dd
     * @return Optional con el precio de cierre, o Optional.empty() si no se
     *         encuentran datos
     */
    @Cacheable(
    		  value  = "historicalPrices",
    		  key    = "#p0 + ':' + #p1.toString()"
    		)    
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
            //logger.info("📥 Respuesta de la API: {}", response);
            if (response == null || !response.containsKey("Time Series (Daily)")) {
                logger.warn("⚠ No se encontró 'Time Series (Daily)' en la respuesta.");
                return Optional.empty();
            }
            Map<String, Object> timeSeries = (Map<String, Object>) response.get("Time Series (Daily)");
            String dateKey = date.toString(); // Se espera que la clave sea en formato yyyy-MM-dd
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
            String dateKey = date.toString(); // Se espera que la clave sea en formato yyyy-MM-dd
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
            // return Optional.of(jsonConIndentacion);
            return Optional.of(closePrice.toString());
        } catch (Exception e) {
            logger.error("❌ Error inesperado en getHistoricalStockDataAsJson(): {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
