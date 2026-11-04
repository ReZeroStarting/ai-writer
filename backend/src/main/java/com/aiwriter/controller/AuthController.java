package com.aiwriter.controller;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RefreshRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req.getRefreshToken()));
    }

    /**
     * 登出：作废当前 refresh token 的白名单记录。
     * 用 refresh token 本身证明身份，因此不需要额外的 access token。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
