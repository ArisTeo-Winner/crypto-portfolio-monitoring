package com.mx.cryptomonitor.application.controllers;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/oauth2")
public class OAuth2AliasController {

	@GetMapping("/authorize/google")
	public void startGoogle(HttpServletResponse response) throws IOException {
		response.sendRedirect("/oauth2/authorization/google");
	}
	
	public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails me, HttpServletRequest req){
		
		
		return null;
	}
}
