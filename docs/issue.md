# 기술 기록 — 설계 판단과 문제 해결

> 개발 과정에서 마주친 설계 문제, 트레이드오프, 버그 추적 중 기술적으로 유의미한 사례를 선별했다.
> 각 기록은 "무엇을 했는가"보다 "왜 그렇게 결정했는가"에 초점을 맞췄다.

---

## #001 — Refresh Token 저장소 이중화 — Redis Cache-aside + DB 원본, RTR 무효화 전략

> `인증` `Redis` `정합성`

### 배경

Refresh Token을 DB에만 두면 재발급 요청마다 DB 조회가 발생하고, Redis에만 두면 Redis 장애 시 모든 사용자의 토큰이 유실된다. 두 저장소의 장점을 동시에 취하면서 RTR(Refresh Token Rotation)까지 얹는 구조가 필요했다.

### 검토한 대안

**대안 1 — DB 단독**
정합성·영속성은 확실하지만 재발급 트래픽이 그대로 DB 부하가 된다.

**대안 2 — Redis 단독**
빠르지만 Redis가 휘발성이라 재시작·장애 시 전 사용자 강제 로그아웃. 토큰의 "원본"을 인메모리에만 두는 것은 위험하다.

**대안 3 — Redis(캐시) + DB(원본) 이중화 (채택)**
DB를 원본(source of truth)으로, Redis를 cache-aside 캐시로 둔다.

### 채택한 해결 방법

```java
// RefreshTokenRepository — Redis 먼저, miss면 DB 폴백 후 캐시 워밍
public String findByUserId(Long userId) {
    String token = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
    if (token != null) return token;                       // 캐시 히트

    RefreshToken rt = jpaRepository.findByUserId(userId)   // 미스 → DB
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    Duration remaining = Duration.between(LocalDateTime.now(), rt.getExpiresAt());
    if (!remaining.isNegative())
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, rt.getToken(), remaining);
    return rt.getToken();
}
```

- **RTR**: `reissue()`에서 토큰 검증 후 새 Access/Refresh를 발급하고 `saveOrUpdate()`로 기존 토큰을 교체(rotate). 같은 Refresh Token으로 두 번 재발급할 수 없다.
- **불일치 시 즉시 무효화**: `RefreshTokenService.validate()`에서 저장값과 다르면 `delete(userId)`로 해당 사용자 토큰을 통째로 삭제 — 탈취 후 재사용 정황을 보수적으로 차단.
- **저장 시 DB·Redis 동기화**: `save()`가 DB upsert(`rotate()`) 후 Redis에 TTL과 함께 set. 캐시 미스 시에는 DB의 잔여 만료시간으로 Redis TTL을 복원.

### 교훈

원본과 캐시의 역할을 명확히 나누는 것이 핵심이다. DB는 "진실", Redis는 "빠른 사본"으로 두면 Redis가 죽어도 DB에서 복구되고, 평상시에는 Redis가 DB 부하를 흡수한다. RTR에서 토큰 불일치는 곧 "정상 흐름이 아님"이므로, 의심스러우면 보존이 아니라 무효화하는 fail-safe 방향이 보안상 옳다.

---

## #002 — JWT 인증 정보 로딩: 매 요청 DB 조회 vs 클레임 기반 — 트레이드오프의 의도적 보류

> `인증` `성능` `트레이드오프`

### 문제 상황

`JwtAuthenticationFilter`는 매 요청마다 `getAuthentication()`을 호출한다. 현재 구현은 토큰에서 userId를 꺼내 `loadUserById()`로 DB에서 사용자 전체를 조회해 SecurityContext에 올린다. 즉, **인증된 모든 요청이 매번 사용자 테이블을 1회 조회**한다.

### 원인 분석

- 토큰 클레임만으로 인증 객체를 구성하면 DB 조회가 사라지지만, 토큰 발급 이후 변경된 권한·탈퇴 상태가 즉시 반영되지 않는다(토큰 만료 전까지 stale).
- 매 요청 DB 조회는 항상 최신 사용자 상태를 보장하지만 트래픽에 비례해 DB 부하가 커진다.

