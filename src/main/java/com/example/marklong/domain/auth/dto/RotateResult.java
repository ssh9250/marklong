package com.example.marklong.domain.auth.dto;

public record RotateResult(
        String newToken,
        Long userId,
        String familyId
) {
}
