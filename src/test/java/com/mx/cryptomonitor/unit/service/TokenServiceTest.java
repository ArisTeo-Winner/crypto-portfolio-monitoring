package com.mx.cryptomonitor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.RefreshTokenRepository;
import com.mx.cryptomonitor.domain.services.TokenService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

	private final Logger logger = LoggerFactory.getLogger(TokenServiceTest.class);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @InjectMocks
    private TokenService tokenService;
    
/*
    @Test
    void testRefreshAccessToken_validToken() {
    	
    	logger.info("---TokenServiceTest >>> testRefreshAccessToken_validToken()---");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshToken("refresh-token");
        refreshToken.setUser(new User());
        refreshToken.getUser().setEmail("test@example.com");
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        //when(jwtTokenUtil.generateAccessToken("test@example.com")).thenReturn("new-access-token");

        var response = tokenService.revokeRefreshToken(null);
        
    	logger.info("Respuesta response; {}, {}",response.accessToken(),response.refreshToken());

        
        assertEquals("new-access-token", response.accessToken());
      //  assertEquals("refresh-token", response.refreshToken());
    }
    
    @Test
    void testRefreshAccessToken_expiredToken() {

    	logger.info("---TokenServiceTest >>> testRefreshAccessToken_expiredToken()---");
    	
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setRefreshToken("refresh-token");
        refreshToken.setExpiresAt(LocalDateTime.now().minusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByRefreshToken("refresh-token")).thenReturn(Optional.of(refreshToken));

        assertThrows(SecurityException.class, () -> tokenService.refreshAccessToken("refresh-token"));
    }*/

}
