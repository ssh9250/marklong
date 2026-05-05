package com.example.marklong.global.util;

import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

public class OwnerValidator {
    private OwnerValidator() {
    }

    public static void validate(Long userId, Long ownerId) {
        if (!userId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}