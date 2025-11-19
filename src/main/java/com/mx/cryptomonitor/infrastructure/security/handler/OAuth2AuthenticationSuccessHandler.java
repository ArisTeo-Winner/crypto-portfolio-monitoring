package com.mx.cryptomonitor.infrastructure.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.domain.models.RefreshToken;
import com.mx.cryptomonitor.domain.models.Session;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.AuthService;
import com.mx.cryptomonitor.domain.services.SessionService;
import com.mx.cryptomonitor.domain.services.TokenService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.oauth.AuthenticatedUserPrincipal;
import com.mx.cryptomonitor.infrastructure.security.oidc.CustomOidcUserService;
import com.mx.cryptomonitor.infrastructure.security.oidc.CustomOidcUserService.DomainOidcUser;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final AuthService authService;
	private final ObjectMapper objectMapper;
	
	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		// TODO Auto-generated method stub
	
		try {
			if (!(authentication.getPrincipal() instanceof DomainOidcUser principal)) {
				log.error("Principal inesperado : {}", authentication.getClass());
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Principal inesperado");
				return;				
			}
			
			User user = principal.getDomainUser();
			user.setLastLogin(LocalDateTime.now());
			userRepository.save(user);
			
			JwtResponse jwt = authService.issueTokensForUser(user, request);
			
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			Map<String, Object> body = new HashMap<>();
			body.put("accessToken", jwt.accessToken());
			body.put("refreshToken", jwt.refreshToken());
			objectMapper.writeValue(response.getOutputStream(), body);
			
		} catch (Exception e) {
			// TODO: handle exception
			log.error("Error en success handler", e);
			try {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error al emitir token");
			} catch (Exception ex) {
				// TODO: handle exception
				
			}
		}
	}
}
