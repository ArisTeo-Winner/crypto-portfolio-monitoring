package com.mx.cryptomonitor;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "securepassword";
        String hashedPassword = "$2a$10$uMeZsvZ1AdpYmVp1G3PDJebKHf1GM.9cOX.YZFHmalZUKgnPvgZ7G";

        boolean matches = encoder.matches(rawPassword, hashedPassword);
        System.out.println("Password matches: " + matches);
    }
}
