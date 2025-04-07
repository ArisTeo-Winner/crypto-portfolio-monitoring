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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.application.dtos.UserDTO;
import com.mx.cryptomonitor.application.mappers.UserMapper;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.shared.dto.response.UserResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Transactional
class UserServiceTest {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceTest.class);


	@Mock
	private UserRepository userRepository;
	
	@Mock
	private BCryptPasswordEncoder passwordEncoder;
	
    @Mock
    private UserMapper userMapper;

	
	
    @InjectMocks
    private UserService userService;
    
 
    private UUID userId;
/*

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.fromString("9cd1ba3b-3676-4e52-8373-c7cd68492c71");

        userService = new UserService(userRepository, passwordEncoder, userMapper);
        
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
    

    @Test
    public void testSaveUser() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPasswordHash("password");
        
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(user.getPasswordHash())).thenReturn("encodedPassword");
        when(userRepository.save(user)).thenReturn(user);

        User savedUser = userService.save(user);

        assertNotNull(savedUser);
        assertEquals("encodedPassword", savedUser.getPasswordHash());
        verify(userRepository, times(1)).save(user);
    }
    
 
 
    @Test
    public void testFindByEmail_Success() {
        // Configurar datos de prueba
        User user = new User();
        user.setUsername("Alan_doe");
        user.setEmail("alan@example.com");
        user.setActive(true);

        UserResponse userResponse = new UserResponse(
            "Alan_doe", "alan@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        // Configurar comportamientos simulados
        when(userRepository.findByEmail("alan@example.com")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        // Ejecutar el método a probar
        Optional<UserResponse> result = userService.findByEmail("alan@example.com");

        // Verificar resultados
        assertTrue(result.isPresent());
        assertEquals("Alan_doe", result.get().username());
        assertEquals("alan@example.com", result.get().email());

        // Verificar interacciones con los mocks
        verify(userRepository, times(1)).findByEmail("alan@example.com");
        verify(userMapper, times(1)).toResponse(user);
    }


    @Test
    public void testGetAllUsers_Success() {
        // Configurar datos de prueba
        User user1 = new User();
        user1.setUsername("Alan_doe");
        user1.setEmail("alan@example.com");
        user1.setActive(true);

        User user2 = new User();
        user2.setUsername("Jane_doe");
        user2.setEmail("jane@example.com");
        user2.setActive(true);

        List<User> users = List.of(user1, user2);

        UserResponse userResponse1 = new UserResponse(
            "Alan_doe", "alan@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        UserResponse userResponse2 = new UserResponse(
            "Jane_doe", "jane@example.com", null, null, null, null, null, null, null, null, null, true, null
        );

        // Configurar comportamientos simulados
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.toResponse(user1)).thenReturn(userResponse1);
        when(userMapper.toResponse(user2)).thenReturn(userResponse2);

        // Ejecutar el método a probar
        List<UserResponse> result = userService.getAllUsers();

        // Verificar resultados
        assertEquals(2, result.size());
        assertEquals("Alan_doe", result.get(0).username());
        assertEquals("jane@example.com", result.get(1).email());

        // Verificar interacciones con los mocks
        verify(userRepository, times(1)).findAll();
        verify(userMapper, times(1)).toResponse(user1);
        verify(userMapper, times(1)).toResponse(user2);
    }
*/
}