package com.mx.cryptomonitor.user.infrastructure.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

  @Autowired private UserRepository userRepository;

  @Autowired private UserDetailsService userDetailsService;

  @Autowired private JwtTokenUtil jwtTokenUtil;

  @Autowired private SessionRepository sessionRepository;

  @Value("${springdoc.api-docs.enabled:false}")
  private boolean springdocApiDocsEnabled;

  @Value("${springdoc.swagger-ui.enabled:false}")
  private boolean springdocSwaggerUiEnabled;

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  // Lista de endpoints públicos que no requieren autenticación
  private static final List<String> BASE_PUBLIC_ENDPOINTS =
      List.of(
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout",
          "/api/v1/users/register",
          "/api/v1/users/password/reset",
          "/api/v1/users/email/verify",
          "/api/v1/users/public/test-get",
          "/api/v1/users/public/test-post",
          "/api/v1/marketdata/**",
          "/api/v1/crypto/**",
          "/api/v1/assets",
          "/api/v1/assets/search",
          "/api/v1/health",
          "/actuator/health/**",
          "/actuator/prometheus",
          "/actuator/info",
          "/error");

  private static final List<String> SPRINGDOC_PUBLIC_ENDPOINTS =
      List.of("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html");

  private boolean isPublic(String uri) {
    List<String> publicEndpoints = new ArrayList<>(BASE_PUBLIC_ENDPOINTS);
    if (springdocApiDocsEnabled || springdocSwaggerUiEnabled) {
      publicEndpoints.addAll(SPRINGDOC_PUBLIC_ENDPOINTS);
    }
    return publicEndpoints.stream().anyMatch(p -> pathMatcher.match(p, uri));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestURI = request.getRequestURI();

    // Si la solicitud es para un endpoint público, no validar token y continuar
    if (isPublic(requestURI)) {
      logger.debug("Solicitud a endpoint público: {}. No se requiere autenticación.", requestURI);
      chain.doFilter(request, response);
      return;
    }

    // Lógica para endpoints protegidos
    final String authorizationHeader = request.getHeader("Authorization");

    String username = null;
    String jwt = null;

    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      jwt = authorizationHeader.substring(7);
      try {
        username = jwtTokenUtil.getUsernameFromToken(jwt);
      } catch (ExpiredJwtException e) {
        logger.warn("Token expirado para ruta: {}", requestURI);
      } catch (JwtException e) {
        // Cubre UnsupportedJwtException (HS256 vs RS256), MalformedJwtException,
        // SignatureException.
        // No re-lanzar: username queda null y Spring Security emite 401 limpio.
        logger.warn("Token JWT inválido para ruta {}: {}", requestURI, e.getMessage());
      } catch (IllegalArgumentException e) {
        logger.error("No se pudo obtener el nombre de usuario del token", e);
      }
    } else {
      // Sin header Authorization: no hay credenciales que procesar.
      // Con SessionCreationPolicy.STATELESS no existe sesión que pueda autenticar
      // este request. Spring Security evaluará la regla hasRole('USER') y emitirá
      // el 401 vía JwtAuthenticationEntryPoint al no encontrar autenticación en el contexto.
      logger.warn(
          "El encabezado Authorization no contiene un token JWT válido para la ruta: {}",
          requestURI);
    }

    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

      // Validar el token contra los detalles del usuario
      if (jwtTokenUtil.validateToken(jwt, userDetails)) {

        // Extraer el session_id del token
        Claims claims = jwtTokenUtil.getClaimsFromToken(jwt);

        String sessionIdStr = claims.get("session_id", String.class);
        String userId = claims.getSubject();
        Optional<User> user = userRepository.findByEmail(userId);

        // UUID uuid = UUID.fromString(user.get().getId());

        if (sessionIdStr == null) {
          logger.error("Token inválido: falta session_id en el token JWT");
          response.sendError(
              HttpServletResponse.SC_UNAUTHORIZED, "Token inválido: falta session_id");
          return;
        }

        // Validar la sesión
        try {
          UUID sessionId = UUID.fromString(sessionIdStr);
          Session session =
              sessionRepository
                  .findById(sessionId)
                  .orElseThrow(
                      () -> new IllegalStateException("Sesión no encontrada: " + sessionIdStr));

          if (!session.isActive()) {
            logger.error("Sesión inválida o expirada para session_id: {}", sessionIdStr);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión inválida o expirada");
            return;
          }

          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());

          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

          SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (IllegalArgumentException e) {
          logger.error("Formato inválido para session_id: {}", sessionIdStr, e);
          response.sendError(
              HttpServletResponse.SC_UNAUTHORIZED, "Formato inválido para session_id");
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
