package com.example.marklong.domain.user.controller;

import com.example.marklong.domain.user.dto.UserDetailResponse;
import com.example.marklong.domain.user.service.UserService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "User", description = "사용자 정보 조회 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 반환합니다.")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getMyInfo(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        UserDetailResponse response = userService.getUserInfo(authUser.userId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
