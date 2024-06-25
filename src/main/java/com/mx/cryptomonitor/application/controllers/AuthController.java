package com.mx.cryptomonitor.application.controllers;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.domain.services.UserService;
import com.mx.cryptomonitor.infrastructure.security.JwtTokenProvider;
import com.mx.cryptomonitor.domain.models.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
	
	@Autowired
	private JwtTokenProvider jwtTokenProvider;
	
	@Autowired
	private UserService userService;
	
	@PostMapping("/login")
	 public ResponseEntity<String> login(@RequestBody Map<String, String> loginData) {
        String email = loginData.get("email");
        String password = loginData.get("password");

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        User user = userOpt.get();
        boolean validPassword = userService.verifyPassword(user, password);

        if (!validPassword) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        String token = jwtTokenProvider.createToken(email);
        return ResponseEntity.ok(token);
    }
			

}
