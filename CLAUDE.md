# Marklong 프로젝트 가이드

## 기술 스택
- Spring Boot 4.x / Spring Security 7.x / Java 17
- Spring Data JPA + QueryDSL 5.0
- JWT (jjwt 0.12.x) — DB 기반 Refresh Token, 추후 Redis RTR + blacklist 도입 예정
- Redis (spring-boot-starter-data-redis)
- Swagger (springdoc-openapi 2.x)

## 패키지 구조 원칙
- 도메인 단위로 패키지 분리: `domain/{도메인}/controller`, `domain/{도메인}/service`, `domain/{도메인}/dto`, `domain/{도메인}/domain`, `domain/{도메인}/repository`
- 보안 관련 코드: `security/auth`, `security/jwt`, `security/util`
- 공통 코드: `global/exception`, `global/response`, `global/entity`

## 코드 규칙

### 응답 형식
모든 API 응답은 `ApiResponse<T>`로 감싸서 반환한다.
```java
return ResponseEntity.ok(ApiResponse.ok(data));
return ResponseEntity.ok(ApiResponse.ok(null)); // 반환 데이터 없는 경우
```

### 인증
컨트롤러에서 로그인 유저 식별은 `@AuthenticationPrincipal CustomUserDetails`로 통일한다.
```java
public ResponseEntity<?> example(@AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails.getUserId();
}
```

### 예외 처리
비즈니스 예외는 `BusinessException(ErrorCode.xxx)` 로 던진다. 새로운 에러 코드가 필요하면 `ErrorCode` enum에 추가한다.
다만, 간단한 예외코드가 도메인 별로 공통적으로 나타나는 경우 동일한 enum을 사용하도록 한다.
예시) post, comment validate -> POST_ACCESS_DENIED, COMMENT_ACCESS_DENIED => ErrorCode.ACCESS_DENIED

### Swagger 문서화
- 컨트롤러 클래스: `@Tag(name = "...", description = "...")`
- 메서드: `@Operation(summary = "...")` — 간단한 참조용 목적만, `@Parameter`는 생략
- 쿼리 조건 객체: `@RequestBody`, `@ModelAttribute` 등 명시

### Role
`Role` enum 값이 이미 `ROLE_USER`, `ROLE_ADMIN` 형태이므로 `SimpleGrantedAuthority`에 prefix 없이 그대로 사용한다.

### 소프트 딜리트
유저 조회 시 반드시 `deletedAt`을 고려한 메서드를 사용한다.
```java
// 올바른 사용
userRepository.findUserByIdAndDeletedAtIsNull(userId)
userRepository.findByEmailAndDeletedAtIsNull(email)

// 사용 금지 (탈퇴 유저 포함됨)
userRepository.findById(userId)
```

## 현재 미구현 / WIP
- `StockPriceRedisRepository`: 현재가 조회 — Redis 캐시 + TimescaleDB 폴백 구조만 잡혀 있음
- `HoldingService.getHolding()`: 현재가 및 미실현손익 — 외부 API / Redis 연동 후 채울 예정
- `PortfolioService`: currentPrice — 현재 `BigDecimal.ZERO` 하드코딩, 추후 수정 필요
- `JwtAuthenticationFilter`: Redis blacklist 체크 로직 주석 처리 중 — Redis 도입 시 활성화
- `HoldingHistory` 거래 내역에서 매도 시 포트폴리오 배분 수량과 오차 발생 가능 (TODO 주석 참고)
