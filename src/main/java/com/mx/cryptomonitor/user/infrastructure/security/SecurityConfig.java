package com.mx.cryptomonitor.user.infrastructure.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.mx.cryptomonitor.user.infrastructure.security.handler.CustomOAuth2FailureHandler;
import com.mx.cryptomonitor.user.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.user.infrastructure.security.oauth.CustomOAuth2UserService;
import com.mx.cryptomonitor.user.infrastructure.security.oidc.CustomOidcUserService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  // 'unsafe-inline' eliminado de script-src — un API REST no debe ejecutar scripts inline.
  // Si el flujo OAuth2 requiere HTML mínimo, hacerlo sin inline scripts.
  private static final String API_CONTENT_SECURITY_POLICY =
      "default-src 'self'; "
          + "script-src 'self'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data: https:; "
          + "font-src 'self' data:; "
          + "connect-src 'self' https:; "
          + "object-src 'none'; "
          + "frame-ancestors 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'";
  private static final String API_PERMISSIONS_POLICY =
      "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
          + "microphone=(), payment=(), usb=()";

  private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  private final CustomOAuth2FailureHandler customOAuth2FailureHandler;
  private final CustomOAuth2UserService customOAuth2UserService;
  private final CustomOidcUserService customOidcUserService;

  @Value("${app.frontend-base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${springdoc.api-docs.enabled:false}")
  private boolean springdocApiDocsEnabled;

  @Value("${springdoc.swagger-ui.enabled:false}")
  private boolean springdocSwaggerUiEnabled;

  @Autowired private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Autowired private CustomAccessDeniedHandler customAccessDeniedHandler;

  @Autowired @Lazy private JwtRequestFilter jwtRequestFilter;

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
      throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults())
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    .addHeaderWriter(
                        new StaticHeadersWriter("Permissions-Policy", API_PERMISSIONS_POLICY)))
        .authorizeHttpRequests(
            authorizeRequests -> {
              authorizeRequests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
              authorizeRequests
                  .requestMatchers(HttpMethod.GET, "/api/v1/crypto/{symbol}/price")
                  .permitAll();
              authorizeRequests
                  .requestMatchers(
                      "/api/v1/auth/login",
                      "/api/v1/users/{id}/test",
                      "/api/v1/users/register",
                      "/api/v1/users/password/reset",
                      "/api/v1/users/email/verify",
                      "/api/v1/marketdata/stock",
                      "/api/v1/marketdata/**",
                      "/api/v1/marketdata/stock/historical/{symbol}/{date}",
                      "/api/v1/auth/logout",
                      "/api/v1/users/public/test-get",
                      "/api/v1/users/public/test-post",
                      "/oauth/authorize/**",
                      "/oauth/callback/**",
                      "/api/v1/assets",
                      "/api/v1/assets/search",
                      "/api/v1/assets/popular",
                      "/api/v1/health",
                      "/api/v1/oauth2/**",
                      "/actuator/health/**",
                      "/actuator/prometheus",
                      "/actuator/info",
                      "/api/v1/tokens/revoke",
                      "/api/v1/tokens/refresh",
                      "/error")
                  .permitAll();
              if (springdocApiDocsEnabled || springdocSwaggerUiEnabled) {
                authorizeRequests
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                    .permitAll();
              }
              authorizeRequests
                  .requestMatchers(HttpMethod.GET, "/api/v1/me/transactions/{userId}/{assetSymbol}")
                  .hasRole("USER");
              authorizeRequests.requestMatchers("/api/v1/roles/**").hasRole("ADMIN");
              authorizeRequests.requestMatchers("/api/v1/users").hasRole("ADMIN");
              authorizeRequests.requestMatchers("/api/v1/users/me").hasRole("USER");
              authorizeRequests
                  .requestMatchers("/api/v1/users/{id:\\d+}")
                  .hasAuthority("USER:DELETE");
              authorizeRequests.requestMatchers("/api/v1/me/portfolio/**").hasRole("USER");
              authorizeRequests.requestMatchers("/api/v1/me/transactions/**").hasRole("USER");
              authorizeRequests.requestMatchers("/api/v1/me/sessions/**").hasRole("USER");
              authorizeRequests.requestMatchers("/api/v1/me/broker/**").hasRole("USER");
              authorizeRequests.requestMatchers("/api/v1/portfolio/cetes/**").hasRole("USER");
              authorizeRequests
                  .requestMatchers(
                      HttpMethod.DELETE,
                      "/api/v1/auth/**",
                      "/api/v1/users/refresh",
                      "/api/v1/users/{id}",
                      "/api/v1/users/{email}",
                      "/api/v1/users/profile",
                      "/api/v1/users/password/change",
                      "/api/v1/users/me",
                      "/api/v1/me/transactions/{userId}",
                      "/api/v1/crypto/**")
                  .authenticated();
              authorizeRequests.anyRequest().authenticated();
            })
        .oauth2Login(
            oauth2 ->
                oauth2
                    .loginPage("/login")
                    .defaultSuccessUrl("/dashboard", true)
                    .userInfoEndpoint(
                        userInfo ->
                            userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                    .failureHandler(customOAuth2FailureHandler))
        .logout(oauth2 -> oauth2.logoutUrl("/logout").logoutSuccessUrl("/"))
        .exceptionHandling(
            exception ->
                exception
                    .accessDeniedHandler(customAccessDeniedHandler)
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint))
        // STATELESS: la API autentica exclusivamente por JWT en cada request.
        // IF_REQUIRED permitía que Spring creara una sesión HTTP en el primer login y que
        // requests posteriores sin token pasaran autenticados vía cookie JSESSIONID.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(frontendBaseUrl));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    // X-Refresh-Token eliminado: el refresh token ya viaja exclusivamente como cookie HttpOnly.
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Idempotency-Key",
            "X-Request-Id"));
    configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * HttpFirewall con configuración conservadora.
   *
   * <p>Solo se habilita {@code allowUrlEncodedSlash} porque algunos símbolos de activos (p.ej.
   * {@code BTC%2FUSDT}) viajan codificados en path variables. El resto de caracteres peligrosos
   * (backslash, semicolon, double-encoded percent) se mantienen bloqueados para prevenir ataques de
   * path traversal y parameter pollution.
   */
  @Bean
  public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true); // necesario para símbolos con '/' en path vars
    // setAllowUrlEncodedPercent(false)  — default, previene double-encoding
    // setAllowSemicolon(false)          — default, previene parameter pollution
    // setAllowBackSlash(false)          — default, previene path traversal en Windows
    // setAllowUrlEncodedPeriod(false)   — default, previene traversal con %2E
    return firewall;
  }
}
