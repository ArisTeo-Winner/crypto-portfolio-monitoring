package com.mx.cryptomonitor;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@SpringBootTest
public class CryptoPortfolioMonitoringApplicationTests {
	
	   @Autowired
	    private UserRepository userRepository;

	@GetMapping("/users/{id}/test")
	public ResponseEntity<User> testFindById(@PathVariable UUID id) {
	    Optional<User> user = userRepository.findById(id);
	    if (user.isEmpty()) {
	        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
	    }
	    return ResponseEntity.ok(user.get());
	}

}
