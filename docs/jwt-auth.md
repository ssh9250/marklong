# JWT 인증 시스템

## 개요

이 프로젝트는 **Stateless 이중 토큰(Access + Refresh)** 방식으로 인증을 구현합니다.  
Access Token은 JJWT 기반의 서명된 JWT, Refresh Token은 UUID 문자열입니다.  
Refresh Token은 Redis Hash에만 저장하며, 재발급 시 Lua 스크립트로 원자적 회전(Rotation)합니다.

---

## JWT 토큰 탈취·재사용 문제 — RTR을 1단계 → 2단계 → 3단계로 발전

### 문제

JWT는 발급 후 서버가 직접 무효화할 수 없다는 구조적 한계를 가진다. Refresh Token이 탈취되면 만료 전까지 공격자가 정상 사용자와 구분 없이 재발급을 받을 수 있고, 서버는 이 사실 자체를 알아낼 방법이 없다.

```
탈취 시나리오 (RTR 없음)
1. 공격자가 Refresh Token을 탈취
2. 공격자와 정상 사용자가 같은 Refresh Token으로 각자 재발급 요청
3. 서버는 두 요청을 구분할 수 없음 → 탈취 사실 자체가 드러나지 않음
```

이를 줄이기 위해 재발급마다 토큰을 교체(rotate)하고 재사용 시 무효화하는 RTR을 도입했다. 저장소 구조는 세 단계를 거치며 발전시켰다.

### 해결

#### 1단계 (shop): Redis 단일 저장, userId 키

Refresh Token을 `refresh:{userId}` 형태의 키로 Redis에 보관하는 가장 단순한 구조다.

```java
// shop — userId 가 key, token 이 value
stringRedisTemplate.opsForValue().set("refresh:" + userId, token, ttl);
```

구현이 단순하고 조회가 빠르지만 두 가지 약점이 있었다.

- **Redis 장애 = 전체 로그아웃**: 토큰의 원본이 Redis뿐이라 장애 시 모든 사용자가 강제 로그아웃된다.
- **재사용 감지 불가**: ROTATED 상태를 기록하지 않으므로 이미 교체된 토큰을 재사용해도 탈취 정황을 식별할 수 없다.

#### 2단계 (marklong 초기): DB 원본 + Redis 캐시 이중화

`RefreshToken` 엔티티를 도입해 JPA Repository(원본)와 Redis(캐시)로 분리했다. DB를 source of truth로 두고 Redis는 cache-aside로만 운용한다.

```java
public String findByUserId(Long userId) {
    String token = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
    if (token != null) return token;                                        // 캐시 히트
    RefreshToken rt = jpaRepository.findByUserId(userId)                    // 미스 → DB
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    Duration remaining = Duration.between(LocalDateTime.now(), rt.getExpiresAt());
    if (!remaining.isNegative())
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, rt.getToken(), remaining);
    return rt.getToken();
}
```

Redis 장애 시 DB로 복구 가능해졌고, 불일치 시 즉시 `delete`로 무효화해 탈취 정황을 살려두지 않는다. 같은 흐름에서 인증 필터도 개선했다. 기존에는 매 요청마다 `loadUserById()`로 DB를 조회해 SecurityContext를 채웠는데, JWT 클레임의 `userId`·`role`만으로 인증 객체를 구성하는 `AuthUser` record로 대체했다.

단, 키 구조가 `userId → token`이라 재발급 시 token으로 userId를 역방향 조회하는 경로는 반드시 DB를 타야 했다. 이중화의 비용이 특정 조회 경로의 DB 의존으로 돌아오는 지점이었다.

#### 3단계 (marklong 현재): Redis 단일 + 토큰 키 Hash + revokedAfter

키 방향을 **userId → token** 에서 **token → {userId, status, issuedAt, expiresAt}** Hash로 뒤집었다. DB 의존을 완전히 제거하고 상태 기반 재사용 감지를 Redis 안에서 처리한다.

```
rt:{token}  →  Hash { userId, status(ACTIVE|ROTATED), issuedAt, expiresAt }
user:{userId}:rtRevokedAfter  →  epoch(seconds)
```

재발급은 Lua 스크립트로 원자적으로 처리한다. 조회 → 상태 확인 → 교체가 한 번의 Redis 명령으로 실행되어 경쟁 조건 없이 ACTIVE/ROTATED 전환이 보장된다.

