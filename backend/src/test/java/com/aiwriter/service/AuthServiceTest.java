package com.aiwriter.service;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.exception.AuthException;
import com.aiwriter.model.User;
import com.aiwriter.repository.UserRepository;
import com.aiwriter.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        loginRequest = new LoginRequest();
        loginRequest.setAccount("test@example.com");
        loginRequest.setPassword("password123");

        savedUser = User.builder()
                .id(1L).username("testuser").email("test@example.com")
                .passwordHash("hashed-password")
                .createdAt(LocalDateTime.now())
                .build();

        when(tokenProvider.generateAccessToken(anyLong(), anyString())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh-token");
    }

    @Test
    void shouldRegisterNewUser() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepo.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(1L, response.getUser().getId());
        assertEquals("testuser", response.getUser().getUsername());
        verify(userRepo).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        verify(userRepo, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepo.existsByUsername("testuser")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        verify(userRepo, never()).save(any());
    }

    @Test
    void shouldLoginWithCorrectCredentials() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void shouldRejectWrongPassword() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.login(loginRequest));
    }

    @Test
    void shouldRejectNonexistentAccount() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhone("test@example.com")).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> authService.login(loginRequest));
    }

    /** 防账号枚举：「账号不存在」与「密码错误」必须返回完全相同的提示。 */
    @Test
    void shouldReturnSameMessageForNonexistentAccountAndWrongPassword() {
        // 账号不存在
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userRepo.findByPhone("test@example.com")).thenReturn(Optional.empty());
        String msgNonexistent = assertThrows(AuthException.class,
                () -> authService.login(loginRequest)).getMessage();

        // 密码错误（同一账号存在，仅密码不对）
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(false);
        String msgWrongPwd = assertThrows(AuthException.class,
                () -> authService.login(loginRequest)).getMessage();

        assertEquals(msgNonexistent, msgWrongPwd, "两种失败必须返回同一提示，避免枚举账号");
    }

    @Test
    void shouldLoginWithUsername() {
        loginRequest.setAccount("testuser");
        when(userRepo.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        verify(userRepo).findByUsername("testuser");
    }

    @Test
    void shouldRefreshToken() {
        when(tokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("refresh-token")).thenReturn(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(savedUser));

        AuthResponse response = authService.refresh("refresh-token");

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        when(tokenProvider.validateRefreshToken("bad-token")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.refresh("bad-token"));
    }

    /** 用 access token 冒充 refresh token 刷新，必须被拒绝。 */
    @Test
    void shouldRejectAccessTokenUsedAsRefresh() {
        when(tokenProvider.validateRefreshToken("access-token")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.refresh("access-token"));
    }
}
