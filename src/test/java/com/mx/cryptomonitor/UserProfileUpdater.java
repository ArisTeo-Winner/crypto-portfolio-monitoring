package com.mx.cryptomonitor;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;


@Component
public class UserProfileUpdater {


    @Autowired
    private UserRepository userRepository;

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(CryptoPortfolioMonitoringApplication.class, args);
        UserProfileUpdater test = context.getBean(UserProfileUpdater.class);
        test.updateUserProfileInDb();
    }

    @Transactional
    public void updateUserProfileInDb() {
        // Email del usuario a actualizar
        String email = "johndoe@example.com";

        // Buscar usuario por email
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            System.out.println("Usuario antes de la actualización:");
            System.out.println(user);

            // Modificar los datos
            user.setFirstName("John");
            user.setLastName("Doe");
            user.setPhoneNumber("+1234567890");
            user.setAddress("123 Main St");
            user.setCity("New York");
            user.setState("NY");
            user.setPostalCode("10001");
            user.setCountry("USA");
            user.setBio("Crypto enthusiast");
            user.setUpdatedAt(LocalDateTime.now());


            // Guardar los cambios
            userRepository.save(user);

            System.out.println("Usuario después de la actualización:");
            System.out.println(user);
        } else {
            System.out.println("Usuario no encontrado con el email: " + email);
        }
    }}
