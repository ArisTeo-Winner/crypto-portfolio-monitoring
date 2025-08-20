package com.mx.cryptomonitor.integration;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.RefreshTokenRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.infrastructure.security.AuthenticationService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;

@SpringBootTest
@ActiveProfiles("test")
class TokenServiceIntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(TokenServiceIntegrationTest.class);
    
    @Autowired
    private  JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;
    
    private User testUser;
    /*
    @Test
    void testDeleteToken() {
    	refreshTokenRepository.deleteAll();
    }*/
    
    @BeforeEach
    void setUp() {
    	testUser = User.builder()
    			.username("leo")
    			.email("leo@example.com")
    			.passwordHash("password123")
    			.firstName("Manzano")
    			.build();
    	
    	testUser = userRepository.save(testUser);
    	
    	logger.info("Datos mapeados. userRepository.save(testUser):{}",testUser);
    	
    }
    
    @Test
	void testSaveTokenTest() {
    	
    	logger.info("---TokenServiceIntegrationTest >>> testSaveTokenTest()---");
    	
    	UUID userId = testUser.getId();
    	
        User user = userRepository.findById(userId)
        		.orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        // 🔹 NO generar un UUID manualmente para PortfolioEntry
        
        logger.info("Cosulta un UUID de user: "+user);
    	
        //User userAut = authenticationService.authenticate("leo@example.com", "password123");

    	String token = jwtTokenUtil.generateRefreshToken(user.getEmail());

        logger.info("🔍 Token Generada: {}", token);
        logger.info("🆔 ID del usuario : {}", userId);
        
    	RefreshToken refreshToken =  RefreshToken.builder()
            .user(user)
            .refreshToken(token)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now())
            .lastUsedAt(LocalDateTime.now())
            .revoked(false)
            .ipAddress("127.0.0.1")
            .userAgent("45345")
    		.build();
    	
        logger.info("🔍 Usuario de Token : " + refreshToken.getUser());
/**/
    	refreshTokenRepository.save(refreshToken);
		
	}
    
}
