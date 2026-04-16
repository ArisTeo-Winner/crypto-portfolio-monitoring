package com.mx.cryptomonitor.user.infrastructure.inbound.rest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.user.application.dto.request.EmailVerifyRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordChangeRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordResetRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserMeUpdateRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.application.dto.response.UserResponse;
import com.mx.cryptomonitor.user.application.service.UserService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.EmailVerifyRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeDeleteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeReadRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.MeWriteRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordChangeRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.PasswordResetRateLimiter;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.security.UserRegistrationRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(UserController.class);
  private final UserService userService;
  private final UserRegistrationRateLimiter userRegistrationRateLimiter;
  private final PasswordResetRateLimiter passwordResetRateLimiter;
  private final PasswordChangeRateLimiter passwordChangeRateLimiter;
  private final EmailVerifyRateLimiter emailVerifyRateLimiter;
  private final MeReadRateLimiter meReadRateLimiter;
  private final MeWriteRateLimiter meWriteRateLimiter;
  private final MeDeleteRateLimiter meDeleteRateLimiter;

  @Operation(
      summary = "Registrar usuario",
      description = "Registra un nuevo usuario y devuelve la informacion del usuario creado")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Usuario registrado correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "409", description = "Conflicto de registro"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos de registro"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/register")
  public ResponseEntity<UserResponse> registerUser(
      @Valid @RequestBody UserRegistrationRequest request, HttpServletRequest httpRequest) {
    userRegistrationRateLimiter.validateOrThrow(httpRequest);
    UserResponse response = userService.registerUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "Solicitar reset password",
      description = "Inicia flujo de reset de password")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "202", description = "Solicitud aceptada"),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/password/reset")
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
    passwordResetRateLimiter.validateOrThrow(httpRequest);
    userService.requestPasswordReset(request.email());
    return ResponseEntity.accepted().build();
  }

  @Operation(summary = "Cambiar password", description = "Cambia password del usuario autenticado")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Password actualizada"),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/password/change")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody PasswordChangeRequest request,
      HttpServletRequest httpRequest,
      org.springframework.security.core.Authentication authentication) {
    passwordChangeRateLimiter.validateOrThrow(httpRequest);
    String email = authentication != null ? authentication.getName() : null;
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("User not authenticated");
    }
    userService.changePassword(email, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Verificar email", description = "Verifica email usando token")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Email verificado"),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "429", description = "Demasiados intentos"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PostMapping("/email/verify")
  public ResponseEntity<Void> verifyEmail(
      @Valid @RequestBody EmailVerifyRequest request, HttpServletRequest httpRequest) {
    emailVerifyRateLimiter.validateOrThrow(httpRequest);
    userService.verifyEmail(request.token());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Obtener perfil", description = "Retorna el perfil del usuario autenticado")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Perfil obtenido",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(
      HttpServletRequest httpRequest,
      org.springframework.security.core.Authentication authentication) {
    meReadRateLimiter.validateOrThrow(httpRequest);
    String email = authentication != null ? authentication.getName() : null;
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("User not authenticated");
    }
    return ResponseEntity.ok(userService.me(email));
  }

  @Operation(
      summary = "Actualizar perfil",
      description = "Actualiza el perfil del usuario autenticado")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Perfil actualizado",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PutMapping("/me")
  public ResponseEntity<UserResponse> updateMe(
      @Valid @RequestBody UserMeUpdateRequest request,
      HttpServletRequest httpRequest,
      org.springframework.security.core.Authentication authentication) {
    meWriteRateLimiter.validateOrThrow(httpRequest);
    String email = authentication != null ? authentication.getName() : null;
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("User not authenticated");
    }
    return ResponseEntity.ok(userService.updateMe(email, request));
  }

  @Operation(summary = "Eliminar cuenta", description = "Elimina la cuenta del usuario autenticado")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Cuenta eliminada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteMe(
      HttpServletRequest httpRequest,
      org.springframework.security.core.Authentication authentication) {
    meDeleteRateLimiter.validateOrThrow(httpRequest);
    String email = authentication != null ? authentication.getName() : null;
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("User not authenticated");
    }
    userService.deleteMe(email);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Obtener usuario por email", description = "Busca un usuario por su correo")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Usuario encontrado",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
      })
  @GetMapping("/{email}")
  public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
    return userService
        .findByEmail(email)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(
      summary = "Lista la informacion del usuario",
      description = "Retorna lista de usuarios")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Lista de usuario obtenida correctamente")
      })
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserResponse>> getAllUsers() {
    logger.info("=== Ejecutando metodo getAllUsers() desde UserController ===");
    List<UserResponse> users = userService.getAllUsers();
    return ResponseEntity.ok(users);
  }

  @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por su identificador")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Usuario eliminado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @DeleteMapping("/{id}")
  public ResponseEntity<String> deleteUser(@PathVariable UUID id) {
    logger.info("=== Ejecutando metodo deleteUser() desde UserController ===");
    try {
      userService.deleteUserById(id);
      return ResponseEntity.ok("Usuario eliminado exitosamente.");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.status(500).body("Error interno del servidor.");
    }
  }

  @Operation(summary = "Actualizar perfil", description = "Actualiza datos del usuario por email")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Perfil actualizado correctamente",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
      })
  @PutMapping("/profile")
  public ResponseEntity<User> updateUserProfile(
      @RequestParam String email, @RequestBody User updatedUser) {
    try {
      if (email == null || email.isBlank()) {
        return ResponseEntity.badRequest().body(null);
      }
      if (updatedUser == null) {
        return ResponseEntity.badRequest().body(null);
      }

      User updatedProfile = userService.updateUser(email, updatedUser);
      return ResponseEntity.ok(updatedProfile);
    } catch (IllegalArgumentException e) {
      logger.error("Error de validacion: {}", e.getMessage());
      return ResponseEntity.badRequest().body(null);
    } catch (RuntimeException e) {
      logger.error("Error en el servicio: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }
  }

  @Operation(
      summary = "Probar busqueda por id",
      description = "Endpoint tecnico para validar busqueda por id")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Usuario encontrado",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
      })
  @GetMapping("/{id}/test")
  public ResponseEntity<User> testFindById(@PathVariable UUID id) {
    logger.info("ID recibido: {}", id);
    Optional<User> user = userService.findById(id);
    if (user.isPresent()) {
      logger.info("Usuario encontrado: {}", user.get());
      return ResponseEntity.ok(user.get());
    }
    logger.warn("Usuario con ID {} no encontrado", id);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
  }

  @Operation(
      summary = "Health check publico GET",
      description = "Valida disponibilidad del endpoint publico GET")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Endpoint publico disponible")})
  @GetMapping("/public/test-get")
  public ResponseEntity<String> testPublicEndpoint() {
    return ResponseEntity.ok("Endpoint publico GET funcionando");
  }

  @Operation(
      summary = "Health check publico POST",
      description = "Valida disponibilidad del endpoint publico POST")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Endpoint publico disponible")})
  @PostMapping("/public/test-post")
  public ResponseEntity<String> testPublicPost() {
    return ResponseEntity.ok("Endpoint publico POST funcionando");
  }
}
