package com.example.marklong.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // auth
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_001", "존재하지 않는 유저입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH_002", "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "AUTH_003", "이미 사용 중인 별명입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_004", "비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_006", "만료된 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_007", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_008", "접근 권한이 없습니다."),

    // hold
    HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND, "HOLD_001", "보유 종목을 찾을 수 없습니다."),
    INSUFFICIENT_UNALLOCATED_QUANTITY(HttpStatus.BAD_REQUEST, "HOLD_002", "미배분 수량이 부족합니다."),
    HOLDING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "HOLD_003", "보유 자산에 대한 권한이 없습니다."),

    // stock
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK_001", "존재하지 않는 종목입니다."),

    // portfolio
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "PORT_001", "존재하지 않는 포트폴리오입니다."),
    PORTFOLIO_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PORT_002", "포트폴리오에 대한 권한이 없습니다."),
    PORTFOLIO_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "PORT_003", "보유 종목을 찾을 수 없습니다."),
    INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "PORT_004", "보유 수량이 부족합니다."),

    // post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않는 게시글입니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_002", "게시글에 대한 권한이 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_003", "존재하지 않는 댓글입니다."),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_004", "댓글에 대한 권한이 없습니다."),

    // global

    DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "DATA_004", "해당 데이터를 찾을 수 없습니다."),

;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
