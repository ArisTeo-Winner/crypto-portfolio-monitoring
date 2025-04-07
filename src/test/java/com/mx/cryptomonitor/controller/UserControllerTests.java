package com.mx.cryptomonitor.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.application.controllers.UserController;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.TokenService;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
import com.mx.cryptomonitor.infrastructure.security.SecurityConfig;
import com.mx.cryptomonitor.infrastructure.security.JwtRequestFilter;
import com.mx.cryptomonitor.shared.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.shared.dto.response.UserResponse;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTests {
	
    @Autowired
    private MockMvc mockMvc;

    
    @Test
    void testRegisterUser() throws Exception {
        String registrationRequestJson = "{\"username\":\"leo_manzano\",\"email\":\"leo@example.com\",\"password\":\"password\"}";

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationRequestJson))
                .andExpect(status().isCreated()); // Ajusta el estado esperado según tu API
    }
    
    /*
    @Test
    public void testRegisterUser() throws Exception {
        // Crear el DTO de solicitud
        UserRegistrationRequest request = new UserRegistrationRequest(
            "testuser",
            "test@example.com",
            "password123",
            "John",
            "Doe",
            null, // phoneNumber
            null, // address
            null, // city
            null, // state
            null, // postalCode
            null, // country
            null  // dateOfBirth
        );

        UserResponse response = new UserResponse(
            "testuser",
            "test@example.com",
            "John",
            "Doe",
            null, // phoneNumber
            null, // address
            null, // city
            null, // state
            null, // postalCode
            null, // country
            null, // dateOfBirth
            true,
            LocalDateTime.now()
        );

        // Simular el comportamiento del servicio
        when(userService.registerUser(any(UserRegistrationRequest.class))).thenReturn(response);

        // Ejecutar la solicitud
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
    
    @Test
    public void testGetUserByEmail_Success() throws Exception {
        // Configurar datos de prueba
        UserResponse userResponse = new UserResponse(
            "Alan_doe", "alan@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        // Configurar comportamientos simulados
        when(userService.findByEmail("alan@example.com")).thenReturn(Optional.of(userResponse));

        // Ejecutar la solicitud HTTP
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/alan@example.com"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("Alan_doe"))
               .andExpect(jsonPath("$.email").value("alan@example.com"));
    }

  
    @Test
    public void testGetAllUsers_Success() throws Exception {
        // Configurar datos de prueba
        UserResponse userResponse1 = new UserResponse(
            "Alan_doe", "alan@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        UserResponse userResponse2 = new UserResponse(
            "Jane_doe", "jane@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        List<UserResponse> userResponses = List.of(userResponse1, userResponse2);

        // Configurar comportamientos simulados
        when(userService.getAllUsers()).thenReturn(userResponses);

        // Ejecutar la solicitud HTTP
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].username").value("Alan_doe"))
               .andExpect(jsonPath("$[1].email").value("jane@example.com"));
    }*/



}
