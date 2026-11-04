package com.aiwriter.config;

import com.aiwriter.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // 健康检查要给 K8s / 负载均衡探活用，不能要求令牌
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Prometheus 抓取端点：抓取器通常不便携带 JWT。
                // 生产环境应只在内网暴露（或放在独立的管理端口），不要直接对公网开放。
                .requestMatchers("/actuator/prometheus").permitAll()
                // 错误页必须放行：否则 415/400 这类由容器转发到 /error 的请求
                // 会被二次鉴权拦成 401，把真实状态码盖掉。
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            // 未携带/携带无效令牌 → 401（前端据此触发刷新）；已认证但无权限 → 403
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, "未认证或登录已过期"))
                .accessDeniedHandler((req, res, e) ->
                        writeJson(res, HttpServletResponse.SC_FORBIDDEN, "无权访问该资源"))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeJson(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
