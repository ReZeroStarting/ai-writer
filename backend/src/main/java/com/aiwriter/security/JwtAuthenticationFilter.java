package com.aiwriter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    /**
     * SSE / Flux 接口（如 /api/ai/**、/api/v1/frameworks/{id}/write/*）会触发 Servlet ASYNC 派发，
     * 二次进入过滤器链。OncePerRequestFilter 默认在 ASYNC 派发时跳过本过滤器，
     * 导致 SecurityContext 丢失、被 AnonymousAuthenticationFilter 置为匿名，
     * 最终被 anyRequest().authenticated() 判为未认证而返回 401。
     * 因此必须在 ASYNC 派发时同样执行，才能让流式接口通过鉴权。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        // 仅接受 access token：refresh token 不能用于访问受保护资源
        if (StringUtils.hasText(token) && tokenProvider.validateAccessToken(token)) {
            Long userId = tokenProvider.getUserIdFromToken(token);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
