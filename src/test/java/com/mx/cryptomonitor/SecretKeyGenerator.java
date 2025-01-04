package com.mx.cryptomonitor;

import java.util.Base64;

public class SecretKeyGenerator {
    public static void main(String[] args) {
        String secret = "my_super_secret_key_12345!@#$%^&*()";
        String base64Secret = Base64.getEncoder().encodeToString(secret.getBytes());
        System.out.println("Base64-Encoded Secret Key: " + base64Secret);
    }
}
