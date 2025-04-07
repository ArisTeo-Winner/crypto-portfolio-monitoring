package com.mx.cryptomonitor.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.domain.models.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenUtil {
	
	//private String secret = "your-secret-key";
    private final SecretKey secret = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

	
	public String extractUsername(String token) {
		return extractClain(token, Claims::getSubject);
		
	}

	private <T> T extractClain(String token, Function<Claims, T> claimsResolver) {
		// TODO Auto-generated method stub
		
		final Claims claims = extractAllClaims(token);
		
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		// TODO Auto-generated method stub
		return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
		
	}

	public Boolean isTokenExpired(String token) {
		
        return Jwts.parserBuilder()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
	}
	
	private Date extractExpiration(String token) {
		// TODO Auto-generated method stub
		return extractClain(token, Claims::getExpiration);
	}

	public boolean validateToken(String token, UserDetails userDetails) {
		// TODO Auto-generated method stub
		final String username = extractUsername(token);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
	}
	/*
    public String generateToken(User user, UUID sessionId) {
    	
    	System.out.println("KEY SECRET : "+ Base64.getEncoder().encodeToString(secret.getEncoded()));
    	
        Map<String, Object> claims = new HashMap<>();
        claims.put("session_id", sessionId.toString());
    	
        return Jwts.builder()
        		.setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration)) // 10 horas
                .signWith(secret)
                .compact();
    }*/
    
    public String generateAccessToken(String email, UUID sessionId) {
    	
        Map<String, Object> claims = new HashMap<>();
        claims.put("session_id", sessionId.toString()); // Agregar session_id al payload
        
    	
        return Jwts.builder()
        	.setClaims(claims)
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(secret)
            .compact();
    }

    public String generateRefreshToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(secret)
            .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(secret)
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }
    
    // Obtener los claims del token
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(secret)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
    


    // Obtener el email (subject) del token
    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }


 
    
    public long getAccessExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshExpiration() {
        return refreshTokenExpiration;
    }
	

}
