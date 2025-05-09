package com.mx.cryptomonitor.application.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.mx.cryptomonitor.application.mappers.UserMapper;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.services.TokenService;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
//import com.mx.cryptomonitor.shared.dto.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.request.RefreshTokenRequest;
import com.mx.cryptomonitor.shared.dto.request.UserRegistrationRequest;

import com.mx.cryptomonitor.shared.dto.response.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.mx.cryptomonitor.shared.dto.response.JwtResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
	
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserController.class);

    
	private final UserRepository userRepository;

    
    private final UserService userService;

    
    private final AuthenticationService authenticationService;
    
    
    private final TokenService tokenService;

    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtUserDetailsService userDetailsService;
/*
    @Autowired
    public UserController(AuthenticationManager authenticationManager, JwtTokenUtil jwtTokenUtil, JwtUserDetailsService userDetailsService, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
    }*/

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        // Registrar al usuario y devolver un UserResponse
        logger.info("📌 Iniciando registro de usuario: {}", request.email());

        UserResponse response = userService.registerUser(request);
        
        logger.info("✅ Usuario registrado correctamente: {}", response.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Lista la información del usuario",
    		description = "Retorna lista de usuarios")
    @ApiResponses(value = {
    		@ApiResponse(responseCode = "200",description = "Lista de usuario obtenida correctamente")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
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
    
    @GetMapping("/public/test")
    public ResponseEntity<String> testPublicEndpoint() {
        return ResponseEntity.ok("Endpoint público funcionando");
    }
    
    @PostMapping("/public/test-post")
    public ResponseEntity<String> testPublicPost() {
        return ResponseEntity.ok("Endpoint público POST funcionando");
    }

    
    
}
