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
import org.springframework.dao.DataIntegrityViolationException;
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
        // 前置检查：给出友好提示（覆盖绝大多数情况）
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
        try {
            user = userRepo.save(user);
        } catch (DataIntegrityViolationException e) {
            // 并发竞态兜底：两个请求同时通过了上面的 exists 检查，最终由数据库唯一索引拦下。
            // 此时不能把异常抛成 500，而要按"已被注册"处理。
            log.warn("并发注册被唯一索引拦下: email={}, username={}", req.getEmail(), req.getUsername());
            throw new IllegalArgumentException(resolveDuplicateMessage(req, e));
        }

        log.info("用户注册: id={}, username={}, email={}", user.getId(), user.getUsername(), user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * 竞态兜底时判断到底是邮箱还是用户名冲突：优先以数据库中已存在的记录为准，
     * 查不到再退回解析底层异常消息。
     */
    private String resolveDuplicateMessage(RegisterRequest req, DataIntegrityViolationException e) {
        if (userRepo.existsByEmail(req.getEmail())) {
            return "邮箱已被注册";
        }
        if (userRepo.existsByUsername(req.getUsername())) {
            return "用户名已被使用";
        }
        String raw = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (raw != null && raw.toLowerCase().contains("username")) {
            return "用户名已被使用";
        }
        return "邮箱已被注册";
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getAccount())
                .or(() -> userRepo.findByUsername(req.getAccount()))
                .or(() -> userRepo.findByPhone(req.getAccount()))
                .orElse(null);

        if (user == null) {
            // 安全：账号不存在与密码错误返回同一提示，避免被用于枚举已注册账号
            log.warn("登录失败: 账号不存在 account={}", req.getAccount());
            throw new AuthException("账号或密码错误");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败: 密码错误 userId={}, username={}", user.getId(), user.getUsername());
            throw new AuthException("账号或密码错误");
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
