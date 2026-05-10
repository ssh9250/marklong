package com.example.marklong.domain.event.domain;

public enum EventType {
    EARNINGS,   // 실적 발표
    DISCLOSURE, // 공시
    DIVIDEND,   //  배당락, 배당지급
    MACRO,      // 거시경제 이벤트(FOMC 등)
    USER_MEMO   //  사용자 메모
}
