package com.aiwriter.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank @Size(min = 2, max = 50)
    private String username;
    @NotBlank @Email
    private String email;
    private String phone;
    @NotBlank @Size(min = 6, max = 100)
    private String password;
}
