package com.mx.cryptomonitor.application.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.AuthService;
import com.mx.cryptomonitor.domain.services.TokenService;
import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final Logger logger = LoggerFactory.getLogger(AuthController.class);

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserService userService;

	@Autowired
	private AuthenticationService authenticationService;

	private final TokenService tokenService;

	private final AuthenticationManager authenticationManager;
	private final JwtTokenUtil jwtTokenUtil;
	private final JwtUserDetailsService userDetailsService;

	@Autowired
	public AuthController(AuthService authService, AuthenticationManager authenticationManager,
			JwtTokenUtil jwtTokenUtil, JwtUserDetailsService userDetailsService, TokenService tokenService) {
		this.authService = authService;
		this.authenticationManager = authenticationManager;
		this.jwtTokenUtil = jwtTokenUtil;
		this.userDetailsService = userDetailsService;
		this.tokenService = tokenService;
	}

	/**/
	@PostMapping("/login")
	public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request,
			HttpServletResponse servletresponse) {

		JwtResponse token = authService.login(loginRequest, request);
		/*
		 * if (token == null || token.accessToken().isEmpty()) {
		 * logger.warn("Login failed: token is null or empty"); return
		 * ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials"); }
		 */
		// Crear la cookie con el token
		Cookie tokenCookie = new Cookie("jwt", token.accessToken());
		tokenCookie.setHttpOnly(true); // No accesible desde JavaScript
		tokenCookie.setSecure(true); // Solo se envía a través de HTTPS
		tokenCookie.setPath("/"); // Disponible en toda la aplicación
		tokenCookie.setMaxAge(24 * 60 * 60); // Expira en 1 día

		// Agregar la cookie a la respuesta HTTP
		servletresponse.addCookie(tokenCookie);

		logger.info("User {} logged in successfully", loginRequest.email());
		return ResponseEntity.ok(token);

	}

	/*
	 * Endpoint - Refresh Token para obtener un nuevo Access Toke POST
	 * /api/v1/users/refresh
	 */
	@PostMapping("/refresh")
	public ResponseEntity<JwtResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
		
    	logger.info("--AuthController >>> refreshToken()--");


		if (refreshToken == null || refreshToken.isEmpty()) {
			throw new IllegalArgumentException("Encabezado X-Refresh-Token no proporcionado o vacío");
		}

		JwtResponse refreshJwt = authService.refreshToken(refreshToken);

		return ResponseEntity.ok(refreshJwt);
	}

	/*
	 * Endpoint - Cierra la sesión del usuario actual. POST /api/v1/auth/logout
	 **/
	@PostMapping("/logout")
	public ResponseEntity<String> logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
		logger.info("---AuthController >>> logout");
		logger.info("Refresh Token: {}", refreshToken);

		authService.logout(refreshToken);
		return ResponseEntity.ok("Sesión cerrada exitosamente");
	}

}
