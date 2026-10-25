package com.machinext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
public class SecurityConfig {

    @Value("${app.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:5500}")
    private java.util.List<String> corsAllowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/", "/index.html", "/styles.css", "/reservations.css", "/app.js", "/provider.html", "/provider.css", "/provider.js", "/admin.html", "/admin.css", "/admin.js", "/admin-reports.html", "/admin-reports.css", "/admin-reports.js", "/business.html", "/business.css", "/business.js", "/business-reports.html", "/business-reports.css", "/business-reports.js", "/admin-accounts.html", "/admin-accounts.css", "/admin-accounts.js", "/invoice.html", "/invoice.css", "/invoice.js").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/equipment").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/equipment/availability").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/business/**").hasRole("BUSINESS_ADMIN")
                        .requestMatchers("/api/v1/provider/**").hasRole("PROVEEDOR")
                        .requestMatchers("/api/v1/reservations/**").hasRole("CLIENTE")
                        .requestMatchers("/api/v1/rentals/**").hasAnyRole("PROVEEDOR", "ADMIN")
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(corsAllowedOrigins);
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(java.util.List.of("Location"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
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
