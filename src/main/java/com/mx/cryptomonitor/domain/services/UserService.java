package com.mx.cryptomonitor.domain.services;

import java.util.List;
import java.util.Optional;

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
	
	public Optional<User> findByEmail(String email){
		return userRepository.findByEmail(email);
	}
	
	public User save(User user) {
		
		user.setSalt(generateSalt());
		user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash() + generateSalt()));
		return userRepository.save(user);
	}
	
	public String generateSalt() {
		return Long.toHexString(Double.doubleToLongBits(Math.random()));
	}
	
	public boolean verifyPassword(User user, String password) {
		String encodedPassword = passwordEncoder.encode(password+user.getSalt());
		return encodedPassword.matches(password + user.getPasswordHash());
	}
	
	
	/*public User save(User user) {
		return userRepository.save(user);
	}*/

}
