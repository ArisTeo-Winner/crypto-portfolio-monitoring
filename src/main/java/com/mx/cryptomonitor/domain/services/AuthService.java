package com.mx.cryptomonitor.domain.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.RefreshTokenRepository;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.exceptions.AuthenticationException;
import com.mx.cryptomonitor.infrastructure.exceptions.InvalidTokenException;
import com.mx.cryptomonitor.infrastructure.exceptions.SessionNotFoundException;
import com.mx.cryptomonitor.infrastructure.exceptions.UserNotFoundException;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
import com.mx.cryptomonitor.shared.dto.request.LoginRequest;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final Logger logger = LoggerFactory.getLogger(AuthService.class);


	private final UserRepository userRepository;
	
	private final RefreshTokenRepository refreshTokenRepository;
	
	private final AuthenticationService authenticationService;
	
	private final SessionRepository sessionRepository;
	
    private final JwtUserDetailsService userDetailsService;

    private final TokenService tokenService;
    
    private final JwtTokenUtil jwtTokenUtil;

    
	/**/
    @Transactional
    public JwtResponse login(LoginRequest LoginRequest, HttpServletRequest request) {
    	
    	logger.info("--AuthService >>> login({},{})",LoginRequest.email(), LoginRequest.password());

    	
        User user = userRepository.findByEmail(LoginRequest.email())
            .orElseThrow(() -> new AuthenticationException("Credenciales inválidas"));

        // Autenticar al usuario
         user = authenticationService.authenticate(LoginRequest.email(), LoginRequest.password());

         //JwtResponse refreshToken  = refreshAccessToken(String refreshTokenValue);
         
         UserDetails userDetails = userDetailsService.loadUserByUsername(LoginRequest.email());

     	
         String ipAddress = request.getHeader("X-Forwarded-For");
         if (ipAddress == null || ipAddress.isEmpty()) {
             ipAddress = request.getRemoteAddr();
         }
         String userAgent = request.getHeader("User-Agent");
         
         
         // Generar token de refresco
         String refreshTokenValue = jwtTokenUtil.generateRefreshToken(LoginRequest.email());
         
         //Registra Token
         RefreshToken rt = new RefreshToken();
         rt.setUser(user);
         rt.setRefreshToken(refreshTokenValue);
         rt.setCreatedAt(LocalDateTime.now());
         rt.setExpiresAt(LocalDateTime.now().plusSeconds(604800));
         rt.setIpAddress(ipAddress);
         rt.setUserAgent(userAgent);
         refreshTokenRepository.save(rt);

         // Crear sesión
         Session session = new Session();
         session.setUser(user);
         session.setLoginTime(LocalDateTime.now());
         session.setActive(true);
         session.setRefreshTokenId(rt.getId());
         sessionRepository.save(session);
         
         // Actualizar last_login
         user.setLastLogin(LocalDateTime.now());
         userRepository.save(user);

         // Generar JWT
         String accessToken = jwtTokenUtil.generateAccessToken(LoginRequest.email(), session.getSessionId());

        //String accessToken = jwtTokenUtil.generateToken(user, session.getSessionId());
        //roleService.logRoleEvent(user.getId(), null, "LOGIN", "Inicio de sesión exitoso", ipAddress, userAgent);
        return new JwtResponse(accessToken,refreshTokenValue);
    }
	
    @Transactional
    public void logout(String refreshTokenValue) {
        logger.info("---AuthService >>> logout");
        logger.info("Iniciando cierre de sesión. Refresh Token: {}",refreshTokenValue);
        RefreshToken refreshToken = refreshTokenRepository.findByRefreshToken(refreshTokenValue)
            .orElseThrow(() -> {
                logger.warn("Token de refresco no encontrado: {}", refreshTokenValue);
                return new InvalidTokenException("Token de refresco inválido");
            });

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Token ya revocado o expirado: {}", refreshTokenValue);
            throw new InvalidTokenException("Token ya revocado o expirado");
        }

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);
        logger.info("Token de refresco revocado: {}", refreshTokenValue);

        Session session = sessionRepository.findByRefreshTokenId(refreshToken.getId())
            .orElseThrow(() -> {
                logger.warn("Sesión no encontrada para refreshToken: {}", refreshTokenValue);
                return new InvalidTokenException("Sesión no encontrada");
            });
        session.setActive(false);
        session.setLogoutTime(LocalDateTime.now());
        sessionRepository.save(session);
        logger.info("Sesión cerrada para usuario: {}", refreshToken.getUser());
    }   
    
    @Transactional
    public JwtResponse refreshToken(String refreshTokenValue) {
    	
        logger.info("---AuthService >>> refreshToken");
        logger.info("Iniciando Refresh Token: {}",refreshTokenValue);

    	
        RefreshToken refreshToken = refreshTokenRepository.findByRefreshToken(refreshTokenValue)
            .orElseThrow(() -> new InvalidTokenException("Token de refresco inválido"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token revocado o expirado");
        }

        User user = userRepository.findById(refreshToken.getUser().getId())
            .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        Session session = sessionRepository.findByRefreshTokenId(refreshToken.getId())
            .orElseThrow(() -> new SessionNotFoundException("Sesión no encontrada"));
        
        closeSession(session.getSessionId());

        //String newAccessToken = jwtTokenUtil.generateAccessToken(user.getEmail(), session.getSessionId());
        
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshToken.setRevoked(true); // Invalidar el refresh token anterior
        refreshTokenRepository.save(refreshToken); 
        
        
        // Generar token de refresco
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(user.getEmail());
        
        //Registra Token
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setRefreshToken(newRefreshToken);
        rt.setCreatedAt(LocalDateTime.now());
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(604800));
        refreshTokenRepository.save(rt);

        // Crear sesión
        session = new Session();
        session.setUser(user);
        session.setLoginTime(LocalDateTime.now());
        session.setActive(true);
        session.setRefreshTokenId(rt.getId());
        sessionRepository.save(session);

        // Generar JWT
        String newAccessToken = jwtTokenUtil.generateAccessToken(user.getEmail(), session.getSessionId());
        
        //auditLogService.logEvent(user.getId(), "TOKEN_REFRESHED", "Token de acceso renovado desde " + ipAddress);
        

        
        return new JwtResponse(newAccessToken, newRefreshToken);
    }
    
    public Session createSession(User user) {
        Session session = new Session();
        session.setUser(user);
        session.setLoginTime(LocalDateTime.now());
        session.setActive(true);
        session.setRefreshTokenId(null);
        return sessionRepository.save(session);
    }

    @Transactional
    public void closeSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
        session.setLogoutTime(LocalDateTime.now());
        session.setActive(false);
        sessionRepository.save(session);
    }

    
	
	
	/*
    @Transactional
    public void logout(String refreshTokenValue) {
        // Buscar y validar el token de refresco
        RefreshToken refreshToken = refreshTokenRepository.findByRefreshToken(refreshTokenValue)
        		.orElseThrow(() -> new InvalidTokenException("Token de refresco inválido"));
        

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token de refresco ya revocado o expirado");
        }

        // Revocar el token de refresco
        refreshToken.setRevoked(true);
        //refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        // Invalidar la sesión asociada
        Session session = sessionRepository.findByRefreshTokenId(refreshToken.getId())
            .orElseThrow(() -> new SessionNotFoundException("Sesión no encontrada"));
        session.setActive(false);
        session.setLogoutTime(LocalDateTime.now());
        sessionRepository.save(session);
    }*/
}