```lua
-- ACTIVE 토큰이면 ROTATED로 바꾸고 새 토큰 저장, 아니면 탈취 의심
if status ~= 'ACTIVE' then return -1 end     -- 재사용 감지
redis.call('HSET', KEYS[1], 'status', 'ROTATED')
redis.call('HSET', KEYS[2], 'userId', ARGV[1], 'status', 'ACTIVE', ...)
redis.call('EXPIRE', KEYS[2], ARGV[4])
return 1
```

재사용이 감지되면(반환값 -1) `revokeAll`로 `user:{userId}:rtRevokedAfter` 에 현재 시각을 기록한다. 인증 필터는 Access Token의 `iat`와 이 값을 비교해, 발급 시점이 `revokedAfter` 이전인 토큰을 모두 차단한다. 개별 Access Token 블랙리스트 없이 계정 단위로 세션을 무효화하는 구조다.

```java
Optional<Long> revokedAfter = refreshTokenRedisRepository.getRevokedAfter(userId);
if (revokedAfter.isPresent() && revokedAfter.get() > issuedAt) {
    reject(response); // revokedAfter 이전에 발급된 AT 전부 차단
    return;
}
```

로그아웃도 `revokeAll`을 호출해 같은 경로로 처리한다.

남은 제약은 reissue 시 User DB 조회다. Access Token 생성에 `role`이 필요한데, `rt:{token}` Hash에는 userId만 있어 `userRepository.findById(userId)`를 한 번 더 타야 한다. role을 Hash에 함께 저장하는 방향도 고려했지만, DB와 Redis 간 role 불일치 위험이 생기는 트레이드오프가 있어 현재는 DB 조회를 유지하고 있다.

### 단계별 비교

| 항목               | 1단계 (shop)        | 2단계 (marklong 초기)      | 3단계 (marklong 현재)            |
| ---------------- | ----------------- | ----------------------- | ----------------------------- |
| 저장소              | Redis 단일          | DB 원본 + Redis 캐시        | Redis 단일                      |
| 토큰 키 방향          | userId → token    | userId → token          | token → Hash                  |
| Redis 장애 시       | 전체 강제 로그아웃        | DB에서 복구 가능              | 전체 강제 로그아웃                    |
| 재사용 감지           | 없음                | 불일치 시 무효화               | ROTATED 상태 감지 → revokeAll     |
| 세션 무효화 방식        | 개별 토큰 삭제          | 개별 토큰 삭제                | revokedAfter 타임스탬프 (계정 단위)    |
| AT 블랙리스트         | 있음                | 있음                      | 없음 (revokedAfter로 대체, phase4) |
| reissue DB 조회    | userId → role 1회  | token → userId, userId → role 2회 | userId → role 1회     |
| 인증 필터 DB 조회      | 매 요청              | 없음 (클레임 기반)             | 없음 (클레임 기반)                   |
| 원자적 rotate       | 없음                | 없음                      | Lua 스크립트                      |

---

## 전체 구조 한눈에 보기

```
클라이언트
    │
    ▼
[ JwtExceptionFilter ]        ← JWT 예외를 HTTP 에러 응답으로 변환
    │
    ▼
[ JwtAuthenticationFilter ]   ← 토큰 추출 → 검증 → revokedAfter 확인 → SecurityContext 세팅
    │
    ▼
[ Spring Security FilterChain ]
    │
    ▼
[ Controller / OAuth2 Handler ]
```

### 패키지 위치

| 역할 | 경로 |
|------|------|
| 토큰 생성·검증 | `security/jwt/JwtTokenProvider` |
| 인증 필터 | `security/jwt/JwtAuthenticationFilter` |
| 예외 처리 필터 | `security/jwt/JwtExceptionFilter` |
| 보안 설정 | `config/SecurityConfig` |
| 컨트롤러 주입용 Principal | `security/auth/AuthUser` |
| 로그인·로그아웃·재발급 서비스 | `domain/auth/service/AuthService` |
| Refresh Token Redis 저장소 | `domain/auth/repository/RefreshTokenRedisRepository` |
| 인증 컨트롤러 | `domain/auth/controller/AuthController` |
| OAuth2 성공 핸들러 | `domain/auth/oauth/OAuth2AuthenticationSuccessHandler` |

---

## 컴포넌트 상세

### JwtTokenProvider

토큰의 생성, 파싱, 검증을 담당하는 핵심 컴포넌트입니다.

**설정값** (`application.yaml`)
```
jwt.secret                  HMAC-SHA 서명 키 (최소 32바이트)
jwt.access-expiration-ms    Access Token 유효 시간 (ms)
jwt.refresh-expiration-ms   Refresh Token 유효 시간 (ms)
```

