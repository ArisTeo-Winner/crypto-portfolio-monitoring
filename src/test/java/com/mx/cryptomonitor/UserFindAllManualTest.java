package com.mx.cryptomonitor;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class UserFindAllManualTest {
	    @Autowired
	    private UserRepository userRepository;

	    @Autowired
	    private UserService userService;
/*
	    @Test
	    public void testFindAllUsersDirectlyFromRepository() {
	        System.out.println("=== Ejecutando testFindAllUsersDirectlyFromRepository() desde UserFindAllManualTest ===");
	        List<User> users = userRepository.findAll();
	        System.out.println("Usuarios encontrados desde UserRepository:");
	        if (users != null && !users.isEmpty()) {
	            users.forEach(user -> System.out.println(user));
	        } else {
	            System.out.println("No se encontraron usuarios en UserRepository.");
	        }
	    }


	    
	    @Test
	    void findById_ShouldReturnUser_WhenUserExists() {
	    	
	        System.out.println("=== Ejecutando findById_ShouldReturnUser_WhenUserExists() desde UserFindAllManualTest ===");

	        UUID id = UUID.fromString("9cd1ba3b-3676-4e52-8373-c7cd68492c71");
	        Optional<User> user = userRepository.findById(id);
	        assertTrue(user.isPresent(), "El usuario debería existir");
	    }
	 */
}
