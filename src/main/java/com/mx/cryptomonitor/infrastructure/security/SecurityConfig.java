package com.mx.cryptomonitor.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import com.mx.cryptomonitor.infrastructure.security.handler.OAuth2AuthenticationSuccessHandler;
import com.mx.cryptomonitor.infrastructure.security.oauth.CustomOAuth2UserService;
import com.mx.cryptomonitor.infrastructure.security.oidc.CustomOidcUserService;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUserDetailsService userDetailsService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Autowired
    @Lazy
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests

                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/users/{id}/test",
                                "/api/v1/users/register",
                                "/api/v1/users/login",
                                "/api/v1/marketdata/stock",
                                "/api/v1/marketdata/**",
                                "/api/v1/marketdata/stock/historical/{symbol}/{date}",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/refresh",
                                "/api/v1/users/public/test",
                                "/api/v1/users/public/test-post",
                                "/oauth/authorize/**",
                                "/oauth/callback/**",
                                "/swagger-ui/**", 
                                "/v3/api-docs/**", 
                                "/swagger-ui.html",
                                "/api/v1/oauth2/**",
                                "/actuator/health"
                                )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/transactions/{userId}/{assetSymbol}").hasRole("USER")
                		.requestMatchers("/api/v1/roles/**").hasRole("ADMIN")
                		.requestMatchers("/api/v1/users").hasRole("ADMIN")
                		.requestMatchers("/api/v1/users/{id:\\d+}").hasAuthority("USER:DELETE")
                		.requestMatchers("/api/v1/portfolio/**").hasRole("USER")                        
                		.requestMatchers("/api/v1/transactions/**").hasRole("USER")                        

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/auth/**",
                                "/api/v1/users/refresh",
                                "/api/v1/users/{id}",
                                "/api/v1/users/{email}",
                                "/api/v1/users/profile",
                                "/api/v1/transactions/{userId}")
                        .authenticated() // Requiere autenticación
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                		.loginPage("/login")
                		.defaultSuccessUrl("/dashboard", true)
                		.userInfoEndpoint(userInfo -> userInfo
                				
                				.userService(customOAuth2UserService)
                				.oidcUserService(customOidcUserService)                				
                				)
                		.successHandler(oAuth2AuthenticationSuccessHandler)
                		)
                .logout(oauth2 -> oauth2
                		.logoutUrl("/logout")
                		.logoutSuccessUrl("/")
                		)
                .exceptionHandling(exception -> exception
                		.accessDeniedHandler(customAccessDeniedHandler)
                		.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                										 )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                		);

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
