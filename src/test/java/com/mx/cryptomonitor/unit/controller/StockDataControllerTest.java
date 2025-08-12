package com.mx.cryptomonitor.unit.controller;

import com.mx.cryptomonitor.application.controllers.StockDataController;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.api.MarketDataService;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockDataController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(SpringExtension.class)
public class StockDataControllerTest {
	
	private static final Logger logger = LoggerFactory.getLogger(StockDataControllerTest.class);


    @Autowired
    private MockMvc mockMvc;

    // Se simula el servicio para no depender de llamadas reales a APIs externas.
    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private JwtTokenUtil jwtTokenUtil;
    
    @MockBean
    private SessionRepository sessionRepository;
    
    @MockBean
    private UserDetailsService userDetailsService;

    /**
     * Prueba que, dado un símbolo válido, se retorne el precio de la acción.
     */
    @Test   
    public void testGetStockPriceSuccess() throws Exception {
    	
        logger.info("=== Ejecutando método testGetStockPriceSuccess() desde StockDataControllerTest ===");

    	
        String symbol = "AMZN";
        BigDecimal expectedPrice = new BigDecimal("208.4700");

        // Simulamos que el servicio retorna el precio esperado.
        when(marketDataService.getStockQuote(symbol)).thenReturn(Optional.of(expectedPrice));

        mockMvc.perform(get("/api/v1/marketdata/stock")
                .param("symbol", symbol)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                //.andExpect(content().string(expectedPrice.toString()))
                ;
                
    }

    /**
     * Prueba que, si el servicio no encuentra información para el símbolo, se retorne 404.
     * 
    */
    @Test
    public void testGetStockPriceNotFound() throws Exception {
    	
        logger.info("=== Ejecutando método testGetStockPriceNotFound() desde StockDataControllerTest ===");

    	
        String symbol = "INVALID";
        when(marketDataService.getStockPrice(symbol)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/marketdata/stock")
                .param("symbol", symbol)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("No se encontró información para el símbolo: " + symbol));
    }

    /**
     * Prueba que, dado un símbolo inválido (nulo o vacío), se retorne 400 Bad Request.     
    */
    @Test
    public void testGetStockPriceBadRequest() throws Exception {
    	
        logger.info("=== Ejecutando método testGetStockPriceBadRequest() desde StockDataControllerTest ===");

    	
        String symbol = "   "; // Cadena vacía o espacios

        logger.info("symbol : "+symbol);
        
        mockMvc.perform(get("/api/v1/marketdata/stock")
                .param("symbol", symbol)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
