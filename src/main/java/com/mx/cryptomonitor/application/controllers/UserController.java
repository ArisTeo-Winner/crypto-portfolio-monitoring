package com.mx.cryptomonitor.application.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.application.mappers.UserMapper;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
//import com.mx.cryptomonitor.shared.dto.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.UserRegistrationRequest;

import com.mx.cryptomonitor.shared.dto.response.UserResponse;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
	
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
	private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationService authenticationService;
    

    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtUserDetailsService userDetailsService;

    @Autowired
    public UserController(AuthenticationManager authenticationManager, JwtTokenUtil jwtTokenUtil, JwtUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        // Registrar al usuario y devolver un UserResponse
        UserResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest loginRequest) {
        // Autenticar al usuario
        User user = authenticationService.authenticate(loginRequest.email(), loginRequest.password());

        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.email());

        // Generar token JWT
        String token = jwtTokenUtil.generateToken(user);
        
        // Retornar el token como respuesta
        return ResponseEntity.ok(new JwtResponse(token));
    }
    
    
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        logger.info("=== Ejecutando método getAllUsers() desde UserController ===");

        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable UUID id){
        logger.info("=== Ejecutando método deleteUser() desde UserController ===");

        try {
			
    	userService.deleteUserById(id);
    	return ResponseEntity.ok("Usuario eliminado exitosamente.");
    	
        } catch (IllegalArgumentException e){
        	return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
			// TODO: handle exception
        	return ResponseEntity.status(500).body("Error interno del servidor.");
		}
    }
  

    @PutMapping("/profile")
    public ResponseEntity<User> updateUserProfile(@RequestParam String email, @RequestBody User updatedUser) {
        try {
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(null);
            }
            if (updatedUser == null) {
                return ResponseEntity.badRequest().body(null);
            }

            User updatedProfile = userService.updateUser(email, updatedUser);
            return ResponseEntity.ok(updatedProfile);

        } catch (IllegalArgumentException e) {
            logger.error("Error de validación: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);

        } catch (RuntimeException e) {
            logger.error("Error en el servicio: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    

    @GetMapping("/{id}/test")
    public ResponseEntity<User> testFindById(@PathVariable UUID id) {
    	logger.info("ID recibido: {}", id);
        Optional<User> user = userService.findById(id);
        if (user.isPresent()) {
        	logger.info("Usuario encontrado: {}", user.get());
            return ResponseEntity.ok(user.get());
        } else {
        	logger.warn("Usuario con ID {} no encontrado", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    
    
}
