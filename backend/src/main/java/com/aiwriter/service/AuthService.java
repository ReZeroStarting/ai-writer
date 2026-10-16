package com.aiwriter.service;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.exception.AuthException;
import com.aiwriter.model.User;
import com.aiwriter.repository.UserRepository;
import com.aiwriter.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("用户名已被使用");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build();
        user = userRepo.save(user);

        log.info("用户注册: id={}, username={}, email={}", user.getId(), user.getUsername(), user.getEmail());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getAccount())
                .or(() -> userRepo.findByUsername(req.getAccount()))
                .or(() -> userRepo.findByPhone(req.getAccount()))
                .orElseThrow(() -> {
                    log.warn("登录失败: 账号不存在 account={}", req.getAccount());
                    return new AuthException("账号不存在");
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败: 密码错误 userId={}, username={}", user.getId(), user.getUsername());
            throw new AuthException("密码错误");
        }

        log.info("用户登录: userId={}, username={}", user.getId(), user.getUsername());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        // 必须是 refresh token，拿 access token 来刷新将被拒绝
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new AuthException("刷新令牌无效或已过期");
        }
        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException("用户不存在"));
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateAccessToken(user.getId(), user.getUsername()))
                .refreshToken(tokenProvider.generateRefreshToken(user.getId()))
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId()).username(user.getUsername())
                        .email(user.getEmail()).phone(user.getPhone())
                        .createdAt(user.getCreatedAt().toString()).build())
                .build();
    }
}
