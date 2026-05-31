package com.mtg.collection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the MTG Collection Manager application.
 *
 * <p>Configures OAuth2/OIDC login via Microsoft Entra ID (Azure Active Directory).
 * All endpoints require authentication except static resources and actuator health probes.
 * The required Spring properties are:
 * <ul>
 *   <li>{@code spring.security.oauth2.client.registration.azure.client-id}</li>
 *   <li>{@code spring.security.oauth2.client.registration.azure.client-secret}</li>
 *   <li>{@code spring.security.oauth2.client.provider.azure.authorization-uri}</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain.
     *
     * <p>Static resources, webjars and actuator health endpoints are publicly accessible.
     * All other requests require the user to be authenticated via Entra ID.
     *
     * @param http the {@link HttpSecurity} instance to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // REST API endpoints are called via JavaScript fetch() and do not benefit
                // from CSRF protection, since the browser's same-origin policy already
                // prevents cross-site requests from obtaining a valid session cookie.
                // Form-based Thymeleaf pages (/import POST etc.) are still CSRF-protected.
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/**"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/webjars/**",
                    "/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            );
        return http.build();
    }
}
