package com.mx.cryptomonitor.infrastructure.security;


import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtRequestFilter extends OncePerRequestFilter{
	
    private final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);


	@Autowired
	private JwtUserDetailsService jwtUserDetailsService;
	
	@Autowired
	private JwtTokenUtil jwtTokenUtil;
	

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String email = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            logger.info("JWT extraído: {}", jwt);
            try {
                email = jwtTokenUtil.extractUsername(jwt);
                logger.info("Email extraído del token JWT: {}", email);
            } catch (Exception e) {
                logger.error("Error al extraer el email del token JWT: {}", e.getMessage());
            }
        } else {
            logger.warn("El encabezado Authorization no contiene un token JWT válido.");
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            logger.info("Autenticando usuario con email: {}", email);
            UserDetails userDetails = jwtUserDetailsService.loadUserByUsername(email);

            if (jwtTokenUtil.validateToken(jwt, userDetails)) {
                logger.info("Token JWT validado con éxito para el usuario: {}", email);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                logger.error("Token JWT inválido para el usuario: {}", email);
            }
        }

        chain.doFilter(request, response);
    }


}
