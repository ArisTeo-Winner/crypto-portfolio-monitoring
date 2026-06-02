package com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.user.application.dto.request.EmailVerifyRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordChangeRequest;
import com.mx.cryptomonitor.user.application.dto.request.PasswordResetRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserMeUpdateRequest;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

/**
 * Full-stack integration tests for all UserController CRUD endpoints.
 *
 * <p>Runs against real PostgreSQL + Redis containers (via UserModuleIntegrationTest).
 * Each nested class maps to one endpoint group and exhaustively covers: happy paths,
 * authentication/authorization, input validation, response contract, and state isolation.
 */
@DisplayName("UserController — CRUD completo")
class UserControllerCrudIT extends UserModuleIntegrationTest {

  private static final String BASE = "/api/v1/users";
  private static final String STRONG_PASSWORD = "StrongP@ssw0rd!2026";

  @Autowired private UserRepository userRepository;

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /register
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("POST /register")
  class Register {

    @Test
    @DisplayName("201 — registro mínimo (solo campos obligatorios)")
    void register_minimumFields_returns201() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "user_" + suffix, "min_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.username").value("user_" + suffix))
          .andExpect(jsonPath("$.email").value("min_" + suffix + "@test.local"))
          .andExpect(jsonPath("$.active").value(true))
          .andExpect(jsonPath("$.createdAt").isNotEmpty())
          .andExpect(jsonPath("$.updatedAt").doesNotExist())
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("201 — registro completo (todos los campos opcionales)")
    void register_allFields_returns201() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "full_" + suffix,
              "full_" + suffix + "@test.local",
              STRONG_PASSWORD,
              "Aristeo",
              "Ortiz",
              "+52 55 1234 5678",
              "Av. Reforma 500",
              "CDMX",
              "CDMX",
              "06600",
              "México",
              null,
              "MXN",
              "America/Mexico_City");

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.firstName").value("Aristeo"))
          .andExpect(jsonPath("$.lastName").value("Ortiz"))
          .andExpect(jsonPath("$.city").value("CDMX"))
          .andExpect(jsonPath("$.preferredCurrency").value("MXN"))
          .andExpect(jsonPath("$.timezone").value("America/Mexico_City"))
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("201 — currency default USD cuando no se envía preferredCurrency")
    void register_noCurrency_defaultsToUSD() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "def_" + suffix, "def_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      String body =
          mockMvc
              .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(body, "$.preferredCurrency")).isEqualTo("USD");
    }

    @Test
    @DisplayName("201 — timezone default America/Mexico_City cuando no se envía")
    void register_noTimezone_defaultsToMexicoCity() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "tz_" + suffix, "tz_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      String body =
          mockMvc
              .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(body, "$.timezone")).isEqualTo("America/Mexico_City");
    }

    @Test
    @DisplayName("201 — createdAt y updatedAt se persisten al registrar")
    void register_timestampsArePersisted() throws Exception {
      String suffix = suffix();
      String email = "ts_" + suffix + "@test.local";
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "ts_" + suffix, email, STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      LocalDateTime before = LocalDateTime.now();

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isCreated());

      User saved =
          userRepository
              .findByEmailIgnoreCase(email)
              .orElseThrow(() -> new AssertionError("Usuario no persistido"));

      assertThat(saved.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
      assertThat(saved.getUpdatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
    }

    // ── Conflictos ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("409 — email duplicado")
    void register_duplicateEmail_returns409() throws Exception {
      String suffix = suffix();
      String email = "dup_" + suffix + "@test.local";
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "dup1_" + suffix, email, STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);
      UserRegistrationRequest req2 =
          new UserRegistrationRequest(
              "dup2_" + suffix, email, STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc.perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isCreated());
      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req2)))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.errorCode").value("REGISTRATION_CONFLICT"));
    }

    @Test
    @DisplayName("409 — username duplicado")
    void register_duplicateUsername_returns409() throws Exception {
      String suffix = suffix();
      String username = "same_user_" + suffix;
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              username, "ua_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);
      UserRegistrationRequest req2 =
          new UserRegistrationRequest(
              username, "ub_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc.perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isCreated());
      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req2)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.errorCode").value("REGISTRATION_CONFLICT"));
    }

    // ── Validación de campos ──────────────────────────────────────────────────

    @Test
    @DisplayName("400 — username ausente")
    void register_missingUsername_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/register")
                  .contentType(json())
                  .content("{\"email\":\"x@test.local\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("400 — email ausente")
    void register_missingEmail_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/register")
                  .contentType(json())
                  .content("{\"username\":\"testuser\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — password ausente")
    void register_missingPassword_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/register")
                  .contentType(json())
                  .content("{\"username\":\"testuser\",\"email\":\"x@test.local\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — email con formato inválido")
    void register_invalidEmail_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "inv_" + suffix, "not-an-email", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — username demasiado corto (< 4 chars)")
    void register_usernameTooShort_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "usr", "short_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — username demasiado largo (> 50 chars)")
    void register_usernameTooLong_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "a".repeat(51), "long_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — username con caracteres inválidos (espacio)")
    void register_usernameWithSpaces_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "user name", "sp_" + suffix + "@test.local", STRONG_PASSWORD,
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — password débil (sin mayúscula)")
    void register_weakPassword_noUppercase_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "wk_" + suffix, "wk_" + suffix + "@test.local", "weakpass1!",
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — password débil (sin carácter especial)")
    void register_weakPassword_noSpecialChar_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "wk2_" + suffix, "wk2_" + suffix + "@test.local", "Weakpass1",
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — password demasiado corta (< 8 chars)")
    void register_weakPassword_tooShort_returns400() throws Exception {
      String suffix = suffix();
      UserRegistrationRequest req =
          new UserRegistrationRequest(
              "wk3_" + suffix, "wk3_" + suffix + "@test.local", "Ab1!",
              null, null, null, null, null, null, null, null, null, null, null);

      mockMvc
          .perform(post(BASE + "/register").contentType(json()).content(toJson(req)))
          .andExpect(status().isBadRequest());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /me
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("GET /me")
  class GetMe {

    @Test
    @DisplayName("200 — devuelve perfil completo del usuario autenticado")
    void getMe_happyPath_returnsProfile() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.username").isNotEmpty())
          .andExpect(jsonPath("$.email").isNotEmpty())
          .andExpect(jsonPath("$.active").value(true))
          .andExpect(jsonPath("$.createdAt").isNotEmpty())
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("401 — sin token")
    void getMe_noToken_returns401() throws Exception {
      mockMvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("401 — token malformado")
    void getMe_malformedToken_returns401() throws Exception {
      mockMvc
          .perform(get(BASE + "/me").header("Authorization", "Bearer not.a.valid.jwt"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("aislamiento — cada usuario solo ve sus propios datos")
    void getMe_isolation_eachUserSeesOwnEmail() throws Exception {
      Tokens tokensA = registerAndLogin();
      Tokens tokensB = registerAndLogin();

      String emailA = JsonPath.read(getMe(tokensA), "$.email");
      String emailB = JsonPath.read(getMe(tokensB), "$.email");

      assertThat(emailA).isNotEqualTo(emailB);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PUT /me
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("PUT /me")
  class UpdateMe {

    @Test
    @DisplayName("200 — actualización parcial: solo los campos enviados cambian")
    void updateMe_partial_onlySuppliedFieldsChange() throws Exception {
      Tokens tokens = registerAndLogin();

      UserMeUpdateRequest req =
          new UserMeUpdateRequest(
              "Carlos", "Mendoza", null, null, "Guadalajara",
              "Jalisco", null, "México", null, "MXN", "America/Mexico_City", null);

      mockMvc
          .perform(
              put(BASE + "/me")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content(toJson(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value("Carlos"))
          .andExpect(jsonPath("$.lastName").value("Mendoza"))
          .andExpect(jsonPath("$.city").value("Guadalajara"))
          .andExpect(jsonPath("$.state").value("Jalisco"))
          .andExpect(jsonPath("$.country").value("México"))
          .andExpect(jsonPath("$.preferredCurrency").value("MXN"))
          .andExpect(jsonPath("$.timezone").value("America/Mexico_City"))
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("200 — actualización completa se refleja en GET /me")
    void updateMe_full_reflectedInGetMe() throws Exception {
      Tokens tokens = registerAndLogin();

      UserMeUpdateRequest req =
          new UserMeUpdateRequest(
              "Aristeo", "Ortiz", "+52 55 9999 0000", "Av. Reforma 500",
              "CDMX", "CDMX", "06600", "México",
              "Apasionado del cripto", "MXN", "America/Mexico_City",
              java.time.LocalDate.of(1990, 5, 15));

      mockMvc
          .perform(
              put(BASE + "/me")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content(toJson(req)))
          .andExpect(status().isOk());

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value("Aristeo"))
          .andExpect(jsonPath("$.lastName").value("Ortiz"))
          .andExpect(jsonPath("$.phoneNumber").value("+52 55 9999 0000"))
          .andExpect(jsonPath("$.address").value("Av. Reforma 500"))
          .andExpect(jsonPath("$.city").value("CDMX"))
          .andExpect(jsonPath("$.postalCode").value("06600"))
          .andExpect(jsonPath("$.country").value("México"))
          .andExpect(jsonPath("$.preferredCurrency").value("MXN"))
          .andExpect(jsonPath("$.timezone").value("America/Mexico_City"))
          .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
    }

    @Test
    @DisplayName("200 — dateOfBirth se persiste y se devuelve correctamente")
    void updateMe_dateOfBirth_persistedAndReturned() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              put(BASE + "/me")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content("{\"dateOfBirth\":\"1990-05-15\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
    }

    @Test
    @DisplayName("200 — bio se persiste y se devuelve en la respuesta")
    void updateMe_bio_persistedAndReturned() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              put(BASE + "/me")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content("{\"bio\":\"Apasionado del cripto y los mercados\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.bio").value("Apasionado del cripto y los mercados"));

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.bio").value("Apasionado del cripto y los mercados"));
    }

    @Test
    @DisplayName("400 — dateOfBirth en el futuro es rechazado")
    void updateMe_dateOfBirthInFuture_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              put(BASE + "/me")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content("{\"dateOfBirth\":\"2099-01-01\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("200 — campos null en el request conservan los valores existentes")
    void updateMe_nullFields_keepExistingValues() throws Exception {
      Tokens tokens = registerAndLogin();

      // Primera actualización: establece firstName y ciudad
      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(
                  toJson(new UserMeUpdateRequest(
                      "Original", null, null, null, "Monterrey",
                      null, null, null, null, null, null, null))))
          .andExpect(status().isOk());

      // Segunda actualización: solo cambia lastName, firstName debe seguir siendo "Original"
      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(
                  toJson(new UserMeUpdateRequest(
                      null, "Nuevo Apellido", null, null, null,
                      null, null, null, null, null, null, null))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value("Original"))
          .andExpect(jsonPath("$.lastName").value("Nuevo Apellido"))
          .andExpect(jsonPath("$.city").value("Monterrey"));
    }

    @Test
    @DisplayName("aislamiento — actualizar A no modifica el perfil de B")
    void updateMe_isolation_doesNotAffectOtherUser() throws Exception {
      Tokens tokensA = registerAndLogin();
      Tokens tokensB = registerAndLogin();

      String originalFirstNameB = JsonPath.read(getMe(tokensB), "$.firstName");

      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokensA))
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  "SoyA", null, null, null, null,
                  null, null, null, null, null, null, null))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value("SoyA"));

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokensB)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstName").value(originalFirstNameB));
    }

    // ── Validaciones ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("400 — bio supera 500 caracteres")
    void updateMe_bioTooLong_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  null, null, null, null, null,
                  null, null, null, "x".repeat(501), null, null, null))))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("400 — preferredCurrency supera 3 caracteres")
    void updateMe_currencyTooLong_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  null, null, null, null, null,
                  null, null, null, null, "USDX", null, null))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — firstName supera 120 caracteres")
    void updateMe_firstNameTooLong_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  "A".repeat(121), null, null, null, null,
                  null, null, null, null, null, null, null))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — phoneNumber supera 30 caracteres")
    void updateMe_phoneNumberTooLong_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc.perform(
          put(BASE + "/me")
              .header("Authorization", bearer(tokens))
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  null, null, "1".repeat(31), null, null,
                  null, null, null, null, null, null, null))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("401 — sin token")
    void updateMe_noToken_returns401() throws Exception {
      mockMvc.perform(
          put(BASE + "/me")
              .contentType(json())
              .content(toJson(new UserMeUpdateRequest(
                  "Test", null, null, null, null,
                  null, null, null, null, null, null, null))))
          .andExpect(status().isUnauthorized());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // DELETE /me
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("DELETE /me")
  class DeleteMe {

    @Test
    @DisplayName("204 — elimina la cuenta y borra el usuario de la base de datos")
    void deleteMe_happyPath_userRemovedFromDb() throws Exception {
      Tokens tokens = registerAndLogin();
      String email = JsonPath.read(getMe(tokens), "$.email");

      mockMvc
          .perform(delete(BASE + "/me").header("Authorization", bearer(tokens)))
          .andExpect(status().isNoContent());

      assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("401 — sin token")
    void deleteMe_noToken_returns401() throws Exception {
      mockMvc.perform(delete(BASE + "/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("401 — token malformado")
    void deleteMe_malformedToken_returns401() throws Exception {
      mockMvc
          .perform(delete(BASE + "/me").header("Authorization", "Bearer garbage.token.here"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("aislamiento — eliminar A no afecta la cuenta de B")
    void deleteMe_isolation_doesNotAffectOtherUser() throws Exception {
      Tokens tokensA = registerAndLogin();
      Tokens tokensB = registerAndLogin();

      mockMvc
          .perform(delete(BASE + "/me").header("Authorization", bearer(tokensA)))
          .andExpect(status().isNoContent());

      mockMvc
          .perform(get(BASE + "/me").header("Authorization", bearer(tokensB)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(true));
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /{email}
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("GET /{email}")
  class GetByEmail {

    @Test
    @DisplayName("200 — devuelve datos del usuario sin exponer password")
    void getByEmail_found_returns200() throws Exception {
      Tokens tokens = registerAndLogin();
      String email = JsonPath.read(getMe(tokens), "$.email");

      mockMvc
          .perform(get(BASE + "/{email}", email).header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.username").isNotEmpty())
          .andExpect(jsonPath("$.active").value(true))
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("404 — email que no existe")
    void getByEmail_notFound_returns404() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              get(BASE + "/{email}", "nobody_" + UUID.randomUUID() + "@test.local")
                  .header("Authorization", bearer(tokens)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("401 — sin token")
    void getByEmail_noToken_returns401() throws Exception {
      mockMvc
          .perform(get(BASE + "/{email}", "any@test.local"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("búsqueda case-insensitive por email")
    void getByEmail_caseInsensitive_found() throws Exception {
      Tokens tokens = registerAndLogin();
      String email = JsonPath.read(getMe(tokens), "$.email");
      String upperEmail = email.toUpperCase();

      mockMvc
          .perform(get(BASE + "/{email}", upperEmail).header("Authorization", bearer(tokens)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").isNotEmpty());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // DELETE /{id}
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("DELETE /{id}")
  class DeleteById {

    @Test
    @DisplayName("200 — elimina el usuario existente de la base de datos")
    void deleteById_existingUser_removes() throws Exception {
      Tokens tokens = registerAndLogin();
      String email = JsonPath.read(getMe(tokens), "$.email");
      User user =
          userRepository
              .findByEmailIgnoreCase(email)
              .orElseThrow(() -> new AssertionError("Usuario no encontrado"));

      mockMvc
          .perform(
              delete(BASE + "/{id}", user.getId()).header("Authorization", bearer(tokens)))
          .andExpect(status().isOk());

      assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("404 — UUID que no existe en la base de datos")
    void deleteById_notFound_returns404() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              delete(BASE + "/{id}", UUID.randomUUID()).header("Authorization", bearer(tokens)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("401 — sin token")
    void deleteById_noToken_returns401() throws Exception {
      mockMvc
          .perform(delete(BASE + "/{id}", UUID.randomUUID()))
          .andExpect(status().isUnauthorized());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET / (solo ADMIN)
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("GET / (admin)")
  class ListAllUsers {

    @Test
    @DisplayName("403 — usuario con ROLE_USER no puede listar todos")
    void getAllUsers_roleUser_returns403() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(get(BASE).header("Authorization", bearer(tokens)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("401 — sin token")
    void getAllUsers_noToken_returns401() throws Exception {
      mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /password/change
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("POST /password/change")
  class PasswordChange {

    @Test
    @DisplayName("204 — cambio de contraseña exitoso")
    void changePassword_happyPath_returns204() throws Exception {
      Tokens tokens = registerAndLogin();
      String newPassword = "NewP@ssw0rd!2026";

      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content(toJson(new PasswordChangeRequest(STRONG_PASSWORD, newPassword))))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("400 — contraseña actual incorrecta")
    void changePassword_wrongCurrent_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content(toJson(new PasswordChangeRequest("WrongCurrent@1!", "NewP@ssw0rd!2026"))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — nueva contraseña débil (sin carácter especial)")
    void changePassword_weakNewPassword_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content(toJson(new PasswordChangeRequest(STRONG_PASSWORD, "Weakpass1"))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — currentPassword ausente")
    void changePassword_missingCurrentPassword_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content("{\"newPassword\":\"" + STRONG_PASSWORD + "\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — newPassword ausente")
    void changePassword_missingNewPassword_returns400() throws Exception {
      Tokens tokens = registerAndLogin();

      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .header("Authorization", bearer(tokens))
                  .contentType(json())
                  .content("{\"currentPassword\":\"" + STRONG_PASSWORD + "\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("401 — sin token")
    void changePassword_noToken_returns401() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/password/change")
                  .contentType(json())
                  .content(toJson(new PasswordChangeRequest(STRONG_PASSWORD, "NewP@ssw0rd!2026"))))
          .andExpect(status().isUnauthorized());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /password/reset
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("POST /password/reset")
  class PasswordReset {

    @Test
    @DisplayName("202 — email existente devuelve 202 (no enumera cuentas)")
    void resetPassword_existingEmail_returns202() throws Exception {
      Tokens tokens = registerAndLogin();
      String email = JsonPath.read(getMe(tokens), "$.email");

      mockMvc
          .perform(
              post(BASE + "/password/reset")
                  .contentType(json())
                  .content(toJson(new PasswordResetRequest(email))))
          .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("202 — email inexistente también devuelve 202 (previene enumeración)")
    void resetPassword_nonExistingEmail_returns202() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/password/reset")
                  .contentType(json())
                  .content(toJson(new PasswordResetRequest("noexiste_" + suffix() + "@test.local"))))
          .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("400 — email con formato inválido")
    void resetPassword_invalidEmail_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/password/reset")
                  .contentType(json())
                  .content(toJson(new PasswordResetRequest("not-an-email"))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — email ausente")
    void resetPassword_missingEmail_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/password/reset")
                  .contentType(json())
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /email/verify
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("POST /email/verify")
  class EmailVerify {

    @Test
    @DisplayName("400 — token en blanco")
    void verifyEmail_blankToken_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/email/verify")
                  .contentType(json())
                  .content(toJson(new EmailVerifyRequest("   "))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — token ausente en el body")
    void verifyEmail_missingToken_returns400() throws Exception {
      mockMvc
          .perform(
              post(BASE + "/email/verify")
                  .contentType(json())
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers privados
  // ═══════════════════════════════════════════════════════════════════════════

  private String suffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private MediaType json() {
    return MediaType.APPLICATION_JSON;
  }

  private String bearer(Tokens tokens) {
    return "Bearer " + tokens.accessToken();
  }

  private String toJson(Object obj) throws Exception {
    return objectMapper.writeValueAsString(obj);
  }

  private String getMe(Tokens tokens) throws Exception {
    return mockMvc
        .perform(get(BASE + "/me").header("Authorization", bearer(tokens)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
