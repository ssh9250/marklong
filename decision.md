# 설계 결정 기록 (Architecture Decision Records)

> 프로젝트 전반에 걸쳐 내린 주요 설계 판단을 시간 순서로 정리합니다.

---

## [2026-05-04] 권한 검증 위치 — Entity 내부 vs Service 계층

- 이유: update/delete 시 소유자 검증 로직을 어느 계층에 둘지 기준이 필요했다. 처음에는 Entity가 스스로 권한을 검증하는 방향으로 update/delete 모두 `entity.update(Long userId, ...)` / `entity.delete(Long userId)` 형태로 작성하려 했다.
- 검토 과정: delete를 구현하면서 `SoftDeleteEntity`에 이미 `delete()` 메서드가 존재하는 상황에 맞닥뜨렸다. 여기에 권한 검증까지 합쳐 감싸면 메서드 계층이 늘어나고 복잡도만 증가한다고 판단했다. 이 시점에 update도 함께 재검토 — Entity 내부 검증 방식을 update에만 유지하면 update/delete의 처리 방식이 달라져 코드 일관성이 깨진다.
- 대안: Entity 내부 검증 유지 → update/delete 처리 방식 불일치, `SoftDeleteEntity.delete()` 재사용 불가
- 결정: 권한 검증은 Service 계층에서 일괄 처리, Entity는 비즈니스 로직 수행만 담당. `update(Long userId, ...)` 방식에서 Service가 검증 후 `entity.update(...)` 호출로 전환. 개발 일관성을 우선 기준으로 삼아 계층별 책임을 단일화

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