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

@Service
@Transactional
public class UserService {
	
	@Autowired
	private UserRepository userRepository;
	private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	
	public List<User> findAll(){
		return userRepository.findAll();
	}
	
	public Optional<User> findByUsername(String username){
		return userRepository.findByUsername(username);
	}
	
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

        String salt = generateSalt();
        user.setSalt(salt);
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash() + salt));
        return userRepository.save(user);
    }

	
	public String generateSalt() {
		return Long.toHexString(Double.doubleToLongBits(Math.random()));
	}
	
	public boolean verifyPassword(User user, String password) {
		//String encodedPassword = passwordEncoder.encode(password+user.getSalt());
		return passwordEncoder.matches(password + user.getSalt(), user.getPasswordHash());
	}
	

	/*public User save(User user) {
		return userRepository.save(user);
	}*/

}
