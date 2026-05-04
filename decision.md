# 설계 결정 기록 (Architecture Decision Records)

> 프로젝트 전반에 걸쳐 내린 주요 설계 판단을 시간 순서로 정리합니다.

---

## [2026-05-04] 권한 검증 위치 — Entity 내부 vs Service 계층

- 이유: update/delete 시 소유자 검증 로직을 어느 계층에 둘지 기준이 필요했다. 처음에는 Entity가 스스로 권한을 검증하는 방향으로 update/delete 모두 `entity.update(Long userId, ...)` / `entity.delete(Long userId)` 형태로 작성하려 했다.
- 검토 과정: delete를 구현하면서 `SoftDeleteEntity`에 이미 `delete()` 메서드가 존재하는 상황에 맞닥뜨렸다. 여기에 권한 검증까지 합쳐 감싸면 메서드 계층이 늘어나고 복잡도만 증가한다고 판단했다. 이 시점에 update도 함께 재검토 — Entity 내부 검증 방식을 update에만 유지하면 update/delete의 처리 방식이 달라져 코드 일관성이 깨진다.
- 대안: Entity 내부 검증 유지 → update/delete 처리 방식 불일치, `SoftDeleteEntity.delete()` 재사용 불가
- 결정: 권한 검증은 Service 계층에서 일괄 처리, Entity는 비즈니스 로직 수행만 담당. `update(Long userId, ...)` 방식에서 Service가 검증 후 `entity.update(...)` 호출로 전환. 개발 일관성을 우선 기준으로 삼아 계층별 책임을 단일화

---
