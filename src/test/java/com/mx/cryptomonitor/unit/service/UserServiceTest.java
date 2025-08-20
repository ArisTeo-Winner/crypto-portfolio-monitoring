package com.mx.cryptomonitor.unit.service;

import static org.hamcrest.CoreMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.application.mappers.UserMapper;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.shared.dto.response.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
	
    private static final Logger logger = LoggerFactory.getLogger(UserServiceTest.class);
	
	@Mock
	private UserRepository userRepository;
	
	@Mock
	private UserMapper userMapper;
	
	@InjectMocks
	private UserService userService;
	
	
	
	@Test
	void testCreateUSer_valideUser() {
		
		logger.info("Executing test: testCreateUSer_valideUser");
		
		User userNew = new User();
		userNew.setId(UUID.randomUUID());
		userNew.setUsername("Paul_Leo");
		userNew.setEmail("paul@example.com");
		userNew.setPasswordHash("passwordHash");
		userNew.setFirstName("Paul");
		userNew.setLastName("Leo");
		
		when(userRepository.save(userNew)).thenReturn(userNew);
		
		User saveUser = userService.save(userNew);
		
		assertEquals(userNew, saveUser);
		assertEquals("paul@example.com", saveUser.getEmail());
		
		
	}
	
	@Test
	void testGetUserFullName_UserFound() {
		
        logger.info("Executing test: testGetUserFullName_UserFound");
        
        UUID userId = UUID.randomUUID();

		User expectedUser = new User(
				userId, 
				"Paul_Leo", 
				"paul@example.com", 
				"passwordHash", 
				"Paul", 
				"Leo", 
				null, 
				null, 
				null, 
				null, 
				null, 
				null, 
				null, 
				null, 
				null, 
				false, 
				null, 
				null, 
				null, 
				null);
		
		when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));
		
		Optional<User> actualUser =  userService.findById(userId);
		
		assertTrue(actualUser.isPresent());
        assertEquals(expectedUser, actualUser.get());

		
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
    void findByUserEmail_ShouldReturnUser_WhenUserExistes() {
    	
        logger.info("Executing test: findByUserEmail_ShouldReturnUser_WhenUserExistes");
    	
    	UUID userId = UUID.randomUUID();
    	User user = new User();
    	user.setId(userId);
    	user.setUsername("Test");
    	user.setEmail("test@example.com");
    	
    	UserResponse userResponse = new UserResponse("Test", "test@example.com", null, null, null, null, null, null, null, null, null, false, null); 
    	
    	when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    	when(userMapper.toResponse(user)).thenReturn(userResponse);
    	
    	Optional<UserResponse> userbyEmail= userService.findByEmail("test@example.com");    	
    	
    	assertTrue(userbyEmail.isPresent());
    	assertEquals("test@example.com", userbyEmail.get().email());    	    
    }
    
	
    @Test
    void deleteUserById_ShouldDeleteUser_WhenUserExists() {
    	
        logger.info("=== Ejecutando método deleteUserById_ShouldDeleteUser_WhenUserExists() desde UserServiceTest ===");

        UUID userId = UUID.randomUUID();
        
        User user = new User();
        user.setId(userId);
        user.setUsername("testUser");
        user.setEmail("test@example.com");

        // Mockear repositorio
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUserById(userId);

        verify(userRepository, times(1)).deleteById(userId);
    }

}
