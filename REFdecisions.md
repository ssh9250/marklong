# 설계 결정 기록 (Architecture Decision Records)

> 프로젝트 전반에 걸쳐 내린 주요 설계 판단을 시간 순서로 정리합니다.

---

## [2025-09-22] 공통 API 응답 래퍼 ApiResponse<T> 도입
- 이유: Controller마다 응답 형식이 달라 클라이언트 분기 처리가 복잡했고, 예외 발생 시 Spring 기본 에러 응답이 노출됨. GlobalExceptionHandler와 성공/실패 구조를 통일할 필요
- 대안: ResponseEntity만 사용 → API마다 성공 판단 기준이 달라져 유지보수 어려움
- 결정: `success(T data)` / `fail(String message)` 정적 팩토리로 생성하는 제네릭 래퍼 클래스 도입. Controller와 GlobalExceptionHandler 모두 ApiResponse로 통일

---

## [2026-01-15] JWT + Redis RTR(Refresh Token Rotation) 인증 방식 선택
- 이유: stateless 특성으로 수평 확장에 적합하고, RTR 방식으로 Refresh Token 탈취 후 재사용을 감지할 수 있음. C2C 규모의 서비스에서 세션 방식은 서버 메모리 부담이 큼
- 대안: 세션 방식 → 서버 상태 의존, 스케일 아웃 시 세션 공유 문제 발생
- 결정: Access Token(JWT, 단기) + Refresh Token(Redis 저장, 장기) 조합. 토큰 재발급 시 이전 Refresh Token 무효화(rotation). 로그아웃 시 blacklist 등록

---

## [2026-01-21] 통합 테스트에서 SecurityContext·Redis 격리를 @AfterEach로 처리
- 이유: `@Transactional`은 DB 롤백만 처리하고, SecurityContext(ThreadLocal)와 Redis(인메모리)는 별도 초기화가 없으면 테스트 간 오염됨
- 대안: 각 테스트에서 수동 초기화 → 누락 위험 높음
- 결정: `@AfterEach tearDown()`에서 `SecurityContextHolder.clearContext()` + `redisTemplate.flushDb()` 명시적 호출

---

## [2026-02-01] Category 엔티티 다른 인스턴스 필드 직접 접근 → 패키지 프라이빗 메서드로 캡슐화
- 이유: Java는 같은 클래스 내 타 인스턴스의 private 필드 접근을 허용하지만, 객체 간 캡슐화 원칙 위반. `child.parent = this` 직접 대입 방식
- 대안: public setter 노출 → 외부 레이어에서도 연관관계를 직접 조작 가능해짐
- 결정: `changeParent(Category parent)` 패키지 프라이빗 메서드 추가. 같은 패키지 내에서만 호출 가능하여 외부 노출 없이 양방향 연관관계 관리

---

## [2026-02-13] 도메인 엔티티 메서드 접근 제어자 전략 (DDD 캡슐화)
- 이유: 엔티티가 스스로 상태를 관리하는 DDD 원칙 적용. Service 레이어가 엔티티 내부를 직접 조작하는 것을 방지
- 대안: 모두 public → 어디서든 호출 가능해져 도메인 무결성 보장 어려움
- 결정:
    - `public`: 핵심 비즈니스 메서드 (`create()`, `cancel()` 등) — Service에서 호출
    - 패키지 프라이빗: 연관관계 편의 메서드 (`assignMember()`, `addOrderItem()` 등) — 같은 패키지 엔티티 간 협력
    - `private`: 내부 검증/계산 로직 (`validateOrderStatus()` 등) — 외부 접근 불가

---