**주요 메서드**

| 메서드 | 설명 |
|--------|------|
| `createAccessToken(userId, role)` | `sub=userId`, `role` 클레임 포함 JWT 생성 |
| `createRefreshToken()` | UUID 문자열 반환 (서명 없음) |
| `validateToken(token)` | 서명·만료 검증. 실패 시 예외를 그대로 throw (JwtExceptionFilter가 처리) |
| `getAuthentication(token)` | 클레임에서 userId·role 추출 → `AuthUser` 기반 `Authentication` 반환 (DB 조회 없음) |
| `resolveToken(request)` | `Authorization: Bearer <token>` 헤더에서 토큰 추출 |
| `getExpiration(token)` | 남은 유효 시간(ms) 반환. 예외 시 0 반환 |
| `getIssuedAt(token)` | 발급 시각(ms) 반환. revokedAfter 비교에 사용 |

---

### AuthUser

JWT 클레임에서 추출한 인증 정보를 담는 record입니다. SecurityContext의 Principal로 사용되며, 컨트롤러에서 `@AuthenticationPrincipal AuthUser authUser`로 주입받습니다.

```java
public record AuthUser(Long userId, Role role) {}
```

DB 조회 없이 토큰 클레임만으로 생성되므로, 매 요청마다 DB를 조회하던 기존 방식 대비 성능이 개선됐습니다.

---

### JwtAuthenticationFilter

모든 요청에서 한 번 실행되는 필터입니다 (`OncePerRequestFilter`).

**처리 흐름**
```
요청 수신
  │
  ├─ Authorization 헤더 없음 → 필터 통과 (비인증 요청)
  │
  ├─ validateToken() 실패 → JwtExceptionFilter가 에러 응답 반환
  │
  ├─ revokedAfter > issuedAt → 401 반환 (로그아웃 또는 탈취로 무효화된 세션)
  │
  └─ 검증 통과
       → getAuthentication() 호출 (클레임 파싱)
       → SecurityContextHolder에 Authentication(AuthUser) 세팅
       → 필터 통과
```

`revokedAfter` 확인은 `user:{userId}:rtRevokedAfter` 키를 조회해 Access Token의 `iat`와 비교합니다. 개별 블랙리스트 없이 계정 단위로 발급된 모든 이전 토큰을 차단할 수 있습니다.

---

### JwtExceptionFilter

`JwtAuthenticationFilter` 앞에 위치하며, 필터 체인에서 발생하는 JWT 예외를 잡아 표준 JSON 에러 응답으로 변환합니다.

| 예외 | ErrorCode |
|------|-----------|
| `ExpiredJwtException` | `EXPIRED_TOKEN` |
| `SignatureException`, `MalformedJwtException`, `UnsupportedJwtException`, `IllegalArgumentException`, `JwtException` | `INVALID_TOKEN` |
| 그 외 | `UNAUTHORIZED` |

---

### SecurityConfig

Spring Security 필터 체인 전체 설정입니다.

**필터 순서**
```
JwtExceptionFilter → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter
```

**공개 엔드포인트** (인증 불필요)
- `POST /api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`
- `GET /api/posts`, `/api/posts/*`
- `GET /api/events`, `/api/events/*`
- `/api/login/oauth2/**`
- Swagger (`/swagger-ui/**`, `/v3/api-docs/**` 등)
- `/api/test/**`, `/init/**`

**보호 엔드포인트**
| 경로 | 필요 권한 |
|------|-----------|
| `/api/posts/me` | `USER` 또는 `ADMIN` |
| `/api/events/admin` | `ADMIN` |
| `/api/admin/**` | `ADMIN` |
| `/api/user/**` | `USER` 또는 `ADMIN` |
| 나머지 모든 요청 | 인증 필요 |

**CORS 허용 Origin**: `http://localhost:3000`, `http://localhost:5173`

---

### RefreshTokenRedisRepository

Refresh Token을 Redis Hash에만 저장·관리합니다. DB 의존 없이 Redis 안에서 RTR과 재사용 감지를 처리합니다.

**Redis 키 구조**
```
rt:{token}                   Hash { userId, status, issuedAt, expiresAt }
user:{userId}:rtRevokedAfter  String (epoch seconds)
```

**주요 메서드**

