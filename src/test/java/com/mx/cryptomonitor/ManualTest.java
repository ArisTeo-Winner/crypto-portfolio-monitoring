package com.mx.cryptomonitor;

import java.util.Optional;
import java.util.UUID;

import org.mockito.Mockito;

import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.domain.repositories.UserRepository;
import com.mx.cryptomonitor.domain.services.UserService;

public class ManualTest {

    public static void main(String[] args) {
        UserRepository mockRepository = Mockito.mock(UserRepository.class);

        // Simula un usuario existente
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("johndoe@example.com")
                .firstName("OldFirstName")
                .lastName("OldLastName")
                .build();
        Mockito.when(mockRepository.findByEmail("johndoe@example.com"))
               .thenReturn(Optional.of(existingUser));
/*
        // Crear UserService con el mock
        UserService userService = new UserService();
        userService.setUserRepository(mockRepository);

        // Simular datos actualizados
        User updatedUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        // Llamar al método
        User result = userService.updateUser("johndoe@example.com", updatedUser);
        System.out.println("Updated User: " + result);*/
    }
}
