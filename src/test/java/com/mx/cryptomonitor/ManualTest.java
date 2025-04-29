package com.mx.cryptomonitor;

import java.util.Optional;
import java.util.UUID;

import org.mockito.Mockito;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;

import io.github.cdimascio.dotenv.Dotenv;

public class ManualTest {

	public static void main(String[] args) {
	    Dotenv dotenv = Dotenv.configure()
	            .directory(".")
	            .ignoreIfMissing()
	            .load();
	    dotenv.entries().forEach(entry -> {
	        System.setProperty(entry.getKey(), entry.getValue());
	        System.out.println("Cargada: " + entry.getKey() + "=" + entry.getValue());
	    });

	}
}
