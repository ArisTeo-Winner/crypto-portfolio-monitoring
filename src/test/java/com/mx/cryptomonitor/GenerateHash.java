package com.mx.cryptomonitor;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHash {
 
	
	
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "securepassword123";
        String generatedHash = encoder.encode(rawPassword);
        System.out.println("Generated Hash: " + generatedHash);
    }
}
