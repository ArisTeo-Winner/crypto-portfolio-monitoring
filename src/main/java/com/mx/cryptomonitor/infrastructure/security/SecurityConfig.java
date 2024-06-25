package com.mx.cryptomonitor.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;


@Configuration
@EnableWebSecurity
public class SecurityConfig{
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
		
		http
			.csrf().disable()
			.authorizeHttpRequests(authorizeRequests ->
				authorizeRequests
					.requestMatchers("/auth/**","/users/register").permitAll()
					.anyRequest().authenticated()					
			)
			.httpBasic();
		
		
		return http.build();
	}
	

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowSemicolon(true);  // Permitir punto y coma
        firewall.setAllowBackSlash(true);  // Permitir barra invertida
        firewall.setAllowUrlEncodedPeriod(true);  // Permitir punto codificado en URL
        return firewall;
    }

}
