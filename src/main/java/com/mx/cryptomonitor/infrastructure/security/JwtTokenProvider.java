package com.mx.cryptomonitor.infrastructure.security;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtTokenProvider {
	
	@Value("${security.jwt.token.secret-key:secret}")
	private String secretKey;
	
	@Value("${security.jwt.token.expire-length:3600000}")
	private long validityInMilliseconds;
	
	public String createToken(String email) {
		
		Claims claims = Jwts.claims().setSubject(email);
		Date now = new Date();
		Date validity = new Date(now.getTime()+validityInMilliseconds);
		
		return Jwts.builder()
				.setClaims(claims)
				.setIssuedAt(now)
				.setExpiration(validity)
				.signWith(SignatureAlgorithm.HS256, secretKey)
				.compact();
	}
	
	public boolean validateToken(String token) {
		
		try {
			Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
			return true;
			
		} catch (Exception e) {
			// TODO: handle exception
			return false;
		}
		
		}
	
	public String getEmail(String token) {
		return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
	}
	
}
