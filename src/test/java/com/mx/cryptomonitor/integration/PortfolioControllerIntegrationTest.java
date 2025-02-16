package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PortfolioControllerIntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(PortfolioControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PortfolioEntryRepository portfolioEntryRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    // Variables para el usuario y la entrada de portafolio de prueba.
    private PortfolioEntry testPortfolioEntry;
    
    
    private User testUser;
    
    @BeforeEach
    public void setup() {
        // Limpiar las tablas para evitar datos residuales.
        transactionRepository.deleteAll();
        portfolioEntryRepository.deleteAll();
        
        UUID userId = UUID.fromString("9cd1ba3b-3676-4e52-8373-c7cd68492c71");
        
        testUser = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        logger.info("Cosulta un UUID de user: "+testUser);

        // Crear y guardar una entrada de portafolio para el usuario.
        testPortfolioEntry = PortfolioEntry.builder()
                .user(testUser)
                .assetSymbol("ETH")
                .assetType("CRYPTO")
                .totalQuantity(BigDecimal.valueOf(10))
                .totalInvested(BigDecimal.valueOf(20000))
                .averagePricePerUnit(BigDecimal.valueOf(2000))
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        testPortfolioEntry = portfolioEntryRepository.save(testPortfolioEntry);
        
        logger.info("Usuario de prueba creado: {}", testUser);
        logger.info("Entrada de portafolio creada con ID: {}", testPortfolioEntry.getPortfolioEntryId());
    }
    
    @Test
    public void testRegisterTransactionEndpoint() throws Exception {
        // Construir el TransactionRequest usando el ID real del usuario y de la entrada de portafolio.
        TransactionRequest request = new TransactionRequest(
            testUser.getId(),
            testPortfolioEntry.getPortfolioEntryId(), // Se envía el ID de la entrada existente
            "ETH", 
            "CRYPTO", 
            "BUY", 
            BigDecimal.valueOf(5), 
            BigDecimal.valueOf(2000), // princePerUnit
            BigDecimal.valueOf(10000), // totalValue
            LocalDateTime.now(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(2000),
            "Comprar ETH"
        );
        
        // Realizar la solicitud POST al endpoint
        String jsonResponse = mockMvc.perform(post("/api/v1/portfolio/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(result -> logger.info("Respuesta del endpoint: {}", result.getResponse().getContentAsString()))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.assetSymbol").value("ETH"))
            .andExpect(jsonPath("$.transactionType").value("BUY"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        // Deserializar la respuesta a TransactionResponse
        TransactionResponse response = objectMapper.readValue(jsonResponse, TransactionResponse.class);
        assertNotNull(response, "La respuesta no debe ser nula");
        assertEquals(testUser.getId(), response.userId(), "El userId de la respuesta debe coincidir con el usuario de prueba");
        
        // Verificar en la base de datos que la transacción se guardó.
        // Como el record TransactionResponse no tiene un campo 'user' (sólo userId),
        // se valida utilizando el campo userId en la respuesta.
        List<TransactionResponse> transactions = transactionRepository.findByUserId(testUser.getId());
        assertFalse(transactions.isEmpty(), "Debe existir al menos una transacción para el usuario");
        // Como TransactionResponse solo tiene userId, validamos ese campo:
        TransactionResponse savedTransaction = transactions.get(0);
        assertNotNull(savedTransaction.userId(), "El campo 'userId' en la transacción guardada no debe ser nulo");
        assertEquals(testUser.getId(), savedTransaction.userId(), "El user_id de la transacción debe coincidir con el ID del usuario de prueba");

        
    }

}
