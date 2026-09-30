package com.GDGoCSMU.ASKeep.global.security;

import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;

    public SecurityConfig(JwtProvider jwtProvider, TokenBlacklist tokenBlacklist) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 회원가입 / 로그인은 누구나
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/auth/signup", "/api/v1/users/auth/login").permitAll()
                        // 내 담당 API: 로그인 필요
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .requestMatchers("/api/v1/sessions", "/api/v1/sessions/*", "/api/v1/sessions/*/start", "/api/v1/sessions/*/end").authenticated()
                        // 그 외 (자료/질문/답변, FastAPI 내부 API 등 다른 팀원 담당)는 일단 열어둔다.
                        // 팀과 합의되면 여기서 .authenticated()로 바꾸면 된다.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, ErrorCode.FORBIDDEN)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, tokenBlacklist),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.getStatus().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        // 필터 단계라 컨트롤러 예외처리를 못 타므로 공통 응답 형식을 직접 쓴다
        res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"" + code.name()
                + "\",\"message\":\"" + code.getMessage() + "\"}}");
    }
}
