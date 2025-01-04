package com.mx.cryptomonitor.infrastructure.security;

import java.util.Date;
import java.util.function.Function;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtTokenUtil {
	
	private String secret = "your-secret-key";
	
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
	
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder().setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 horas
                .signWith(SignatureAlgorithm.HS256, secret).compact();
    }
	

}
