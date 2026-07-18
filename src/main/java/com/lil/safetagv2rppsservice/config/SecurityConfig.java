package com.lil.safetagv2rppsservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/practitioners/**", "/api/v1/practitioners").permitAll() // <-- Ouvre toutes les routes praticiens
                        .requestMatchers("/error").permitAll() // 🛠️ Permet d'afficher la vraie exception
                        .requestMatchers("/api/v1/geocoding/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new GatewayHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static class GatewayHeaderFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            System.out.println("[DIAGNOSTIC] URI appelée reçue par RPPS : " + request.getRequestURI());
            System.out.println("[DIAGNOSTIC] Header X-User-Id : " + request.getHeader("X-User-Id"));
            System.out.println("[DIAGNOSTIC] Header X-User-Role : " + request.getHeader("X-User-Role"));
            String userId = request.getHeader("X-User-Id");
            String role = request.getHeader("X-User-Role");

            if (userId != null && role != null) {
                String authorityName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                var authority = new SimpleGrantedAuthority(authorityName);
                var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println("[DIAGNOSTIC] Authentification créée avec succès dans le contexte !");
            } else {
                System.out.println("[DIAGNOSTIC] Absence d'en-têtes utilisateur valides.");
            }

            filterChain.doFilter(request, response);
        }
    }
}