## [2026-02-13] Order-OrderItem 순환 의존성 — "먼저 생성, 나중에 연관관계 설정" 패턴
- 이유: OrderItem 생성 시 Order 참조 필요, Order 생성 시 OrderItem 참조 필요 → 전형적인 순환 의존성. 어느 쪽을 먼저 생성해야 할지 결정 불가
- 대안: DB에 먼저 저장 후 연결(불완전한 상태 저장 위험), Order를 직접 전달(교착 상태)
- 결정: `OrderItem.create(item, quantity)` — Order 없이 생성. `Order.create(member, address)` — OrderItem 없이 생성. `order.addOrderItem(orderItem)` — 이후 패키지 프라이빗 `assignOrder()`로 연결. 재고 검증과 가격 스냅샷은 `OrderItem.create()` 시점에 수행(fail-fast + 정합성)

---

## [2026-02-20] Post-Comment orphanRemoval + 소프트 삭제 전략 혼용 문제 인식
- 이유: `Post.removeComment()`에서 컬렉션에서만 제거하면 orphanRemoval이 하드 DELETE를 유발. 소프트 삭제 의도와 충돌
- 대안: `@SQLDelete` 일괄 적용 → 전 도메인 통일 가능하지만 관리자 우회 로직 복잡
- 결정: 현재 Member/Order는 `@SQLDelete`+`@SQLRestriction` 방식, Comment는 수동(`comment.delete()`) 방식 혼용 중. **삭제 전략 통일 미완료 (TODO)**

---

## [2026-03-09] 소프트 삭제 — @SQLDelete + @SQLRestriction vs 수동 방식 도입
- 이유: 하드 삭제 시 데이터 복구 불가, 참조 무결성 파괴, 이력 추적 불가, 통계 데이터 손실 문제
- 대안: 하드 삭제 → 단순하지만 데이터 보존 불가. orphanRemoval만 의존 → cascade REMOVE와 충돌
- 결정: Member/Order는 `@SQLDelete(UPDATE deleted=true)` + `@SQLRestriction("deleted=false")` 적용 (서비스 코드 변경 없이 자동 처리). Comment는 `comment.delete()` 수동 방식 (관리자 조회 유연성 확보). `@SQLRestriction` 방식은 실수 방지에 강하지만 관리자 우회에 native query 필요

---

## [2026-03-14] N+1 해결 — QueryDSL + DTO Projection + count 쿼리 분리
- 이유: JPA `@Query` fetch join은 동적 조건 추가 어렵고 1:N + Paging 조합 시 메모리 페이징 경고(OOM 위험). JPQL 유지보수 어려움
- 대안: `@BatchSize` → N+1을 IN 쿼리로 최적화 가능하지만 DTO 직접 반환 불가. 2단계 조회 → 추가 쿼리 발생
- 결정: N:1 조인(Post→Member)은 fetch join + Paging 안전. 1:N 집계(댓글 수)는 일반 join + `groupBy` + `count()` + DTO Projection으로 처리 → row 뻥튀기 없이 DB 레벨 페이징 가능. count 쿼리는 content 쿼리와 분리하여 불필요한 join/orderBy 제거

---

## [2026-03-16] Post 동적 검색 — GET 요청 조건을 @RequestBody → @ModelAttribute로 전환
- 이유: HTTP 스펙상 GET body는 프록시/서버가 무시하거나 거부할 수 있고, Swagger 렌더링 불가, REST 원칙 위반
- 대안: 각 조건을 `@RequestParam`으로 개별 선언 → 조건이 많을 때 메서드 시그니처 비대화
- 결정: `@ModelAttribute PostSearchConditionDto`로 Query Parameter를 DTO에 자동 바인딩. `LocalDateTime` 필드는 `@DateTimeFormat(ISO.DATE_TIME)` 명시 필요. 검색 조건이 없으면 전체 조회와 동일하게 동작하므로 getAllPosts/searchPosts 엔드포인트 통합

---

