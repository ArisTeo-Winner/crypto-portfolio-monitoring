package com.mx.cryptomonitor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.RefreshTokenRepository;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.UserRegistrationRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(value = Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class UserControllerIntegrationTest {
	
	private final Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);
	
	@Autowired
	private MockMvc mockMvc; 	
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    @Autowired
    private SessionRepository sessionRepository;

    private UUID existingUserId;
    
    private String jwtToken;

    @BeforeEach
    public void setUp() throws Exception {
    	
    	logger.info("Inicio de setUp()");
    	
    	/**
    	 * Limpiar los datos antes de cada prueba.
    	 * NOTA. Solo aplica para @ActiveProfiles("test"), 
    	 * puede causar problema con bd local si no ajusta adecuadamente  
    	 */    	    	
    	refreshTokenRepository.deleteAll(); 
    	sessionRepository.deleteAll();
        
    }
    
    
    @Test
    public void registerUser_success() throws Exception {
    	
		logger.info("=== Ejecutando método registerUser_success() desde UserControllerIntegrationTest ===");

    	
    	UserRegistrationRequest request = new UserRegistrationRequest(
    			"testuser", 
    			"testuser@example.com", 
    			"Test@123", 
    			null, null, null, null, null, null, null, null, null);
    	
    	logger.info("Datos mapeados UserRegistrationRequest:{}",request);    	
    	
    	mockMvc.perform(post("/api/v1/users/register")
    			.contentType(MediaType.APPLICATION_JSON)
    			.content(objectMapper.writeValueAsString(request)))
    	.andExpect(status().isCreated())
    	.andDo(mvcResult -> {
            // Imprime el JSON de la respuesta
    		logger.info("Respuesta JSON: " + mvcResult.getResponse().getContentAsString());
        });    	
    	
    }
    /**/
    @Test
    public void login_success() throws Exception {
    	
		logger.info("=== Ejecutando método login_success() desde UserControllerIntegrationTest ===");

    	
        //String loginRequest = "{\"email\":\"alan@example.com\",\"password\":\"securepassword\"}";
    	
    	User  savedUser = userRepository.findByEmail("testuser@example.com")
    			.orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    			
    	existingUserId = savedUser.getId();
    	
    	logger.info(">>>userRepository.findByEmail: {}",savedUser.toString());
    	
    	assertEquals(savedUser.getEmail(),"testuser@example.com");
    	    	
    	LoginRequest loginRequest = new LoginRequest("testuser@example.com","Test@123");     	
        
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
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void deleteUser_ShouldReturn204_WhenUserExists() throws Exception {
    	
    	logger.info("Inicio de deleteUser_ShouldReturn204_WhenUserExists()");
    	/**/
        logger.info("Id de usuario a eliminar:{}",existingUserId);    	
    	
        mockMvc.perform(delete("/api/v1/users/{id}",existingUserId)
                        .header("Authorization", "Bearer "+jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());                                      
    }
    
    /*
    @Test
    public void deleteUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
        UUID nonExistentUserId = UUID.randomUUID();

        mockMvc.perform(delete("/users/" + nonExistentUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void deleteUser_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        mockMvc.perform(delete("/users/" + existingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }*/
}
