package com.mx.cryptomonitor.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Transactional
class UserServiceTest {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceTest.class);


	@Mock
	private UserRepository userRepository;

    @InjectMocks
    private UserService userService;
    
    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private UUID userId;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.fromString("bc1c7eb7-1814-43e7-a46d-00ff0d1676ad");

        userService = new UserService(userRepository);
        
    }

    @Test
    void deleteUserById_ShouldDeleteUser_WhenUserExists() {
    	
        logger.info("=== Ejecutando método deleteUserById_ShouldDeleteUser_WhenUserExists() desde UserServiceTest ===");

        User user = new User();
        user.setId(userId);
        user.setUsername("testUser");
        user.setEmail("test@example.com");

        // Mockear repositorio
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUserById(userId);

        verify(userRepository, times(1)).deleteById(userId);
    }

    @Test
    void deleteUserById_ShouldThrowException_WhenUserDoesNotExist() {
    	
        logger.info("=== Ejecutando método deleteUserById_ShouldThrowException_WhenUserDoesNotExist() desde UserServiceTest ===");

    	
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUserById(userId));
        verify(userRepository, never()).deleteById(userId);
    }
    


    @Test
    void findById_ShouldReturnUser_WhenUserExists() {
    	
        logger.info("=== Ejecutando método findById_ShouldReturnUser_WhenUserExists() desde UserServiceTest ===");

    	
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("johndoe");

        // Mock behavior
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        Optional<User> foundUser = userService.findById(userId);

        // Assert
        assertTrue(foundUser.isPresent(), "El usuario debería existir");
        assertEquals(userId, foundUser.get().getId(), "El ID del usuario encontrado debería coincidir");
    }
    



}