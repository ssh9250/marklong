package com.example.marklong.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String password;
    @NotBlank
    private String nickname;
}
