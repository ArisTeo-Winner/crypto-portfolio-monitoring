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
  private static final String API_CONTENT_SECURITY_POLICY =
      "default-src 'self'; "
          + "script-src 'self' 'unsafe-inline'; "
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
            authorizeRequests ->
                authorizeRequests
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/crypto/{symbol}/price")
                    .permitAll()
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
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/api/v1/assets/search",
                        "/api/v1/oauth2/**",
                        "/actuator/health/**",
                        "/actuator/prometheus",
                        "/actuator/info",
                        "/api/v1/tokens/revoke",
                        "/api/v1/tokens/refresh",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/me/transactions/{userId}/{assetSymbol}")
                    .hasRole("USER")
                    .requestMatchers("/api/v1/roles/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/users")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/users/me")
                    .hasRole("USER")
                    .requestMatchers("/api/v1/users/{id:\\d+}")
                    .hasAuthority("USER:DELETE")
                    .requestMatchers("/api/v1/me/portfolio/**")
                    .hasRole("USER")
                    .requestMatchers("/api/v1/me/transactions/**")
                    .hasRole("USER")
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
                    .authenticated()
                    .anyRequest()
                    .authenticated())
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
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Idempotency-Key",
            "X-Refresh-Token",
            "X-Request-Id"));
    configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    firewall.setAllowUrlEncodedPercent(true);
    firewall.setAllowSemicolon(true);
    firewall.setAllowBackSlash(true);
    firewall.setAllowUrlEncodedPeriod(true);
    return firewall;
  }
}
