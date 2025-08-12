package com.mx.cryptomonitor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.PortfolioEntryRepository;
import com.mx.cryptomonitor.domain.repositories.TransactionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.exceptions.UserNotFoundException;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.TransactionRequest;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;



@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(value = Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class PortfolioControllerIntegrationTest {

        private final Logger logger = LoggerFactory.getLogger(PortfolioControllerIntegrationTest.class);
/**/
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
        
        @Autowired
        private PasswordEncoder passwordEncoder;

        private User testUser;
        
        private String email = "test@example.com";
        private String password = "Test@Password";
        
        private String jwtToken;
        private UUID mockUserId;


        @BeforeEach
        public void setup() throws JsonProcessingException, Exception {
                // Limpiar las tablas para evitar datos residuales.
        	
        	 
                transactionRepository.deleteAll();
                portfolioEntryRepository.deleteAll();     
                
               //mockUserId = UUID.fromString("22aff521-0264-468a-9209-163208f7401a");
                
               //userRepository.deleteAll();
                               
               // Crear y guardar un usuario de prueba
                /**/
                testUser = User.builder()
                                .username("testuser")
                                .email(email)
                                .passwordHash(passwordEncoder.encode(password))
                                .build();
                testUser = userRepository.save(testUser);
                
                 logger.info("=== Ejecutando método login_success() desde UserControllerIntegrationTest ===");
        	        	
        	User savedUser = userRepository.findByEmail(email)
        			.orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        			
        	mockUserId = savedUser.getId();
        	
        	
        	logger.info(">>>userRepository.findByEmail: {}",savedUser.toString());
        	
            logger.info("Id de Usuario consultado. mockUserId: " + mockUserId);

        	
        	assertEquals(savedUser.getEmail(),email);
        	    	
        	LoginRequest loginRequest = new LoginRequest(savedUser.getEmail(),password);     	
            
            logger.info("Datos mapeado loginRequest: {}",loginRequest);
            
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
        			.contentType(MediaType.APPLICATION_JSON)    			
        			.content(objectMapper.writeValueAsString(loginRequest)))
        			.andExpect(status().isOk())
        			.andReturn();
            
            String response = result.getResponse().getContentAsString();
            
            logger.info("Datos mapeados response:{}",response);
            
            jwtToken = JsonPath.read(response, "$.accessToken");
            
            logger.info("jwtToken:{}",jwtToken);   
                
        }
        
        
        @Test
        @Disabled
        public void login_success() throws Exception {

            
        }

        @Test       
        @WithMockUser(roles = {"USER"})// Simula un usuario autenticado con rol "USER"
        public void testRegisterTransactionEndpoint() throws Exception {
        	
    		logger.info("=== Ejecutando método testRegisterTransactionEndpoint() desde UserControllerIntegrationTest ===");

        	
            logger.info("jwtToken:{}",jwtToken);

        	
        	testUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + email));
                 			

                logger.info("Cosulta un UUID de user: " + testUser.getId());

                // Crear y guardar una entrada de portafolio para el usuario.
                /**/
                PortfolioEntry portfolioEntry = new PortfolioEntry(
                		UUID.randomUUID(), 
        				testUser, 
        				"BTC", 
        				"CRYPTO", 
        				BigDecimal.valueOf(1), 
        				BigDecimal.valueOf(89000), 
        				BigDecimal.valueOf(89000), 
        				null, 
        				null, 
        				null, 
        				LocalDateTime.now(),  
        				LocalDateTime.now(),  
        				Long.valueOf(1));
                 portfolioEntryRepository.save(portfolioEntry);
                 
                logger.info("Datos mapeados para PortfolioEntry: {}", portfolioEntry);
                 
                logger.info("Datos mapeados para Usuario de prueba creado : {}", testUser);

                // Construir el TransactionRequest usando el ID real del usuario y de la entrada
                // de portafolio.
                TransactionRequest request = new TransactionRequest(                            
                		portfolioEntry.getPortfolioEntryId(), // Se envía el ID de la entrada existente
                                "ETH",
                                "CRYPTO",
                                "BUY",
                                BigDecimal.valueOf(5),
                                BigDecimal.valueOf(2000), // princePerUnit
                                BigDecimal.valueOf(10000), // totalValue
                                LocalDateTime.now(),
                                BigDecimal.ZERO,
                                "Comprar ETH");
                
                logger.info("Datos mapeados de TransactionRequest: {}",request);
                                
                logger.info("Cosulta un UUID de user: {}", mockUserId);

                // Configurar un Authentication con el UUID como principal
                Authentication auth = new org.springframework.security.authentication.TestingAuthenticationToken(mockUserId, null, "ROLE_USER");


                // Realizar la solicitud POST al endpoint
                ResultActions result = mockMvc.perform(post("/api/v1/portfolio/transactions")
                        		.with(SecurityMockMvcRequestPostProcessors.authentication(auth)) // Establecer el Authentication
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                               .andDo(res -> logger.info("Respuesta del endpoint: {}",
                                                res.getResponse().getContentAsString()))                               
                                .andExpect(status().isCreated());

                String jsonResponse = result.andReturn().getResponse().getContentAsString();

                
                // Deserializar la respuesta a TransactionResponse
                TransactionResponse response = objectMapper.readValue(jsonResponse, TransactionResponse.class);
                assertNotNull(response);
                assertEquals(testUser.getId(), response.userId());
/*
                // Verificar en la base de datos que la transacción se guardó.
                // Como el record TransactionResponse no tiene un campo 'user' (sólo userId),
                // se valida utilizando el campo userId en la respuesta.
                List<TransactionResponse> transactions = transactionRepository.findByUserId(testUser.getId());
                assertFalse(transactions.isEmpty(), "Debe existir al menos una transacción para el usuario");
                // Como TransactionResponse solo tiene userId, validamos ese campo:
                TransactionResponse savedTransaction = transactions.get(0);
                assertNotNull(savedTransaction.userId(),
                                "El campo 'userId' en la transacción guardada no debe ser nulo");
                assertEquals(testUser.getId(), savedTransaction.userId(),
                                "El user_id de la transacción debe coincidir con el ID del usuario de prueba");
*/
        }

}