## [2026-03-16] QueryDSL count 쿼리와 content 쿼리의 join 조건 동기화 원칙
- 이유: content 쿼리에 `member join`이 있어 `writerContains()`가 `member.nickname`을 참조하는데, count 쿼리에 join을 누락하면 쿼리 오류 발생
- 대안: count 쿼리에도 동일한 join을 모두 포함 → 집계 불필요한 join으로 성능 저하 가능
- 결정: 동적 조건에 사용된 테이블은 count 쿼리에도 반드시 포함. `fetchOne()` 결과는 `Optional.ofNullable(...).orElse(0L)` NPE 방어 필수

---

## [2026-03-19] 페이징 전략 — 게시글은 Page(Offset), 상품은 Slice(Cursor) 분리 적용
- 이유: 게시판은 전체 페이지 수와 페이지 번호 UI가 필요(count 쿼리 필수). 상품 목록은 무한스크롤이 자연스럽고 총 개수 불필요. Offset은 데이터 증가 시 성능 저하 구조적 한계
- 대안: 전체 Page → count 쿼리 오버헤드 과다. 전체 Slice → 총 데이터 수를 알아야 하는 게시판 UX 불가
- 결정: Post는 `Page<PostListDto>` + count 쿼리 분리. Item은 `Slice<ItemListDto>` + 복합 커서(createdAt + id). 복합 커서는 동일 createdAt 데이터 중복/누락 방지를 위해 id를 함께 사용

---

## [2026-03-27] 게시글 조회수 — Redis Write-behind 패턴 도입
- 이유: 조회마다 DB UPDATE는 고빈도 쓰기 부하. 조회수는 실시간 정확도보다 최종 일관성이 허용되는 특성
- 대안: DB 직접 UPDATE → 조회 API마다 쓰기 발생. 완전히 제거 → 비즈니스 요구사항 미충족
- 결정: Redis `INCR`로 조회수 누적(key: `view:post:{id}`), `@Scheduled`로 주기적으로 DB에 flush. 단건 조회 응답 시 Redis + DB 합산값 반환. **IP 기반 중복 방지는 이후 추가** (key: `view:post:{id}:{ip}`, setIfAbsent TTL 24h)

---

## [2026-03-28] Page<T> Redis 목록 캐싱 시도 후 롤백 결정
- 이유: `PageImpl` Jackson 역직렬화 실패(생성자/`@JsonCreator` 없음), `LocalDateTime` 직렬화 미지원, 제네릭 타입 정보 소실 → 우회책이 복잡도를 누적하는 구조
- 대안: CachePage 커스텀 DTO + JavaTimeModule + DefaultTyping 조합으로 해결 가능하나 설정 복잡도와 보안 고려사항이 과다
- 결정: 목록 캐싱 제거, 단순 DB 조회로 롤백. 조회수 캐싱(단순 Long 값)과 달리 객체 그래프 캐싱은 직렬화 설정 비용 > 기대 효과. Write-behind 조회수 캐싱은 유지

---

## [2026-03-29] Swagger JWT SecurityScheme 등록 — Bearer 인증 헤더 자동 처리
- 이유: Swagger UI에서 인증이 필요한 API를 테스트할 때마다 Authorization 헤더를 수동 입력해야 하는 불편함
- 대안: Swagger 미사용 또는 별도 도구 사용
- 결정: `SwaggerConfig`에 `SecurityScheme(BEARER_JWT)` + `SecurityRequirement` 전역 등록. Swagger UI의 "Authorize" 버튼에 토큰 한번 입력으로 모든 API 호출 시 자동 포함

---

## [2026-04-01] 비밀번호 변경 — passwordEncoder.encode() 누락으로 평문 저장 버그
- 이유: 초기 구현에서 MemberService.updatePassword()에 `passwordEncoder.encode()` 호출을 누락하여 평문 비밀번호가 DB에 저장됨
- 결정: 반드시 encode 후 저장. 비밀번호 관련 필드는 setter가 아닌 도메인 메서드를 통해서만 변경하도록 하여 실수를 방지

---