이건 "정합성 ↔ 성능"의 전형적 트레이드오프이고, 현재 트래픽 규모에서는 성능보다 정합성(권한 변경·탈퇴 즉시 반영)이 더 가치 있다고 판단했다.

### 채택한 해결 방법

DB 조회 방식을 유지하되, 클레임 기반 대안을 **주석으로 명시**하고 전환 조건을 코드에 남겼다.

```java
// todo: 트래픽 증가 시 로직 바꾸기
// 현재는 필터에서 매 요청 시 DB 조회를 통해 유저 정보 전체를 security context에 올리고 있지만,
// 트래픽이 증가하면 getAuthentication()을 토큰 클레임 기반으로 변경하거나,
// loadUserById() 결과를 Redis로 캐싱하는 방향으로 개선 필요
// Long userId = Long.parseLong(claims.getSubject());
// String role = claims.get("role", String.class);
// ...
Long userId = getUserId(token);
UserDetails userDetails = userDetailsService.loadUserById(userId);
return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
```

### 교훈

트레이드오프는 "지금 무엇을 택했는가"보다 "언제 다른 선택으로 바꿔야 하는가"를 남기는 것이 중요하다. 클레임 기반/Redis 캐싱이라는 두 가지 탈출구와 전환 트리거(트래픽 증가)를 주석으로 박아두면, 미래의 의사결정이 "왜 이렇게 짰지?"에서 시작하지 않는다. 부하테스트(decision.md Phase 4)에서 이 지점이 병목으로 확인되면 그때 전환하는 것이 근거 있는 최적화다.

---

## #003 — Spring ↔ Django 연동: REST 동기 호출 vs Kafka 이벤트

> `아키텍처` `Kafka` `결합도`

### 문제 상황

뉴스 수집·요약은 Python(Django + 크롤러 + Ollama 요약)이, 서비스 API는 Spring이 담당한다. 두 이종 시스템을 어떻게 연결할지 결정이 필요했다.

```
[Django 크롤러/AI] → ??? → [Spring] → [DB / 사용자]
```

### 검토한 대안

**대안 1 — Spring이 Django REST를 동기 호출**
구현은 단순하지만, 크롤링·LLM 요약은 수 초~수십 초가 걸리는 작업이라 호출 스레드가 묶인다. Django가 느리거나 죽으면 Spring 요청도 함께 실패 — 가용성이 약한 쪽에 종속된다.

**대안 2 — 메시지 브로커(Kafka)를 통한 비동기 이벤트 (채택)**
Spring은 `news.crawl-request`로 크롤 요청을 발행만 하고 즉시 반환. Django는 처리 완료분을 `news.processed`로 발행하고, Spring 컨슈머가 자신의 속도로 소비·저장한다. 양쪽이 시간적으로 분리(temporal decoupling)된다.

### 채택한 해결 방법

