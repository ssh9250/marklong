package com.example.marklong.global.exception;

import com.example.marklong.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.error("BusinessException: {} - {}", code.getCode(), code.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code.getMessage()));
    }

    // 임시로 Validation Error 잡기용
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", errorMessage);
        return ResponseEntity.badRequest().body(ApiResponse.fail(errorMessage));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDenied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(e.getMessage()));
    }

//    @ExceptionHandler({
//            BadCredentialsException.class,
//            AuthenticationException.class
//    })
//    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(Exception e) {
//        // 보안상의 이유로 세분화하지 않는 것이 바람직
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("로그인에 실패하였습니다."));
//    }

//    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class})
//    public ResponseEntity<ApiResponse<Void>> handleLockException(Exception e) {
//        return ResponseEntity
//                .status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getStatus())
//                .body(ApiResponse.fail(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getMessage()));
//    }


    @ExceptionHandler
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.info("handleException: {}", e.getMessage());
        return ResponseEntity.status(500)
                .body(ApiResponse.fail("서버 내부에 오류가 발생했습니다."));
    }
}