## [2026-04-04] Comment API URL 구조 개선 — /api/comments → /api/posts/{postId}/comments
- 이유: Comment는 Post에 종속되는 하위 자원이므로 RESTful 경로 설계 원칙상 부모 자원 경로를 포함해야 함. 기존 `/api/comments`는 Comment를 독립 자원처럼 표현하여 혼란 유발
- 대안: 기존 `/api/comments` 유지 → REST 원칙 위반이지만 단순
- 결정: `/api/posts/{postId}/comments`로 변경. CreateCommentRequestDto에서 postId 필드 제거 (PathVariable로 수신). CommentService.createComment()에 postId 파라미터 추가

---

## [2026-04-06] Slice<T> 직렬화 — hasNext 누락 → SliceResponse<T> 커스텀 DTO 도입
- 이유: Jackson은 `has*()` 접두사를 JavaBean getter로 인식하지 않아 `hasNext()` 미직렬화. `SliceImpl`을 그대로 반환하면 API 응답에 hasNext 필드가 없음
- 대안: `last` 필드(`isLast()` → is* 규칙으로 직렬화)로 대체 → 값이 반대라 혼란. `@JsonProperty("hasNext")` 어노테이션 → SliceImpl 수정 불가
- 결정: `SliceResponse<T>` 커스텀 래퍼 DTO 도입. `hasNext`를 boolean 필드로 명시하여 Jackson이 `isHasNext()`로 직렬화. API 응답 계약을 명확하게 제어

---

## [2026-04-07] C2C 구조 전환 — Order:OrderItem 1:N → 1:1
- 이유: 서로 다른 판매자의 상품이 한 주문에 묶이면 상태 머신(수락/배송/완료), 판매자 권한 검증, 재고 복원 로직이 지나치게 복잡해짐. C2C 중고거래는 1 거래 = 1 상품이 자연스러운 모델
- 대안: 1:N 유지 + 복잡한 상태 관리 → 유지보수 난이도 급증
- 결정: Order:OrderItem = `@OneToOne` 구조로 전환. `calculateTotalPrice()` 호출을 `addOrderItem()` 내부로 이동(NPE 방지). DTO의 `orderItemDtoList`는 List 타입 유지(API 계약 일관성)

---

## [2026-04-08] Order API — 구매/판매 엔드포인트 분리, 스냅샷 필드 도입
- 이유: 구매자/판매자가 각자의 관점에서 주문 목록을 조회해야 함. N+1 없이 권한 검증을 위해 Order에 연관 조회 없이 비교 가능한 식별자 필요
- 대안: 하나의 엔드포인트에서 역할로 분기 → 응답 구조 혼재
- 결정: `/api/orders`(구매자), `/api/orders/sold`(판매자) 분리. Order에 `sellerId`, `buyerEmail` 스냅샷 필드 추가 — 판매자/구매자 탈퇴 후에도 권한 검증 가능. 취소(`DELETE /{id}`)와 거절(`DELETE /{id}/reject`)도 엔드포인트 분리

---

## [2026-04-11] 상태 전이 로직 — OrderStatus Enum으로 이전
- 이유: Order 엔티티에 `accept/startDelivery/complete`마다 상태 검증 로직이 중복됨. 상태 전이 규칙이 분산되어 있어 변경 시 여러 곳 수정 필요
- 대안: Service 레이어에서 상태 전이 조건 관리 → 도메인 로직이 Service에 누출
- 결정: `OrderStatus.next()` — 허용된 상태 순서를 enum 내부에 정의, 불가 상태 시 `InvalidOrderStatusException`. `canCancel()` — 취소 가능 여부 enum에서 판단. `Order.accept/startDelivery/complete` → `orderStatus.next()` 위임으로 단순화

---

