# 설계 결정 기록 (Architecture Decision Records)

> 프로젝트 전반에 걸쳐 내린 주요 설계 판단을 시간 순서로 정리합니다.

---

## [2026-04-18] 주 DB로 TimescaleDB(PostgreSQL 호환) 채택
- 이유: 주가(StockPrice)는 시간축으로 누적되는 시계열 데이터. 일반 RDB로도 가능하지만 시계열 압축·hypertable·연속 집계 등 추후 최적화 여지를 열어두고 싶었음
- 대안: 순수 PostgreSQL → 시계열 특화 기능 부재. 별도 시계열 DB(InfluxDB 등) 추가 → 운영 스택 이원화·JPA 비호환
- 결정: `timescale/timescaledb:latest-pg16` 채택. PostgreSQL 와이어 호환이라 JDBC 드라이버·JPA·Dialect는 PostgreSQL 그대로 사용, 도입 비용 최소화. `init-scripts`로 extension 초기화

---

## [2026-04-19] JWT + DB Refresh Token 인증 체계 도입
- 이유: stateless 특성으로 수평 확장에 적합. Access Token(단기)으로 매 요청 인증, Refresh Token(장기)으로 재발급
- 대안: 세션 방식 → 서버 상태 의존, 스케일 아웃 시 세션 공유 문제
- 결정: Access Token(JWT) + Refresh Token(초기 DB 저장). 추후 Redis 이중화·RTR·blacklist 단계적 도입 전제

---

## [2026-04-29] JWT 패키지를 global.jwt → security.jwt 로 이동
- 이유: JWT·필터·UserDetails는 인증/인가 관심사. `global`(공통 유틸·예외·응답)에 두면 보안 코드가 범용 공통 코드와 섞임
- 대안: global 유지 → 보안 관심사 분산, 책임 경계 모호
- 결정: `security/jwt`(토큰·필터), `security/auth`(UserDetails), `security/util`로 보안 코드를 한 축으로 응집. CLAUDE.md 패키지 원칙으로 명문화

---

## [2026-04-30] JWT subject를 email → userId 기반으로 전환
- 이유: email은 사용자가 변경 가능한 값이라 토큰 수명 내 정합성 부담. JWT payload는 디코딩으로 읽혀 email(PII) 노출. 내부 조회는 모두 userId 기준
- 대안: email subject 유지 → email 변경 시 토큰-식별자 불일치, PII 노출
- 결정: Access Token subject = `userId`, role은 커스텀 클레임. Refresh Token은 클레임 없는 UUID(불투명 토큰)로 단순화. `loadUserById()` 추가

---

## [2026-05-04] 권한 검증 위치 — Entity 내부 vs Service 계층

- 이유: update/delete 시 소유자 검증 로직을 어느 계층에 둘지 기준이 필요했다. 처음에는 Entity가 스스로 권한을 검증하는 방향으로 update/delete 모두 `entity.update(Long userId, ...)` / `entity.delete(Long userId)` 형태로 작성하려 했다.
- 검토 과정: delete를 구현하면서 `SoftDeleteEntity`에 이미 `delete()` 메서드가 존재하는 상황에 맞닥뜨렸다. 여기에 권한 검증까지 합쳐 감싸면 메서드 계층이 늘어나고 복잡도만 증가한다고 판단했다. 이 시점에 update도 함께 재검토 — Entity 내부 검증 방식을 update에만 유지하면 update/delete의 처리 방식이 달라져 코드 일관성이 깨진다.
- 대안: Entity 내부 검증 유지 → update/delete 처리 방식 불일치, `SoftDeleteEntity.delete()` 재사용 불가
- 결정: 권한 검증은 Service 계층에서 일괄 처리, Entity는 비즈니스 로직 수행만 담당. `update(Long userId, ...)` 방식에서 Service가 검증 후 `entity.update(...)` 호출로 전환. 개발 일관성을 우선 기준으로 삼아 계층별 책임을 단일화

---

## [2026-05-13] Spring ↔ Django 연동을 Kafka 비동기 이벤트로
- 이유: 뉴스 크롤링·LLM 요약은 수 초~수십 초 소요. Spring이 Django REST를 동기 호출하면 호출 스레드가 묶이고 가용성이 느린 쪽에 종속됨
- 대안: REST 동기 호출 → 강결합·시간적 결합, Django 장애가 Spring 요청 실패로 전파
- 결정: 토픽 분리(`news.crawl-request` 요청 / `news.processed` 결과 / `stock.alert`). 양 시스템을 시간적으로 분리(temporal decoupling). 컨슈머는 at-least-once 가정 → `existsBySourceId()` 멱등 저장, `ErrorHandlingDeserializer`로 poison message 격리

