package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.response.SessionSummaryResponse;
import com.mx.cryptomonitor.user.application.service.SessionService;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/sessions")
@RequiredArgsConstructor
public class UserSessionController {

  private final SessionService sessionService;
  private final UserRepository userRepository;
  private final JwtTokenUtil jwtTokenUtil;

  @GetMapping
  public ResponseEntity<List<SessionSummaryResponse>> listSessions(
      HttpServletRequest request, Authentication authentication) {

    String email = authentication.getName();
    UUID userId =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"))
            .getId();

    UUID currentSessionId = extractSessionId(request);
    return ResponseEntity.ok(sessionService.listActiveSessions(userId, currentSessionId));
  }

  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> revokeSession(
      @PathVariable UUID sessionId, HttpServletRequest request, Authentication authentication) {

    String email = authentication.getName();
    UUID userId =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"))
            .getId();

    UUID currentSessionId = extractSessionId(request);
    sessionService.revokeSession(userId, sessionId, currentSessionId);
    return ResponseEntity.noContent().build();
  }

  private UUID extractSessionId(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    try {
      Object claim = jwtTokenUtil.getClaimsFromToken(authHeader.substring(7)).get("session_id");
      return claim != null ? UUID.fromString(claim.toString()) : null;
    } catch (Exception e) {
      return null;
    }
  }
}
