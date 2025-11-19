package com.mx.cryptomonitor.application.controllers;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.infrastructure.security.JwtTokenUtil;
import com.mx.cryptomonitor.infrastructure.security.JwtUserDetailsService;
import com.mx.cryptomonitor.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.shared.dto.response.JwtResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthController {
	
	private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
	private final JwtTokenUtil jwtTokenUtil;

	@GetMapping("/callback")
	public JwtResponse handleGoogleLogin(
			HttpServletRequest request,
			HttpServletResponse response, 
			OAuth2AuthenticationToken authenticatio 
			) {
		log.info("OAuth2 callback triggered: {}", authenticatio.getName());
		/*
		var user = oAuth2AuthenticationSuccessHandler.onAuthenticationSuccess(request, response, authenticatio);
		var user = jwtTokenUtil.generateAccessToken(null, null);
		var user = jwtTokenUtil.generateRefreshToken(null);
		*/
		return new JwtResponse("","");
	}
}
