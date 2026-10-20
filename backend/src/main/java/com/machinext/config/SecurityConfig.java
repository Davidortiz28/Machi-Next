package com.machinext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/", "/index.html", "/styles.css", "/reservations.css", "/app.js", "/provider.html", "/provider.css", "/provider.js", "/admin.html", "/admin.css", "/admin.js", "/business.html", "/business.css", "/business.js", "/admin-accounts.html", "/admin-accounts.css", "/admin-accounts.js", "/invoice.html", "/invoice.css", "/invoice.js").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/equipment").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/equipment/availability").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/business/**").hasRole("BUSINESS_ADMIN")
                        .requestMatchers("/api/v1/provider/**").hasRole("PROVEEDOR")
                        .requestMatchers("/api/v1/reservations/**").hasRole("CLIENTE")
                        .requestMatchers("/api/v1/rentals/**").hasAnyRole("PROVEEDOR", "ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
