## [날짜] JWT + Redis RTR 방식 선택
- 이유: stateless + 토큰 탈취 대응
- 대안: session 방식 고려했으나 C2C 스케일 부적합

## [날짜] QueryDSL 도입
- 이유: 동적 검색 조건, 타입 안전성
- 대안: JPQL 직접 작성 → 유지보수 어려움