---

## [2026-05-13] Role enum 접두사 제거 (ROLE_USER → USER)
- 이유: `ROLE_` 접두사는 Spring Security 내부 규약이지 도메인 값의 일부가 아님. enum에 박으면 `hasRole`/`SimpleGrantedAuthority`와 접두사 중복·불일치 소지
- 대안: `ROLE_` 유지 + `hasRole` 사용 → 접두사 자동부착 규칙과 충돌 위험
- 결정: enum은 `USER/ADMIN`, SecurityConfig는 `hasAuthority(...)` 사용. CLAUDE.md에 "접두사 없이 그대로 사용" 규칙 명문화

---

## [2026-05-14] 뉴스 본문/메타데이터 테이블 분리 및 차등 보관
- 이유: 본문은 용량이 크지만 목록·검색에 쓰이는 메타(제목·종목·감성·중요도)는 더 오래 보관 가치가 있음. 한 테이블이면 보관 정책 분리 불가
- 대안: 단일 테이블 무기한 보관 → 저장 비용 선형 증가. 단일 테이블 짧은 TTL → 과거 메타 기반 조회 불가
- 결정: `News`(메타) / `NewsContent`(본문) 테이블 분리. 스케줄러로 차등 TTL — 본문 30일(03:00), 메타 90일(03:30). 삭제 시간 분산으로 부하 집중 회피

---

## [2026-05-15] User 도메인을 auth 패키지에서 분리
- 이유: 인증(토큰·로그인)과 사용자(프로필·가입·탈퇴)는 협력하지만 변경 이유가 다름. 한 패키지면 응집도 저하·의존 방향 혼재
- 대안: `domain/auth`에 User 유지 → auth 패키지 비대화, 관심사 혼합
- 결정: `User/Role/OAuthProvider/UserRepository`를 `domain/user`로 이동. `domain/auth`는 인증 흐름만 보유. 의존 방향 `auth → user` 단방향

---

## [2026-05-15] Refresh Token 저장소 Redis + DB 이중화 (Cache-aside)
- 이유: DB 단독은 재발급 트래픽이 DB 부하로 직결. Redis 단독은 휘발성이라 장애 시 전 사용자 강제 로그아웃
- 대안: DB 단독(부하 집중) / Redis 단독(영속성 없음)
- 결정: DB = 원본(source of truth), Redis = cache-aside 캐시. 캐시 미스 시 DB 폴백 후 잔여 TTL로 Redis 워밍. 저장 시 DB upsert(`rotate()`) + Redis set 동기화

---

## [예정] Redis 서킷 브레이커 - Phase 4

**도입 동기**
- Kafka Consumer가 Redis 캐시를 갱신하는 구조에서
  Redis 장애 시 Consumer 전체가 블로킹되는 문제 방지

**전략**
- Read/Write 서킷 분리
    - READ: 실패 시 DB 직접 조회로 폴백
    - WRITE: 실패 시 Redis 스킵, DB만 반영 (eventually consistent 허용)
- 라이브러리: Resilience4j (Spring Boot 3.x 공식 지원)

**적용 시점**
- Kafka + Redis 연동 구현 완료 후
- 부하테스트 전에 적용하여 장애 격리 동작 검증 포함

---

## [예정] 부하테스트 및 서버 스펙 산정 - Phase 4

**목적**
- Kafka + WebSocket + Redis 연동 구현 후 실제 처리 한계 측정
- 적정 서버 스펙 산출 및 병목 지점 식별

**테스트 도구 후보**
- k6 / Gatling / JMeter 중 선택 (현재 미정)

**측정 지표**
- TPS (Transactions Per Second)
- P95 / P99 응답 latency
- Redis 캐시 히트율
- Kafka Consumer lag
- CPU / Heap 사용률 임계점

**시나리오 후보**
- 정상 부하: 예상 동시 사용자 기준 TPS
- 스파이크: 급격한 트래픽 증가 (장 시작 시간대 모사)
- Redis 장애 주입: 서킷 브레이커 동작 검증 연계

**결과 활용**
- 측정된 TPS 기반으로 적정 인스턴스 스펙 산출
- 병목이 Kafka / Redis / DB / 애플리케이션 중 어디인지 식별
- 서킷 브레이커 설정값 튜닝 근거로 활용

**적용 시점**
- Phase 4 Kafka + WebSocket 구현 완료 후
- 서킷 브레이커 적용 완료 후 테스트 실행