package com.mx.cryptomonitor.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.SessionRepository;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private SessionRepository sessionRepository;


    // Lista de endpoints públicos que no requieren autenticación
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
        "/api/v1/auth/login",
        "/api/v1/users/register",
        "/api/v1/users/refresh",
        "/api/v1/users/public/test",
        "/api/v1/marketdata/**",
        "/swagger-ui/**", 
        "/v3/api-docs/**", 
        "/swagger-ui.html"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // Si la solicitud es para un endpoint público, no validar token y continuar
        if (PUBLIC_ENDPOINTS.contains(requestURI)) {
            logger.debug("Solicitud a endpoint público: {}. No se requiere autenticación.", requestURI);
            chain.doFilter(request, response);
            return;
        }

        // Lógica para endpoints protegidos
        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7); // Extrae el token después de "Bearer "
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwt);
            } catch (IllegalArgumentException e) {
                logger.error("No se pudo obtener el nombre de usuario del token", e);
            } catch (ExpiredJwtException e) {
                logger.warn("El token ha expirado", e);
            }
        } else {
            logger.warn("El encabezado Authorization no contiene un token JWT válido para la ruta: {}", requestURI);
        }
        
        

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // Validar el token contra los detalles del usuario
            if (jwtTokenUtil.validateToken(jwt, userDetails)) {
            	
                // Extraer el session_id del token
                Claims claims = jwtTokenUtil.getClaimsFromToken(jwt);                
                logger.info("Respuesta de >jwtTokenUtil.getClaimsFromToken:{}",claims);

                String sessionIdStr = claims.get("session_id", String.class);
                logger.info("Respuesta >claims.get:{}",sessionIdStr);

                String userId = claims.getSubject();
                logger.info("Respuesta >claimsgetSubject:{}",userId);
                
        		Optional<User> user = userRepository.findByEmail(userId);
        		
        		
                //UUID uuid = UUID.fromString(user.get().getId());

                if (sessionIdStr == null) {
                    logger.error("Token inválido: falta session_id en el token JWT");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido: falta session_id");
                    return;
                }
            	

                // Validar la sesión
                try {
                    UUID sessionId = UUID.fromString(sessionIdStr);
                    Session session = sessionRepository.findById(sessionId)
                        .orElseThrow(() -> new IllegalStateException("Sesión no encontrada: " + sessionIdStr));

                    if (!session.isActive()) {
                        logger.error("Sesión inválida o expirada para session_id: {}", sessionIdStr);
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión inválida o expirada");
                        return;
                    }

                    // Configurar la autenticación en el contexto de seguridad
                    logger.info("Token JWT validado con éxito para el usuario: {}", username);
            	
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                		user.get().getId(), null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                
                } catch (IllegalArgumentException e) {
                    logger.error("Formato inválido para session_id: {}", sessionIdStr, e);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Formato inválido para session_id");
                    return;
                } catch (IllegalStateException e) {
                    logger.error("Sesión no encontrada: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión no encontrada");
                    return;
                }
                
                
            } else {
                logger.warn("Token inválido para el usuario: {}", username);
            }
        }
        
        

        chain.doFilter(request, response);
    }
}
