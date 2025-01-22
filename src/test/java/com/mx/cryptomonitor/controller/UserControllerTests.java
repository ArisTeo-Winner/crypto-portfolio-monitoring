package com.mx.cryptomonitor.controller;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import com.mx.cryptomonitor.application.controllers.UserController;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTests {
	
	private static final Logger logger = LoggerFactory.getLogger(UserControllerTests.class);


    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;
    
    @Mock
    private UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }



    @Test
    void testUpdateUserProfile_ValidParameters() {
    	
        logger.info("=== Ejecutando testUpdateUserProfile_ValidParameters() de UserControllerTests ===");
    	
        // Datos de prueba
        String email = "johndoe@example.com";
        User updatedUser = new User();
        updatedUser.setFirstName("John");
        updatedUser.setLastName("Doe");
        updatedUser.setPhoneNumber("+1234567890");
        updatedUser.setAddress("123 Main St");
        updatedUser.setCity("New York");
        updatedUser.setState("NY");
        updatedUser.setPostalCode("10001");
        updatedUser.setCountry("USA");
        updatedUser.setBio("Crypto enthusiast");

        // Usuario actualizado esperado
        User updatedProfile = new User();
        updatedProfile.setEmail(email);
        updatedProfile.setFirstName("John");
        updatedProfile.setLastName("Doe");
        updatedProfile.setPhoneNumber("+1234567890");
        updatedProfile.setAddress("123 Main St");
        updatedProfile.setCity("New York");
        updatedProfile.setState("NY");
        updatedProfile.setPostalCode("10001");
        updatedProfile.setCountry("USA");
        updatedProfile.setBio("Crypto enthusiast");

        // Configurar el mock del servicio
        when(userService.updateUser(eq(email), eq(updatedUser))).thenReturn(updatedProfile);

        // Llamar al controlador
        ResponseEntity<User> response = controller.updateUserProfile(email, updatedUser);

        // Verificar la respuesta
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("John", response.getBody().getFirstName());
        assertEquals("Doe", response.getBody().getLastName());
        assertEquals("+1234567890", response.getBody().getPhoneNumber());
    }



    @Test
    void testUpdateUserProfile_NullEmail() {
    	
        logger.info("=== Ejecutando testUpdateUserProfile_NullEmail() de UserControllerTests ===");
    	
        // Datos de prueba
        String email = null;
        User updatedUser = new User("Nuevo Nombre", "abc@example.com");

        // Llamar al controlador
        ResponseEntity<User> response = controller.updateUserProfile(email, updatedUser);

        // Verificar la respuesta
        assertEquals(400, response.getStatusCodeValue());
        assertNull(response.getBody());
    }

    @Test
    void testUpdateUserProfile_EmptyEmail() {
    	
        logger.info("=== Ejecutando testUpdateUserProfile_EmptyEmail() de UserControllerTests ===");
    	
        // Datos de prueba
        String email = "";
        User updatedUser = new User("Nuevo Nombre", "abc@example.com");

        // Llamar al controlador
        ResponseEntity<User> response = controller.updateUserProfile(email, updatedUser);

        // Verificar la respuesta
        assertEquals(400, response.getStatusCodeValue());
        assertNull(response.getBody());
    }

    @Test
    void testUpdateUserProfile_NullUser() {
    	
        logger.info("=== Ejecutando testUpdateUserProfile_NullUser() de UserControllerTests ===");
    	
        // Datos de prueba
        String email = "johndoe@example.com";
        User updatedUser = null;

        // Llamar al controlador
        ResponseEntity<User> response = controller.updateUserProfile(email, updatedUser);

        // Verificar la respuesta
        assertEquals(400, response.getStatusCodeValue());
        assertNull(response.getBody());
    }

    @Test
    void testUpdateUserProfile_ServiceThrowsException() {
    	
        logger.info("=== Ejecutando testUpdateUserProfile_ServiceThrowsException() de UserControllerTests ===");
    	
        // Datos de prueba
        String email = "johndoe@example.com";
        User updatedUser = new User("Nuevo Nombre", "abc@example.com");

        // Mock del servicio para lanzar excepción
        when(userService.updateUser(anyString(), any(User.class))).thenThrow(new RuntimeException("Error inesperado"));

        // Llamar al controlador
        ResponseEntity<User> response = controller.updateUserProfile(email, updatedUser);

        // Verificar la respuesta
        assertEquals(500, response.getStatusCodeValue());
        assertNull(response.getBody());
    }
}
