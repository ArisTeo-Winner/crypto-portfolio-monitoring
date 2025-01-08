package com.mx.cryptomonitor.domain.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

import org.apache.logging.log4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class UserService {
	
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserService.class);

	
	@Autowired
	private UserRepository userRepository;
	private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	
	public List<User> findAll(){
		return userRepository.findAll();
	}
	
	public Optional<User> findByUsername(String username){
		return userRepository.findByUsername(username);
	}
	
    @Transactional
	public Optional<User> findByEmail(String email){
		return userRepository.findByEmail(email);
	}
	

    public User save(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already in use");
        }
        
        String encodedPassword = passwordEncoder.encode(user.getPasswordHash());
        user.setPasswordHash(encodedPassword);
        
        return userRepository.save(user);
    }

	
	public String generateSalt() {
		return Long.toHexString(Double.doubleToLongBits(Math.random()));
	}
	
	public boolean validatePassword(String rawPassword, String encodedPassword) {
	    return passwordEncoder.matches(rawPassword, encodedPassword);
	}

	

	/*public User save(User user) {
		return userRepository.save(user);
	}*/

}
