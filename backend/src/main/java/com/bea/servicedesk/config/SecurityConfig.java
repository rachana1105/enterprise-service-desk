package com.bea.servicedesk.config;

import com.bea.servicedesk.repo.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless Spring Security setup: BCrypt passwords, CORS and a JWT bearer-token filter.
 *
 * <p>{@code /error} is permitted so that a 400/404 raised by a controller keeps its status code
 * when the servlet container forwards to the error page, instead of turning into 403.
 */
@Configuration
public class SecurityConfig {
  private static final String BEARER = "Bearer ";

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors.origin}") String origin) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origin));
    c.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserRepository users)
      throws Exception {
    OncePerRequestFilter jwtFilter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith(BEARER)) {
              try {
                String email = jwt.subject(h.substring(BEARER.length()));
                users
                    .findByEmailIgnoreCase(email)
                    .ifPresent(
                        u -> {
                          var a =
                              new UsernamePasswordAuthenticationToken(
                                  u.email,
                                  null,
                                  List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name())));
                          SecurityContextHolder.getContext().setAuthentication(a);
                        });
              } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
              }
            }
            chain.doFilter(req, res);
          }
        };
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/**", "/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