## [2026-04-11] 예외 클래스 — 정적 팩토리 메서드로 의미 있는 생성 컨텍스트 부여
- 이유: `new InvalidOrderStatusException(...)` 직접 생성은 어떤 상황에서 발생했는지 호출부에서 명시적으로 드러나지 않음
- 대안: 생성자 오버로딩 → 파라미터 조합으로 의미 구분이 어려움
- 결정: `InvalidOrderStatusException.invalidTransition()`, `.invalidState()`, `.cannotCancel()` 정적 팩토리 메서드 분리. `InvalidAccessException.notOrderable()`. 생성자는 private으로 처리

---

## [2026-04-12] 조회수 증가 — Spring Event + @Async로 메인 트랜잭션과 분리
- 이유: 게시글 단건 조회 API에서 조회수 Redis 증가 처리가 메인 요청 스레드에서 동기로 실행됨. 조회수 처리가 실패해도 게시글 조회는 성공해야 하며, 응답 지연에 영향을 최소화해야 함
- 대안: AOP → 설정 복잡도 증가. 직접 호출 유지 → 결합도 높음
- 결정: `PostViewedEvent` 발행 + `@Async @EventListener`로 처리. AsyncConfig에 `ThreadPoolTaskExecutor` 설정(corePool=2, maxPool=5). 이벤트 처리 실패가 조회 응답에 영향을 주지 않음

---

## [2026-04-13] 재고 동시성 제어 — 낙관적 락 (@Version) + @Retryable 도입
- 이유: 동시 주문 요청 시 재고 차감 경합이 발생. DB에 직접 잠금 없이 충돌을 감지하고 재시도하는 방식으로 정합성 확보
- 대안: 비관적 락(`SELECT FOR UPDATE`) → 경합 시 성능 저하(응답시간 약 20~30배 증가, 2ms → 70ms). DB 수준 직렬화 → 처리량 급감
- 결정: `Item`에 `@Version` 추가. `@Retryable(retryFor=ObjectOptimisticLockingFailureException, maxAttempts=5, backoff=100ms)`. 재시도 불필요한 예외는 `noRetryFor`로 명시적 제외(StockNotEnoughException, ItemNotFoundException)

---

## [2026-04-14] 낙관적 락 → MySQL InnoDB 데드락 발생 — @Retryable 보완 및 비관적 락 비교
- 이유: H2 테스트 환경에서는 93% 성공하던 낙관적 락이 MySQL InnoDB에서는 행 잠금 경합으로 데드락(CannotAcquireLockException) 발생. version 불일치 감지는 커밋 시점, 데드락은 그 이전 행 잠금 단계에서 발생
- 대안: 비관적 락 전환 → 데드락 없음, 정합성 보장, 그러나 평균 응답시간 70ms(낙관적 락 대비 ~30배). 분산 락(Redisson) → 외부 의존성 추가, 복잡도 증가
- 결정: `CannotAcquireLockException`도 `retryFor`에 추가. `@Recover`를 `RuntimeException`으로 범용화하여 StockNotEnoughException 등은 re-throw. `GlobalExceptionHandler`에 lock 예외 통합 핸들러 추가. createOrder는 비관적 락(`findByIdWithLock`, PESSIMISTIC_WRITE) 전환으로 실험

  | 항목 | 낙관적 락(MySQL) | 비관적 락 |
    |---|---|---|
  | 성공률 | 63% | 56%* |
  | 충돌(409) | 36% | 0% |
  | 평균 응답시간 | 2~3ms | 70ms |
  | 데드락 | 발생 | 없음 |

---

## [2026-04-15] 본인 제품 구매 불가 — 판매자/구매자 동일인 검증 추가
- 이유: C2C 플랫폼에서 자신의 상품을 자신이 주문하면 재고가 줄어드는 비즈니스 오류 발생. 자기 거래를 방지해야 함
- 대안: 프론트엔드 차단만 → 서버 검증 없으면 API 직접 호출로 우회 가능
- 결정: OrderService.createOrder()에서 `item.getSellerId().equals(buyerId)` 검증 추가. 본인 상품 주문 시 적절한 예외 반환

---

