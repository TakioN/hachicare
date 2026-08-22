package com.example.demo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.demo.global.jwt.AuthenticationFailureWriter;
import com.example.demo.global.jwt.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/signup", "/api/signin", "/api/refresh", "/api/signout",
        "/api/health",
        "/swagger-ui/**", "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationFailureWriter authenticationFailureWriter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 토큰 기반이라 세션도 CSRF 토큰도 쓰지 않는다.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 기본을 닫아둔다. 열어야 할 경로만 위에 적는다.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(authenticationFailureWriter)
                    .accessDeniedHandler(authenticationFailureWriter))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
