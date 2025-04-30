package com.mx.cryptomonitor.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerIntegrationTest {
	/*
	private static final Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);


    @InjectMocks
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private UUID existingUserId;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
        
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setPasswordHash("hashedpassword");
        User savedUser = userRepository.save(user);
        existingUserId = savedUser.getId();
    }
    
    

    @Test
    public void deleteUser_ShouldReturn204_WhenUserExists() throws Exception {
    	
        logger.info("=== Ejecutando método deleteUser_ShouldReturn204_WhenUserExists() desde UserControllerIntegrationTest ===");

        mockMvc.perform(delete("/users/" + existingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    public void deleteUser_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
    	
        logger.info("=== Ejecutando método deleteUser_ShouldReturn404_WhenUserDoesNotExist() desde UserControllerIntegrationTest ===");
    	
        UUID nonExistentUserId = UUID.randomUUID();

        mockMvc.perform(delete("/users/" + nonExistentUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void deleteUser_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
    	
        logger.info("=== Ejecutando método deleteUser_ShouldReturn401_WhenTokenIsInvalid() desde UserControllerIntegrationTest ===");
    	
        mockMvc.perform(delete("/users/" + existingUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }*/
}
