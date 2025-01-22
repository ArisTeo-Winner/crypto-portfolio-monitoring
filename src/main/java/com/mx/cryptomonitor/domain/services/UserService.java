package com.mx.cryptomonitor.domain.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import org.slf4j.LoggerFactory;


@Service
public class UserService {
	
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    
	private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	
	public UserService(UserRepository userRepository ) {
		this.userRepository = userRepository;
	}
	
    public UserService() {
		// TODO Auto-generated constructor stub
	}

	// Método para obtener todos los usuarios
    public List<User> getAllUsers() {
        logger.info("=== Ejecutando método getAllUsers() desde UserService ===");
        return userRepository.findAll();
    }

    
	public Optional<User> findByUsername(String username){
		return userRepository.findByUsername(username);
	}
	
	@Transactional
	public void deleteUserById(UUID id) {
        logger.debug("Intentando eliminar usuario con ID: {}", id);
		
        Optional<User> user = userRepository.findById(id);

        if (user.isEmpty()) {
            throw new IllegalArgumentException("El usuario con ID " + id + " no existe.");
        }

		userRepository.deleteById(id);
		logger.info("User with ID {} deleted successfully", id);
	}
	
    @Transactional
	public Optional<User> findByEmail(String email){
		return userRepository.findByEmail(email);
	}
	
    @Transactional
    public User save(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already in use");
        }
        
        String encodedPassword = passwordEncoder.encode(user.getPasswordHash());
        user.setPasswordHash(encodedPassword);
        
        return userRepository.save(user);
    }

	
	public String generateSalt() {
		return Long.toHexString(Double.doubleToLongBits(Math.random()));
	}
	
	public boolean validatePassword(String rawPassword, String encodedPassword) {
	    return passwordEncoder.matches(rawPassword, encodedPassword);
	}

	@Transactional(readOnly = true)
	public Optional<User> findById(UUID id) {
		logger.info("Buscando usuario con ID: {}", id);
	    Optional<User> user = userRepository.findById(id);
	    if (user.isPresent()) {
	    	logger.info("Usuario encontrado: {}", user.get());
	    } else {
	    	logger.warn("No se encontró usuario con ID: {}", id);
	    }
	    return user;
	}
	

	@Transactional
	public User updateUser(String email, User updatedUser) {
	    logger.info("=== Ejecutando método updateUser() desde UserService ===");
	    logger.info("Correo de usuario recibido: " + email);

	    if (email == null || email.isBlank()) {
	        throw new IllegalArgumentException("El correo electrónico no puede ser nulo o vacío.");
	    }

	    Optional<User> optionalUser = userRepository.findByEmail(email);

	    if (optionalUser.isPresent()) {
	        User user = optionalUser.get();

	        logger.info("Usuario encontrado: " + user);

	        user.setFirstName(updatedUser.getFirstName() != null ? updatedUser.getFirstName() : user.getFirstName());
	        user.setLastName(updatedUser.getLastName() != null ? updatedUser.getLastName() : user.getLastName());
	        user.setPhoneNumber(updatedUser.getPhoneNumber() != null ? updatedUser.getPhoneNumber() : user.getPhoneNumber());
	        user.setAddress(updatedUser.getAddress() != null ? updatedUser.getAddress() : user.getAddress());
	        user.setCity(updatedUser.getCity() != null ? updatedUser.getCity() : user.getCity());
	        user.setState(updatedUser.getState() != null ? updatedUser.getState() : user.getState());
	        user.setPostalCode(updatedUser.getPostalCode() != null ? updatedUser.getPostalCode() : user.getPostalCode());
	        user.setCountry(updatedUser.getCountry() != null ? updatedUser.getCountry() : user.getCountry());
	        user.setBio(updatedUser.getBio() != null ? updatedUser.getBio() : user.getBio());
	        user.setUpdatedAt(LocalDateTime.now());

	        return userRepository.save(user);
	    } else {
	        logger.error("Usuario no encontrado con el correo: " + email);
	        throw new RuntimeException("Usuario no encontrado con el correo: " + email);
	    }
	}



	public void setUserRepository(UserRepository userRepository) {
	    this.userRepository = userRepository;
	}


}