| 메서드 | 설명 |
|--------|------|
| `save(userId)` | UUID 생성 → Hash 저장 → token 반환 |
| `rotate(oldToken)` | Lua 스크립트로 원자적 회전. 반환값 0=토큰 없음, -1=재사용 감지, 1=성공 |
| `revokeAll(userId)` | `rtRevokedAfter` 에 현재 시각(epoch) 저장 |
| `getRevokedAfter(userId)` | `rtRevokedAfter` 값 조회. 필터에서 issuedAt 비교에 사용 |

**rotate Lua 스크립트 흐름**
```
oldToken Hash 조회
  ├─ 없음(0) → INVALID_TOKEN 예외
  ├─ status != ACTIVE(-1) → revokeAll → TOKEN_REUSE_DETECTED 예외
  └─ ACTIVE
       → oldToken.status = ROTATED
       → newToken Hash 저장 (ACTIVE, TTL 설정)
       → RotateResult(newToken, userId) 반환
```

---

### AuthService

로컬 로그인 전체 흐름을 조율합니다.

**로그인 (`login`)**
1. 이메일로 User 조회 (소프트 삭제 필터링)
2. OAuth 계정이면 `OAUTH_USER_LOGIN_DENIED` 예외
3. 비밀번호 BCrypt 검증
4. Access Token 생성 + `RefreshTokenRedisRepository.save()` 로 Refresh Token 발급
5. `TokenResponse` 반환

**토큰 재발급 (`reissue`)** — RTR
1. `RefreshTokenRedisRepository.rotate(refreshToken)` 호출 → `RotateResult(newToken, userId)`
2. userId로 User 조회 (role 취득 목적, DB 1회)
3. 새 Access Token 생성
4. `TokenResponse` 반환

**로그아웃 (`logout`)**
1. `RefreshTokenRedisRepository.revokeAll(userId)` 호출 → `rtRevokedAfter` 기록
2. 이후 해당 userId의 모든 Access Token은 필터에서 차단됨

---

### OAuth2 인증 흐름

소셜 로그인 성공 시 `OAuth2AuthenticationSuccessHandler`가 동작합니다.

```
OAuth2 인증 성공
  → Access Token + Refresh Token 생성
  → RefreshTokenRedisRepository.save() 저장
  → 쿠키 설정 후 {redirectUri} 로 리다이렉트
```

**쿠키 설정**

| 쿠키 | HttpOnly | Secure | SameSite | MaxAge |
|------|----------|--------|----------|--------|
| `accessToken` | false | - | Lax | 30분 |
| `refreshToken` | true | true | Lax | 14일 |

`redirectUri` 는 `application.yaml` 의 `oauth2.redirect-uri` 값으로 설정됩니다.

---

## 인증 흐름 요약

### 일반 로그인

```
POST /api/auth/login
  → AuthService.login()
  → JWT Access Token + UUID Refresh Token 발급
  → rt:{token} Hash를 Redis에 저장
  → { accessToken, refreshToken } 응답
```

### 인증이 필요한 API 요청

```
GET /api/... (Authorization: Bearer {accessToken})
  → JwtExceptionFilter (JWT 예외 래핑)
  → JwtAuthenticationFilter
      → 서명·만료 검증 (실패 시 예외 throw → JwtExceptionFilter 처리)
      → user:{userId}:rtRevokedAfter 조회 → iat 비교 (revokedAfter > iat 이면 401)
      → 클레임 파싱 → AuthUser 생성 → SecurityContext 세팅
  → Controller: @AuthenticationPrincipal AuthUser authUser 로 수신
```

### 토큰 재발급

```
POST /api/auth/refresh  { refreshToken }
  → Lua: rt:{oldToken}.status == ACTIVE 확인
      ├─ ACTIVE  → status = ROTATED, rt:{newToken} 신규 저장 (원자적)
      └─ ROTATED → revokeAll(userId) → TOKEN_REUSE_DETECTED 예외
  → User DB 조회 (role 취득)
  → 새 Access Token 발급
```

### 로그아웃

```
POST /api/auth/logout (Authorization: Bearer {accessToken})
  → revokeAll(userId): user:{userId}:rtRevokedAfter = now
  → 이후 해당 userId로 발급된 모든 AT가 필터에서 차단
```

---

## 사용 라이브러리

| 라이브러리 | 용도 |
|------------|------|
| `io.jsonwebtoken (JJWT)` | JWT 생성·파싱·검증 |
| `Spring Security` | 인증·인가 프레임워크 |
| `Spring Data Redis` | Refresh Token 저장, revokedAfter 관리 |
| `Spring Data JPA` | User 조회 (role 취득용) |