## [2026-04-21] Docker 배포 환경 — multi-stage 빌드, Nginx 리버스 프록시, .env 환경변수 분리
- 이유: 로컬 개발 환경과 배포 환경의 설정이 하드코딩으로 혼재되어 있었고, 배포 자동화를 위한 컨테이너화가 필요
- 대안: JAR 직접 배포 → 환경별 설정 관리 어려움. 단일 stage 빌드 → 이미지 사이즈 과다(JDK 포함)
- 결정:
    - Dockerfile: `gradle:8.5-jdk17` 빌드 → `eclipse-temurin:17-jre` 실행 multi-stage
    - `docker-compose.infra.yml`로 MySQL·Redis 인프라 분리 (앱과 독립적으로 기동 가능)
    - `nginx/nginx.conf` 리버스 프록시로 외부 트래픽 앱 서버로 전달
    - `application-prod.yml` 하드코딩 값 → 환경변수(`${DB_USERNAME}` 등) 전환
    - `.env.example` 템플릿 제공, `.env`는 `.gitignore`에 등록하여 시크릿 노출 방지

---

## [2026-04-25] QueryDSL DTO Projection — `Projections.constructor()` 방식 채택
- 이유: `@QueryProjection`은 컴파일 타임 타입 체크가 가능하지만 DTO에 `com.querydsl` 의존성이 생겨 재사용성이 떨어짐. `Projections.fields()`는 필드명 기준 리플렉션 매핑으로 IDE 추적 불가, 오타를 컴파일 타임에 잡지 못함
- 대안: `@QueryProjection` → DTO-QueryDSL 결합도 증가. `Projections.fields()` → 안전성 낮음
- 결정: `Projections.constructor()`로 통일. 생성자 파라미터 타입+순서 기준 매핑으로 QueryDSL 의존성 없이 타입 안전성 확보. 파라미터 불일치 시 즉시 예외 발생으로 디버깅 용이

---

## [2026-04-28] Admin 소프트 삭제 우회 전략 — Hibernate Filter/AOP 대신 Native Query 채택

- 이유: `@SQLRestriction`은 JPQL 기반 모든 쿼리에 `deleted = false` 조건을 자동 삽입한다. 관리자 기능에서 소프트 삭제된 데이터를 조회해야 하는 경우 우회 수단이 필요했다. 두 가지 대안을 검토했다.
- 대안 1: `@SQLRestriction`을 제거하고 Hibernate `@FilterDef` + `@Filter`로 교체 — Session 단위로 `session.enableFilter() / disableFilter()`를 호출해 필터를 켜고 끌 수 있다. AOP Around Advice로 일반 서비스 메서드 진입 시 자동 활성화하고, 관리자 서비스에서만 비활성화하는 방식도 검토했다. 그러나 동일 트랜잭션 내에서 관리자 서비스가 다른 서비스 메서드를 호출하면 해당 메서드의 AOP Advice가 다시 필터를 활성화한다. Session 범위의 필터 상태가 메서드 진입 시마다 덮어써질 수 있어 예측이 어렵고, 서비스 간 호출 구조가 복잡해질수록 리스크가 커진다.
- 대안 2: `@SQLRestriction` 유지 + 관리자 전용 Repository에 `nativeQuery = true` 적용 — Native Query는 Hibernate ORM 레이어를 우회하므로 `@SQLRestriction`이 적용되지 않는다. MySQL 문법에 대한 DB 의존성이 생기지만, 관리자 조회 쿼리는 수가 한정적이고 변경 빈도가 낮다.
- 결정: Native Query(`nativeQuery = true`) 채택. 관리자 기능은 특수 목적으로 범위가 제한적이며, Filter 상태를 트랜잭션 경계 전반에서 안전하게 관리하는 복잡도보다 DB 의존성이 감수할 만한 트레이드오프다. 기존 서비스 로직과 `@SQLRestriction` 설정을 그대로 유지하면서 관리자 Repository 메서드에만 국소적으로 적용한다.

---
