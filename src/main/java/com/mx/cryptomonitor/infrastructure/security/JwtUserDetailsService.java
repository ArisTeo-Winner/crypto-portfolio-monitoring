package com.mx.cryptomonitor.infrastructure.security;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

@Service
public class JwtUserDetailsService implements UserDetailsService{
	
	@Autowired
	private UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		// TODO Auto-generated method stub
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: "+ email));
		
		return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPasswordHash(), new ArrayList<>());
	}

}
