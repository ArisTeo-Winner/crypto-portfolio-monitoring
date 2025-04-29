package com.mx.cryptomonitor.infrastructure.configuration;

import com.mx.cryptomonitor.domain.models.Permission;
import com.mx.cryptomonitor.domain.models.Role;
import com.mx.cryptomonitor.domain.repositories.PermissionRepository;
import com.mx.cryptomonitor.domain.repositories.RoleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @PostConstruct
    @Transactional
    public void init() {
        log.info("Inicializando configuración de seguridad...");

        // Crear permisos si no existen
        if (permissionRepository.count() == 0) {
            List<Permission> defaultPermissions = createDefaultPermissions();
            permissionRepository.saveAll(defaultPermissions);
            log.info("Permisos predeterminados creados: {}", defaultPermissions.size());
        }

        // Crear roles si no existen
        if (roleRepository.count() == 0) {
            List<Role> defaultRoles = createDefaultRoles();
            roleRepository.saveAll(defaultRoles);
            log.info("Roles predeterminados creados: {}", defaultRoles.size());
        }

        log.info("Configuración de seguridad inicializada correctamente");
    }

    private List<Permission> createDefaultPermissions() {
        // Permisos para Portafolio
        Permission createPortfolio = new Permission(null, "Crear Portafolio", "PORTFOLIO_CREATE",
                "Permite crear nuevos portafolios");
        Permission readPortfolio = new Permission(null, "Ver Portafolio", "PORTFOLIO_READ", "Permite ver portafolios");
        Permission updatePortfolio = new Permission(null, "Actualizar Portafolio", "PORTFOLIO_UPDATE",
                "Permite actualizar portafolios");
        Permission deletePortfolio = new Permission(null, "Eliminar Portafolio", "PORTFOLIO_DELETE",
                "Permite eliminar portafolios");

        // Permisos para Transacciones
        Permission createTransaction = new Permission(null, "Crear Transacción", "TRANSACTION_CREATE",
                "Permite crear nuevas transacciones");
        Permission readTransaction = new Permission(null, "Ver Transacción", "TRANSACTION_READ",
                "Permite ver transacciones");
        Permission updateTransaction = new Permission(null, "Actualizar Transacción", "TRANSACTION_UPDATE",
                "Permite actualizar transacciones");
        Permission deleteTransaction = new Permission(null, "Eliminar Transacción", "TRANSACTION_DELETE",
                "Permite eliminar transacciones");

        // Permisos para Usuarios
        Permission createUser = new Permission(null, "Crear Usuario", "USER_CREATE", "Permite crear nuevos usuarios");
        Permission readUser = new Permission(null, "Ver Usuario", "USER_READ", "Permite ver usuarios");
        Permission updateUser = new Permission(null, "Actualizar Usuario", "USER_UPDATE",
                "Permite actualizar usuarios");
        Permission deleteUser = new Permission(null, "Eliminar Usuario", "USER_DELETE", "Permite eliminar usuarios");

        return Arrays.asList(
                createPortfolio, readPortfolio, updatePortfolio, deletePortfolio,
                createTransaction, readTransaction, updateTransaction, deleteTransaction,
                createUser, readUser, updateUser, deleteUser);
    }

    private List<Role> createDefaultRoles() {
        // Rol de administrador
        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        adminRole.setDescription("Administrador con acceso completo al sistema");

        // Rol de usuario normal
        Role userRole = new Role();
        userRole.setName("ROLE_USER");
        userRole.setDescription("Usuario con permisos básicos");

        // Rol de solo lectura
        Role viewerRole = new Role();
        viewerRole.setName("ROLE_VIEWER");
        viewerRole.setDescription("Usuario con permisos solo de lectura");

        // Asignar permisos al rol de administrador (todos)
        Set<Permission> adminPermissions = new HashSet<>(permissionRepository.findAll());
        adminRole.setPermissions(adminPermissions);

        // Asignar permisos al rol de usuario (solo lo básico)
        Set<Permission> userPermissions = new HashSet<>();
        permissionRepository.findByCode("PORTFOLIO_CREATE").ifPresent(userPermissions::add);
        permissionRepository.findByCode("PORTFOLIO_READ").ifPresent(userPermissions::add);
        permissionRepository.findByCode("PORTFOLIO_UPDATE").ifPresent(userPermissions::add);
        permissionRepository.findByCode("TRANSACTION_CREATE").ifPresent(userPermissions::add);
        permissionRepository.findByCode("TRANSACTION_READ").ifPresent(userPermissions::add);
        permissionRepository.findByCode("TRANSACTION_UPDATE").ifPresent(userPermissions::add);
        permissionRepository.findByCode("USER_READ").ifPresent(userPermissions::add);
        permissionRepository.findByCode("USER_UPDATE").ifPresent(userPermissions::add);
        userRole.setPermissions(userPermissions);

        // Asignar permisos al rol de visor (solo lectura)
        Set<Permission> viewerPermissions = new HashSet<>();
        permissionRepository.findByCode("PORTFOLIO_READ").ifPresent(viewerPermissions::add);
        permissionRepository.findByCode("TRANSACTION_READ").ifPresent(viewerPermissions::add);
        permissionRepository.findByCode("USER_READ").ifPresent(viewerPermissions::add);
        viewerRole.setPermissions(viewerPermissions);

        return Arrays.asList(adminRole, userRole, viewerRole);
    }
}