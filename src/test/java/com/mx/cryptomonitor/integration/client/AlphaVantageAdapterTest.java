package com.mx.cryptomonitor.integration.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.infrastructure.api.client.AlphaVantageAdapter;

import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@Slf4j
class AlphaVantageAdapterTest {
	

    static MockWebServer mockWebServer;
    static AlphaVantageAdapter adapter;


    @BeforeAll
    static void startServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Usa la URL dinámica del MockWebServer
        String baseUrl = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder().build();
        adapter = new AlphaVantageAdapter(webClient, baseUrl, "demo-key");
    }

    @AfterAll
    static void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void getLatest_returnsPrice_whenApiReturnsGlobalQuote() throws InterruptedException {

    	log.info("=== Executing test: getLatest_returnsPrice_whenApiReturnsGlobalQuote");

    	
        // JSON simulado exacto del servicio real
        String responseJson = """
            {
              "Global Quote": {
                "01. symbol": "CRCL",
                "02. open": "189.2700",
                "03. high": "204.9100",
                "04. low": "185.5400",
                "05. price": "204.7000",
                "06. volume": "15445172",
                "07. latest trading day": "2025-07-14",
                "08. previous close": "187.3300",
                "09. change": "17.3700",
                "10. change percent": "9.2724%"
              }
            }
            """;
    	
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseJson)
        );

        Optional<BigDecimal> result = adapter.getLatest("CRCL");
        
        assertThat(result)
        		.as("Extrae correctamente el precio 05. price")
        		.isPresent()
        		.contains(new BigDecimal("204.7000"));
        
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        
        String path = req.getRequestUrl().encodedPath();
        String query = req.getRequestUrl().queryParameter("function")+"?symbol="+req.getRequestUrl().queryParameter("symbol");
        
        assertThat(path).endsWith("/query");
        assertThat(req.getRequestUrl().queryParameter("symbol")).isEqualTo("CRCL");
        assertThat(req.getRequestUrl().queryParameter("apikey")).isEqualTo("demo-key");
        assertThat(req.getRequestUrl().queryParameter("function")).isEqualTo("GLOBAL_QUOTE");
    }
    
    void getLatest_handlesHttpErrorsAndReturnsEmtyp() {
    	log.info("=== Executing test: getLatest_handlesHttpErrorsAndReturnsEmtyp");

    	mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    	
    	Optional<BigDecimal> result = adapter.getLatest("CRCL");
    	
    	assertThat(result).isEmpty();
    }
    
    @Test
    void getLatest_returnsEmpty_whenApiReturnsNoGlobalQuote() {
    	log.info("=== Executing test: getLatest_returnsEmpty_whenApiReturnsNoGlobalQuote");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{ \"Note\": \"API limit reached\" }"));

        Optional<BigDecimal> result = adapter.getLatest("CRCL");

        assertThat(result)
            .as("Si no existe la clave Global Quote, retorna Optional.empty()")
            .isEmpty();
    }

    

}
