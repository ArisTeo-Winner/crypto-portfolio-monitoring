package com.mx.cryptomonitor.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

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

	private Boolean isTokenExpired(String token) {
		
		Date expirationDate = extractExpiration(token);
		
		return expirationDate.before(new Date());
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
	
    public String generateToken(User user) {
    	
    	System.out.println("KEY SECRET : "+ Base64.getEncoder().encodeToString(secret.getEncoded()));
    	
        return Jwts.builder()
        		.setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 horas
                .signWith(secret)
                .compact();
    }
	

}
