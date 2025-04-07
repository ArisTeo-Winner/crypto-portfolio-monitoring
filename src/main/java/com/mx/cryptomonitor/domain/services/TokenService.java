package com.mx.cryptomonitor.domain.services;

import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.repositories.RefreshTokenRepository;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {
	
	private final Logger logger = LoggerFactory.getLogger(TokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    
    private final SessionRepository sessionRepository;
	
    private final JwtTokenUtil jwtTokenUtil;
/*
    @Transactional
    public JwtResponse generateTokens(User user, String ipAddress, String userAgent) {
    	
    	logger.info("--TokenService >>> generateTokens()--");
    	
        String accessToken = jwtTokenUtil.generateAccessToken(user, null)
        String refreshTokenValue = jwtTokenUtil.generateRefreshToken(user.getEmail());

        RefreshToken refreshToken = RefreshToken.builder()
        .user(user)
        .refreshToken(refreshTokenValue)
        .createdAt(LocalDateTime.now())
        .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenUtil.getRefreshExpiration() / 1000))
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        .build();
        
        refreshTokenRepository.save(refreshToken);
        
    	logger.info("Respuesta JwtResponse: {}, {}",accessToken,refreshTokenValue);        

        return new JwtResponse(accessToken, refreshTokenValue);
    }

    @Transactional
    public JwtResponse refreshAccessToken(String refreshTokenValue) {
    	
    	logger.info("--TokenService >>> refreshAccessToken()--");
    	logger.info("Refresh Token: {}",refreshTokenValue);

    	
        RefreshToken storedToken = refreshTokenRepository.findByRefreshToken(refreshTokenValue)
            .orElseThrow(() -> new SecurityException("Refresh token inválido"));

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now()) || storedToken.isRevoked()) {
            throw new SecurityException("Refresh token expirado o revocado");
        }
        
        

        String newJti = UUID.randomUUID().toString();
        //String newAccessToken = jwtTokenUtil.generateAccessToken(storedToken.getUser().getEmail());
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(storedToken.getUser().getEmail());

        storedToken.setRevoked(true); // Invalidar el refresh token anterior
        refreshTokenRepository.save(storedToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(storedToken.getUser());
        newToken.setRefreshToken(newRefreshToken);
        //newToken.setAccessTokenJti(newJti);
        newToken.setCreatedAt(LocalDateTime.now());
        newToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenUtil.getRefreshExpiration() / 1000));
        newToken.setIpAddress(storedToken.getIpAddress());
        newToken.setUserAgent(storedToken.getUserAgent());
        refreshTokenRepository.save(newToken);

        return new JwtResponse(newAccessToken, newRefreshToken);
    }
    */
    @Transactional
    public void revokeRefreshToken(String refreshTokenValue) {
    	
    	logger.info("--TokenService >>> revokeRefreshToken()--");

    	logger.info("Refresh Token: {}",refreshTokenValue);

    	
        RefreshToken storedToken = refreshTokenRepository.findByRefreshToken(refreshTokenValue)
            .orElseThrow(() -> new SecurityException("Refresh token no encontrado"));
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
    }
}
