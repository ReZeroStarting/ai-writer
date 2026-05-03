package com.aiwriter.service;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.model.User;
import com.aiwriter.repository.UserRepository;
import com.aiwriter.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new RuntimeException("用户名已被使用");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build();
        user = userRepo.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getAccount())
                .or(() -> userRepo.findByUsername(req.getAccount()))
                .or(() -> userRepo.findByPhone(req.getAccount()))
                .orElseThrow(() -> new RuntimeException("账号不存在"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("刷新令牌无效或已过期");
        }
        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
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
