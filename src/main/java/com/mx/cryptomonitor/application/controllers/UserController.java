package com.mx.cryptomonitor.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.services.UserService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Optional; 

@RestController
@RequestMapping("/users")
public class UserController {

	@Autowired
	private UserService userService;
	
	@PostMapping("/register")
	public ResponseEntity<User> registerUser(@Valid @RequestBody User user){
		return ResponseEntity.ok(userService.save(user));
		
	}
	
	@GetMapping("/{email}")
	public ResponseEntity<User> getUserByEmail(@PathVariable String email){
		
		return userService.findByEmail(email)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
		
	}
	
	@GetMapping
	public ResponseEntity<List<User>> getAllUsers(){
		List<User> users = userService.findAll();
		return ResponseEntity.ok(users);
	}
}