- 토픽 분리: `news.crawl-request`(Spring→Django 요청), `news.processed`(Django→Spring 결과), `stock.alert`(발행)
- 컨슈머 멱등성: `NewsService.saveFromKafka()`가 `existsBySourceId()`로 중복 수신 방어 (at-least-once 대응)
- 역직렬화 격리: `ErrorHandlingDeserializer`로 깨진 메시지가 컨슈머를 막지 않게 함 (devlog #003 참조)
- 향후 Redis 캐시를 Kafka 컨슈머가 갱신하는 구조이므로, Redis 장애가 컨슈머를 막지 않도록 서킷 브레이커를 Phase 4 과제로 분리 (decision.md 참조)

### 교훈

이종 언어·이종 처리시간 시스템을 붙일 때 동기 REST는 "느린 쪽이 빠른 쪽을 끌어내린다". 메시지 브로커는 처리량·가용성을 각 시스템이 독립적으로 갖게 해준다. 단, 비동기의 대가로 멱등성·역직렬화 실패·순서 보장 같은 분산 이슈를 떠안으므로, "중복 수신을 가정한 멱등 저장"을 기본값으로 깔아야 한다.

---

## #004 — 거래 이력의 시점 불변성(point-in-time) — 매도 시 평단가 스냅샷

> `도메인 모델링` `정합성`

### 문제 상황

매도 시 실현손익 = `(매도가 − 평단가) × 수량`이다. 그런데 평단가(`Holding.avgPrice`)는 이후 추가 매수로 계속 바뀐다. 거래 이력(`HoldingHistory`)이 `Holding`을 참조해 평단가를 매번 다시 계산하면, **과거 매도 기록의 실현손익이 미래의 매수에 의해 바뀌는** 정합성 붕괴가 발생한다.

### 원인 분석

거래 이력은 본질적으로 "그 시점에 일어난 사실"이다. 사실은 사후에 변하면 안 된다. 그런데 살아있는 엔티티(`Holding`)를 참조해 파생값을 계산하면, 이력이 원장(ledger)이 아니라 "현재 상태의 뷰"로 전락한다.

### 채택한 해결 방법

매도 시점의 평단가를 `HoldingHistory`에 **스냅샷으로 박제**하고, 실현손익을 생성 시점에 확정한다.

```java
// HoldingService.sell() — holding.sell() 호출 전에 평단가를 먼저 캡처
BigDecimal avgPriceAtTrade = holding.getAvgPrice(); // 먼저 캡처
holding.sell(request.getQuantity());
holdingHistoryRepository.save(HoldingHistory.builder()
        .tradeType(TradeType.SELL)
        .price(request.getPrice())
        .avgPriceAtTrade(avgPriceAtTrade)   // 스냅샷
        .build());

// HoldingHistory 생성자 — 실현손익을 생성 시점에 확정
if (tradeType == TradeType.SELL && avgPriceAtTrade != null) {
    this.realizedProfit = price.subtract(avgPriceAtTrade).multiply(quantity);
} else {
    this.realizedProfit = BigDecimal.ZERO;  // 매수는 실현손익 없음
}
```

- `avgPriceAtTrade`는 매수 시 `null`, 매도 시에만 스냅샷 — 거래 타입별 의미 차이를 필드로 표현
- 캡처 순서가 중요: `holding.sell()`이 평단가에 영향 줄 가능성을 고려해 **변경 전에** 캡처

### 교훈

이력성 데이터는 살아있는 엔티티를 참조하면 안 된다. 회계 원장과 같아서, 기록되는 순간의 값을 그대로 박제(스냅샷)해야 사후에 진실이 흔들리지 않는다. 파생값(실현손익)도 조회 시 계산이 아니라 생성 시 확정해 불변으로 둔다. 단, 잔여 과제로 "포트폴리오 배분 수량 합계 > 보유 수량" 오차 가능성이 남아 있다(코드 TODO·CLAUDE.md 명시).

---

## #005 — 토큰 재발급 시 사용자 정보 조회 위치 — 책임 경계 vs 조회 횟수, 그리고 Refresh Token을 불투명 UUID로 유지한 이유

> `인증` `책임 분리` `트레이드오프`

### 문제 상황

`AuthService.reissue()`는 매개변수로 refresh token 문자열 하나만 받는다. 그런데 새 Access Token을 발급하려면 `userId`와 `role`이 필요하다.

```java
// AuthService.reissue() — 실제 흐름
Long userId = refreshTokenService.extractUserId(refreshToken); // ① DB 조회
refreshTokenService.validate(userId, refreshToken);            //   RTR 검증(Redis 우선)
Role role = userRepository.findById(userId)                    // ② DB 조회
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getRole();
String newAccessToken  = jwtProvider.createAccessToken(userId, role);
String newRefreshToken = jwtProvider.createRefreshToken();     // UUID
refreshTokenService.saveOrUpdate(userId, newRefreshToken);     // rotate
```

문제는 사용자 정보를 얻는 데 **DB 조회가 2회** 든다는 점이다.

1. `extractUserId()` → `refreshTokenJpaRepository.findByToken(token)` — refresh token 문자열로 `RefreshToken` 엔티티를 찾아 `userId`를 꺼낸다. Redis는 `refresh:{userId}` 형태로 userId가 key라 **token → userId 역방향 조회가 불가능**하므로, 이 단계는 반드시 DB를 탄다.
2. `userRepository.findById(userId)` — `RefreshToken`은 `role`을 저장하지 않으므로, role을 얻으려면 `User` 엔티티를 다시 조회해야 한다.

### 검토한 대안

**대안 A — reissue 요청 시 `CustomUserDetails`에서 userId·role을 함께 받기**
"어차피 로그인 상태이니 인증 주체에서 꺼내면 된다"고 생각했으나, reissue는 본질적으로 **Access Token이 만료된 시점**에 호출된다. 만료 토큰은 인증 필터가 SecurityContext에 인증을 올리지 않으므로 `CustomUserDetails`를 신뢰 가능하게 확보할 수 없다. 클라이언트가 보내는 userId·role을 그대로 신뢰하는 것은 권한 위조 위험이 있어 기각.

**대안 B — Refresh Token 자체(claims)에 userId·role을 담기**
구현 단순성은 가장 좋다(조회 0회). 그러나
- 토큰 탈취 시 사용자 정보가 그대로 노출된다.
- 서버 측 무효화(RTR 교체·blacklist)를 위해 결국 Redis/DB 검증이 필요하므로 "self-contained JWT라 저장소 불필요"라는 장점이 희석된다.
- Refresh Token의 목적은 "재발급 자격 증명"이지 정보 운반체가 아니다.
→ 불투명(opaque) UUID 난수가 목적·보안·책임 경계에 더 맞다고 판단해 기각. (이전 프로젝트에서도 동일하게 UUID 방식을 채택해 검증된 설계)

**대안 C — `RefreshTokenService`가 user 도메인까지 조회·관리**
조회를 한 서비스로 모으면 호출부는 단순해진다. 그러나 `RefreshTokenService`의 책임은 **refresh token의 생명주기 관리**다. 여기에 user 도메인 조회를 끌어들이면 단일 책임 원칙에 어긋난다. 기각.

### 채택한 해결 방법

`AuthService`(인증·검증 성격의 계층)가 `userRepository`를 직접 호출하고, **reissue 시 DB 조회 2회를 수용**한다.

- 책임 경계를 조회 횟수보다 우선: `token → userId` 해석은 `RefreshTokenService`, `userId → role` 해석은 user 도메인 접근 권한이 있는 `AuthService`. `RefreshTokenService`는 토큰 생명주기에만 집중.
- Refresh Token은 클레임 없는 **불투명 UUID**로 유지(대안 B 기각의 귀결).
- 비용은 빈도로 정당화: reissue는 Access Token 만료 주기(15분~1시간)에 종속되는 **저빈도 경로**다. 2회 조회는 핫패스가 아니므로 감수 가능한 비용으로 판단.

### 교훈

- **책임 경계가 약간의 중복 조회보다 우선한다**: "조회 한 번 줄이기"를 위해 서비스 책임을 흐리면, 단기 이득보다 장기 유지보수 비용이 크다. `RefreshTokenService`는 토큰만, 사용자 도메인 접근은 `AuthService`로 둔 것이 경계를 선명하게 유지한다.
- **최적화 투자는 호출 빈도에 비례해야 한다**: 같은 "DB 조회 vs 대안"이라도 #002(매 요청 인증 필터, 핫패스)와 이 건(만료 시 reissue, 저빈도)은 결론이 다르다 — 판단 기준은 동일하게 *빈도*다. 핫패스가 아닌 곳을 미리 최적화하는 것은 근거 없는 복잡도 추가다.
- **Refresh Token은 정보가 아니라 자격 증명**: 불투명 UUID 유지가 토큰 탈취 시 정보 노출 차단·서버 무효화 일관성·책임 경계 측면에서 claims 방식보다 우수하다. 구현 단순성(claims)보다 장기 보안 설계를 택했다.

> 연관: #001(Refresh Token 저장소 Redis+DB 이중화·RTR 무효화), #002(인증 정보 로딩의 DB 조회 vs 클레임 — 빈도 기반 판단의 다른 적용 사례)

---