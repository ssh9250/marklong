# 기술 기록 — 설계 판단과 문제 해결

> 개발 과정에서 마주친 설계 문제, 트레이드오프, 버그 추적 중 기술적으로 유의미한 사례를 선별했다.  
> 각 기록은 "무엇을 했는가"보다 "왜 그렇게 결정했는가"에 초점을 맞췄다.

---

## #001 — 연관 엔티티 파생 필드(post.writer) — 정규화 위반 인식과 Object Reference 전환

> `설계` `JPA` `리팩터링`

### 문제 상황

`Post.writer`, `Comment.writer` 필드에 `Member.email`을 직접 복사해 저장하는 방식을 사용했다.
JOIN을 줄이려는 편의 목적이었지만, 이는 같은 정보가 두 테이블에 중복 저장되는 데이터 정규화 위반이다.

```java
// 문제가 있는 구조
// Member 테이블:  id=1, email=hong@test.com
// Post 테이블:    id=1, writer="hong@test.com", member_id=1
// → 동일한 정보가 두 군데에 저장됨

@Entity
public class Post {
    private String writer;   // member.email 복사 저장

    @ManyToOne(fetch = LAZY)
    private Member member;   // 같은 정보를 연관관계로도 보유
}
```

### 원인 분석

문제는 데이터 변경 시 드러났다. 회원이 이메일을 변경하면 `member` 테이블 1건 수정으로 끝나야 하지만, 파생 필드를 사용하는 구조에서는 `post.writer`, `comment.writer` 등 관련 모든 테이블의 레코드를 일괄 업데이트해야 한다. 데이터가 많아질수록 동기화 비용이 선형으로 증가하고, 동기화를 빠뜨리면 두 값이 달라지는 데이터 정합성 문제가 발생한다.

### 해결 과정

파생 필드를 제거하고 연관관계에서 직접 접근하도록 변경했다.

```java
// 수정 후 — 파생 필드 제거, 연관관계에서 직접 접근
post.getMember().getNickname()
post.getMember().getEmail()
```

쿼리에서 작성자 정보가 필요한 경우, `Post → Member` 방향은 N:1 조인이므로 Fetch Join + Paging을 안전하게 사용할 수 있다. 파생 필드로 JOIN을 피하는 것은 실제 성능 이점보다 정합성 비용이 더 크다.

### 교훈

단기적인 편의를 위해 파생 필드를 추가하는 것은 기술 부채다. 특히 "자주 바뀌지 않을 것"이라는 가정은 위험하다. 실무에서는 Object Reference(v1) → ID Reference(v2, MSA 대응) 방향으로 발전하는데, ID Reference는 Aggregate 경계를 명확히 하고 영속성 컨텍스트 의존도를 낮추는 DDD 원칙과도 일치한다.

---

## #002 — Order-OrderItem 순환 의존성 — "먼저 생성, 나중에 연관관계 설정" 패턴

> `설계` `JPA`

### 문제 상황

주문 생성 로직을 구현하면서 양방향 의존성이 교착 상태를 만들었다.

```
OrderItem 생성 → Order 참조 필요 (FK: order_id)
Order 생성    → OrderItem 참조 필요 (totalPrice 계산, 비즈니스 로직)
```

두 엔티티가 서로를 먼저 요구하므로 어느 것을 먼저 생성해야 할지 결정할 수 없었다.

### 검토한 대안

**대안 1 — 단방향 `@OneToMany`(JoinColumn) 구조**  
`@OneToMany`에 `@JoinColumn`을 사용하면 순환이 사라진다. 그러나 JPA 특성상 INSERT 후 FK를 채우는 별도의 UPDATE SQL이 추가로 발생한다.

**대안 2 — 중간 저장**  
`OrderItem`을 먼저 `save()`한 뒤 Order에 추가한다. `order_id = null`인 불완전한 상태가 DB에 저장되는 위험이 있고, 트랜잭션 중간에 불필요한 flush가 발생한다.

**대안 3 — 생성 메서드에 Order 직접 전달**  
`OrderItem.create(order, item, quantity)` 방식을 시도했지만 Order도 OrderItem이 필요한 상황이므로 교착 자체가 해소되지 않는다.

### 채택한 해결 방법: "먼저 생성, 나중에 연관관계 설정"

```java
// 1단계: OrderItem을 Order 없이 생성 (가격 스냅샷, 재고 검증 포함)
public static OrderItem create(Item item, Integer quantity) {
    if (item.getStock() < quantity) {
        throw new StockNotEnoughException(item.getId()); // fail-fast
    }
    item.removeStock(quantity);
    return OrderItem.builder()
            .item(item)
            .quantity(quantity)
            .price(item.getPrice()) // 주문 시점 가격 스냅샷
            .build();
    // order 필드는 아직 null
}

// 2단계: Order 생성
Order order = Order.create(member, requestDto.getAddress());

// 3단계: 연관관계 설정 (패키지 프라이빗 assignOrder 호출)
public void addOrderItem(OrderItem orderItem) {
    this.orderItem = orderItem;
    orderItem.assignOrder(this); // 패키지 프라이빗 — Service에서 직접 호출 불가
    calculateTotalPrice();       // orderItem 할당 직후 계산
}
```

`assignOrder()`를 패키지 프라이빗으로 두어 `order.addOrderItem()`이라는 단일 진입점 외에는 연관관계를 조작할 수 없도록 강제했다.

`OrderItem.create()` 시점에 재고 검증과 가격 스냅샷을 처리하는 것도 중요한 결정이었다. 이후 상품 가격이 변경되더라도 주문 금액의 정합성이 유지된다.

### 교훈

JPA 양방향 연관관계의 순환 의존성은 "한쪽을 불완전 상태로 먼저 생성하고, 편의 메서드로 나중에 연결"하는 방식으로 해소할 수 있다. 패키지 프라이빗 접근 제어자가 이 패턴을 강제하는 안전장치 역할을 한다.

---

## #003 — 소프트 삭제 — `@SQLDelete` 자동화 vs 수동 방식, Cascade 충돌 관리

> `JPA` `설계`

### 배경

C2C 플랫폼에서 하드 삭제는 데이터 복구 불가, 참조 무결성 파괴, 이력 추적 불가 문제를 일으킨다. 특히 탈퇴 회원의 주문·거래 기록이 사라지면 안 되므로 소프트 삭제를 도입했다.

### 문제 상황

Member에 `cascade = ALL, orphanRemoval = true`가 걸린 상태에서 소프트 삭제를 도입했더니 예상과 다른 일이 일어났다.

```java
// Member.java — cascade ALL이 설정된 상태
@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> posts;

@SQLDelete(sql = "UPDATE member SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Member { ... }
```

`memberRepository.delete(member)` 호출 시 실제 실행 흐름:

1. JPA → `em.remove(member)` 호출
2. `cascade = ALL(REMOVE)` → Post, Comment에도 `em.remove()` 전파
3. Member: `@SQLDelete` 가로챔 → `UPDATE member SET deleted = true` ✓
4. Post: `@SQLDelete` **없음** → `DELETE FROM post` ← **하드 삭제**
5. Comment: `@SQLDelete` **없음** → `DELETE FROM comment` ← **하드 삭제**

소프트 삭제를 의도했지만 자식 엔티티들은 하드 삭제됐다.

### 원인 분석

`@SQLDelete`는 해당 엔티티의 `em.remove()` 호출을 가로채는 것이다. cascade REMOVE가 자식에게 `em.remove()`를 전파하는 것까지 막지는 않는다. 자식 엔티티에 `@SQLDelete`가 없으면 그대로 `DELETE` SQL이 실행된다.

`orphanRemoval`과 Comment의 수동 소프트 삭제 사이에도 충돌이 있었다.

```java
// Post.java
@OneToMany(mappedBy = "post", cascade = ALL, orphanRemoval = true)
private List<Comment> comments;
```

`post.getComments().remove(comment)` 호출 시:
- `orphanRemoval = true` → JPA가 Comment에 `em.remove()` 호출
- Comment에 `@SQLDelete` 없음 → **하드 DELETE**
- `comment.delete()`로 소프트 삭제하려던 의도와 충돌

### 전략별 트레이드오프

| 방식 | 장점 | 단점 |
|---|---|---|
| `@SQLDelete` + `@SQLRestriction` | 서비스 코드 변경 없음, 실수 방지 강력 | 관리자 우회 시 native query 필요, cascade 주의 |
| 수동 `entity.delete()` | 조회 유연성(관리자/일반 분리 가능) | 필터 누락 시 버그 위험, 명시적 처리 필요 |

### 채택한 결정

`cascade = ALL`에서 `REMOVE`를 제거하고, 탈퇴 처리는 이벤트(`MemberWithdrawEvent`)를 통해 서비스 레이어에서 명시적으로 순서대로 처리하도록 변경했다.

```
댓글 삭제 → 게시글의 댓글 삭제 → 게시글·첨부파일 삭제 → 아이템 삭제 → 회원 삭제
```

Comment는 관리자가 삭제된 댓글도 조회해야 하므로 `@SQLRestriction` 없이 수동 방식을 유지하고, Repository 쿼리에서 조건을 명시적으로 제어했다.

### 교훈

`@SQLDelete`와 cascade는 독립적으로 동작한다. 소프트 삭제를 도입할 때는 cascade REMOVE가 어느 엔티티까지 전파되는지, 그 엔티티에 `@SQLDelete`가 있는지를 반드시 확인해야 한다. `orphanRemoval`도 동일하게 하드 DELETE를 유발한다.

---

## #004 — N+1 해결: 1:N Fetch Join + 페이징 금지, QueryDSL Projection으로 우회

> `JPA` `성능`

### 문제 상황

게시글 목록 API(`GET /api/posts`)에서 작성자 닉네임과 댓글 수를 함께 반환해야 했다. 처음에는 `@Query`로 fetch join을 사용했지만 댓글 수 집계가 불가능했고, 댓글을 포함하면 페이징과 충돌이 발생했다.

```java
// 1단계: JPQL + fetch join (초기)
@Query(value = "select p from Post p join fetch p.member",
       countQuery = "select count(p) from Post p")
Page<Post> findAllWithMember(Pageable pageable);
// → 댓글 수 집계 불가, 동적 조건 추가 어려움
```

### 원인 분석

1:N Fetch Join + Paging 조합의 근본 문제를 파악해야 했다.

**N:1 조인 (Post → Member)**  
Join 결과 row 수 = Post 수. Cartesian Product 없음 → **Fetch Join + Paging 안전**.

**1:N 조인 (Post → Comments, Collection Fetch Join)**  
Comment가 100개인 게시글 1개를 fetch join하면 결과 row가 100개가 된다. JPA는 DB에서 페이징이 불가능한 것을 알고, **메모리에서 전체 데이터를 가져온 뒤 잘라낸다**. Hibernate는 다음 경고를 출력한다.

```
HHH90003004: firstResult/maxResults specified with collection fetch;
applying in memory
```

수천 건의 게시글이 있을 경우 전체를 메모리에 올리므로 OOM 위험이 있다.

### 해결 과정: QueryDSL + 일반 Join + groupBy + Projection

```java
List<PostListDto> content = queryFactory
    .select(Projections.constructor(PostListDto.class,
        post.id, post.title, post.content,
        member.nickname,
        post.createdAt,
        comment.count()        // DB 레벨에서 집계
    ))
    .from(post)
    .join(post.member, member) // N:1 — row 수 변하지 않음
    .leftJoin(post.comments, comment) // 댓글 없는 게시글도 포함
    .groupBy(post.id)          // 게시글 단위로 집계
    .orderBy(post.createdAt.desc())
    .offset(pageable.getOffset())
    .limit(pageable.getPageSize())
    .fetch();
```

이 코드는 `Post → Comments`(1:N)임에도 Paging이 안전하다. **Fetch Join이 아닌 일반 Join + groupBy + 집계 함수**이기 때문이다. Fetch Join은 연관 엔티티 객체 전체를 영속성 컨텍스트에 로딩하므로 row가 뻥튀기되지만, 일반 Join + DTO Projection은 SQL 집계 결과를 직접 받으므로 row 수가 변하지 않는다.

count 쿼리는 반드시 content 쿼리와 분리했다.

```java
long total = Optional.ofNullable(queryFactory
    .select(post.count())
    .from(post)
    .join(post.member, member) // content 쿼리의 join 조건과 동기화 필수
    .where(searchConditions)
    .fetchOne()).orElse(0L);
```

count 쿼리에서 member join을 빠뜨리면, content 쿼리의 `writerContains()` 동적 조건이 `member.nickname`을 참조할 때 오류가 발생한다. `fetchOne()`은 결과 없을 때 null을 반환하므로 `Optional.ofNullable(...).orElse(0L)` 방어도 필수다.

### 핵심 정리

| 상황 | Paging 안전 여부 | 이유 |
|---|---|---|
| N:1 Fetch Join + Paging | 안전 | row 수 변동 없음 |
| 1:N Fetch Join + Paging | **위험** | 메모리 페이징, OOM 위험 |
| 일반 Join + groupBy + DTO | 안전 | DB 레벨 집계, row 수 변동 없음 |

### 교훈

페이징과 Fetch Join을 조합하기 전에 N:1인지 1:N인지 먼저 확인해야 한다. 댓글 수 같은 집계 데이터는 Fetch Join이 아닌 일반 Join + groupBy + count()로 DB에서 직접 계산하는 것이 안전하다.

---

## #005 — `Page<T>` Redis 캐싱 직렬화 실패 — 우회책이 쌓이는 구조를 인식하고 롤백

> `성능` `설계`

### 배경

조회수 Write-behind 패턴(`Redis INCR + @Scheduled flush`)은 단순 Long 값을 다뤄 문제없이 동작했다. 이 경험을 바탕으로 게시글 목록 조회(`Page<PostListDto>`) 결과도 `@Cacheable`로 Redis에 캐싱해 DB 부하를 줄이려 했다.

### 발생한 문제

**문제 1 — PageImpl 역직렬화 실패**

Redis에 저장은 됐지만 두 번째 요청에서 꺼낼 때 실패했다. `PageImpl`에는 Jackson 역직렬화에 필요한 기본 생성자나 `@JsonCreator`가 없다.

```
Cannot construct instance of `org.springframework.data.domain.PageImpl`
(no Creators, like default constructor, exist):
cannot deserialize from Object value
```

**문제 2 — LocalDateTime 직렬화 실패**

`PostListDto.createTime` 필드가 `LocalDateTime` 타입인데, Jackson 기본 설정은 Java 8 날짜 타입을 지원하지 않는다.

```
Java 8 date/time type `java.time.LocalDateTime` not supported by default:
add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
```

### 시도한 해결 과정

각 문제는 해결 가능했지만, 해결책이 또 다른 복잡도를 낳는 구조가 됐다.

```
PageImpl 직렬화 안 됨       → CachePage<T> 커스텀 DTO 도입
                               → 서비스/컨트롤러 반환 타입 전부 변경

LocalDateTime 직렬화 안 됨  → CacheConfig에 JavaTimeModule 등록
                               → CacheConfig 복잡도 증가

제네릭 타입 정보 소실        → DefaultTyping 설정 추가
                               → 보안 고려사항 발생
```

```java
// 시도 1 — CachePage 커스텀 DTO
public class CachePage<T> implements Serializable {
    private List<T> content;
    private int pageNumber, pageSize, totalPages;
    private long totalElements;
}

// 시도 2 — CacheConfig 복잡도
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
objectMapper.activateDefaultTyping(
    objectMapper.getPolymorphicTypeValidator(),
    ObjectMapper.DefaultTyping.NON_FINAL  // 보안 이슈 발생 가능
);
```

### 최종 결정 — 목록 캐싱 제거

"각각의 문제는 해결 가능하지만, 해결책이 쌓일수록 원래 목적인 DB 부하 감소를 위한 엔지니어링 비용이 기대 효과를 초과한다"고 판단했다.

`@Cacheable`, `@CacheEvict`, `CachePage`, `ObjectMapper` 커스텀 설정을 전부 제거하고 단순 DB 조회로 롤백했다. Write-behind 조회수 캐싱(단순 Long 값 + StringRedisTemplate)은 그대로 유지했다.

```java
// 롤백 후 — 단순 DB 조회
@Transactional(readOnly = true)
public Page<PostListDto> searchPosts(PostSearchConditionDto cond, Pageable pageable) {
    return postRepository.searchPosts(cond, pageable);
}
```

### 교훈

캐싱 도입 전에 "이 타입이 직렬화 가능한가"를 먼저 검토해야 한다. `Long`이나 `String` 같은 단순 값은 쉽지만, Spring 내부 클래스(`PageImpl`)나 Java 8 날짜 타입(`LocalDateTime`)이 포함된 객체 그래프는 설정 비용이 예상보다 훨씬 크다. "동작은 하지만 복잡도가 과도하다"고 판단해 단순한 방향으로 되돌리는 것 자체가 중요한 기술적 판단이다.

---

## #006 — 낙관적 락 + `@Retryable` 고부하 환경 이슈

> `동시성` `성능` `버그`

### 배경

재고 차감 동시성 제어를 위해 `Item` 엔티티에 `@Version` 낙관적 락과 `@Retryable`을 도입했다. 부하 테스트(k6, 50 VUs, 10s) 중 두 가지 문제가 연달아 발생했다.

### 버그 1 — `StockNotEnoughException`이 `@Retryable` 내부로 진입

#### 증상

```
ExhaustedRetryException: Cannot locate recovery method
  Caused by: StockNotEnoughException: 재고가 부족합니다.
```

`@Retryable(retryFor = ObjectOptimisticLockingFailureException.class)`로 설정했음에도 `StockNotEnoughException` 발생 시 500이 반환됐다.

#### 원인

spring-retry의 동작 방식을 잘못 이해하고 있었다. **`retryFor`는 재시도할 예외를 지정하는 것이지, 나머지 예외의 `@Recover` 탐색을 막지 않는다.** `retryFor`에 없는 예외가 발생해도 spring-retry는 `@Recover` 메서드를 탐색하고, 타입이 맞는 `@Recover`가 없으면 `ExhaustedRetryException`으로 감싸 던진다.

#### 해결

```java
@Retryable(
    retryFor  = {ObjectOptimisticLockingFailureException.class},
    noRetryFor = {StockNotEnoughException.class, ItemNotFoundException.class}, // 명시적 제외
    maxAttempts = 5,
    backoff = @Backoff(delay = 100)
)

@Recover
public OrderDetailDto recoverCreateOrder(RuntimeException e, ...) {
    if (e instanceof ObjectOptimisticLockingFailureException
            || e instanceof CannotAcquireLockException) {
        throw new OptimisticLockingFailureException("요청 충돌이 발생하였습니다.");
    }
    throw e; // StockNotEnoughException 등은 그대로 re-throw
}
```

### 버그 2 — H2에서는 없던 MySQL InnoDB 데드락 발생

#### 증상

H2 환경에서 93% 성공하던 낙관적 락이 MySQL로 전환하자 데드락이 발생했다.

```
CannotAcquireLockException: Deadlock found when trying to get lock
  update item set stock=?, version=? where id=? and version=?
```

#### 원인

**낙관적 락은 커밋 시점에 version 불일치를 감지하는 방식이다.** 그 이전에 InnoDB 행 잠금(row lock) 경합이 먼저 발생하면 DB 레벨 데드락을 막지 못한다. H2는 InnoDB의 갭 락(gap lock), 넥스트 키 락(next-key lock) 같은 복잡한 잠금 메커니즘이 없어 동일 조건에서 데드락이 발생하지 않았다. 테스트 환경(H2)과 운영 환경(MySQL)의 동시성 동작이 다른 것이다.

#### 해결

```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class,
                CannotAcquireLockException.class}, // 데드락도 재시도 대상
    noRetryFor = {StockNotEnoughException.class, ItemNotFoundException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 100)
)
```

### 비교 실험 — 낙관적 락 vs 비관적 락 (k6, 50 VUs, 10s)

MySQL 데드락 확인 후 비관적 락(`SELECT FOR UPDATE`)과의 트레이드오프를 직접 측정했다.

```java
// 비관적 락 적용
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Item i WHERE i.id = :id")
Optional<Item> findByIdWithLock(@Param("id") Long id);
```

| 항목 | 락 없음 | 낙관적 락 (H2) | 낙관적 락 (MySQL) | 비관적 락 |
|---|---|---|---|---|
| 성공률 (201) | 21% | 93% | 63% | 56%* |
| 충돌/오류 (409) | 500 데드락 | 6% | 36% | 0% |
| 평균 응답시간 | 빠름 | 2~3ms | 2~3ms | **70ms** |
| 데이터 정합성 | 미보장 | 보장 | 보장 | 보장 |
| 데드락 | 있음 | 없음 | **있음** | 없음 |

*재고 소진으로 인한 400 포함

비관적 락은 충돌을 원천 차단하지만 락 대기(blocking) 비용으로 응답시간이 낙관적 락 대비 약 **20~30배** 느려졌다(2ms → 70ms). 재고처럼 동일 행에 대한 경합이 매우 심한 도메인에서는 낙관적 락의 retry 성공률도 낮아진다.

### 교훈

1. **`noRetryFor` 누락은 흔한 실수다.** `retryFor`에 없는 예외가 `@Recover`를 찾지 못해 `ExhaustedRetryException`으로 감싸지는 동작은 직관적이지 않다. 재시도가 필요 없는 도메인 예외는 반드시 `noRetryFor`에 명시해야 한다.
2. **동시성 테스트는 반드시 운영 DB(MySQL)에서 진행해야 한다.** H2는 InnoDB 잠금 메커니즘이 없어 MySQL에서 발생하는 데드락을 재현하지 못한다.
3. **낙관적 락은 충돌이 드문 환경에 적합하다.** 재고 차감처럼 동일 행 경합이 집중되는 도메인에서는 MySQL 데드락과 낮은 성공률이 문제가 된다. 비관적 락은 정합성을 보장하지만 처리량(TPS)이 낮아진다. 락 전략은 도메인의 경합 특성과 성능 요구사항을 함께 고려해 선택해야 한다.

---

## #007 — `@SQLRestriction` 관리자 우회 전략 탐색 — Hibernate Filter + AOP의 한계와 Native Query 채택

> `설계` `JPA` `트레이드오프`

### 배경

`@SQLRestriction("deleted = false")`를 핵심 도메인(Member, Order)에 적용했다. 이 어노테이션은 해당 엔티티를 대상으로 하는 모든 JPQL 쿼리에 `deleted = false` 조건을 자동으로 삽입한다. 일반 사용자 기능에서는 소프트 삭제된 데이터가 자동으로 걸러지므로 편리하다.

그런데 관리자 기능을 구현하면서 문제가 생겼다. 탈퇴 회원 목록 조회처럼 소프트 삭제된 데이터 자체가 필요한 경우, `@SQLRestriction`이 JPQL 레이어에서 조건을 강제하므로 일반적인 방법으로는 우회할 수 없었다.

### 시도 1 — Hibernate `@FilterDef` + `@Filter` + Session 직접 제어

`@SQLRestriction`은 토글이 불가능하다. 이를 제거하고 Hibernate `@FilterDef` + `@Filter`로 교체하면 Session 단위로 필터를 켜고 끌 수 있다.

```java
@FilterDef(name = "deletedFilter", parameters = @ParamDef(name = "deleted", type = Boolean.class))
@Filter(name = "deletedFilter", condition = "deleted = :deleted")
@Entity
public class Member { ... }

// 일반 서비스 — 필터 활성화
Session session = entityManager.unwrap(Session.class);
session.enableFilter("deletedFilter").setParameter("deleted", false);

// 관리자 서비스 — 필터 비활성화
session.disableFilter("deletedFilter");
```

가능한 방법이었지만, 서비스 계층마다 Session을 꺼내 필터를 수동으로 조작해야 하므로 코드가 분산되고 빠뜨릴 경우 버그 위험이 있었다.

### 시도 2 — Spring AOP로 필터 자동 활성화 + 관리자 서비스에서만 비활성화

코드 분산 문제를 해결하기 위해 AOP를 도입했다. Around Advice로 서비스 메서드 진입 시 자동으로 필터를 활성화하고, 관리자 서비스 메서드는 AOP 적용 대상에서 제외해 비활성화 상태를 유지하는 방식이다.

```java
@Around("execution(* com.study.shop..service.*.*(..)) "
      + "&& !within(com.study.shop.admin.service..*)")
public Object applyDeletedFilter(ProceedingJoinPoint pjp) throws Throwable {
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("deletedFilter").setParameter("deleted", false);
    try {
        return pjp.proceed();
    } finally {
        session.disableFilter("deletedFilter");
    }
}
```

겉으로는 깔끔해 보였지만 구조적인 문제가 있었다.

**문제 — 동일 트랜잭션 내에서 필터 상태가 덮어써진다**

관리자 서비스가 AOP 대상 밖이라 필터가 꺼진 상태로 동작하더라도, 같은 트랜잭션 내에서 일반 서비스 메서드를 호출하는 순간 그 메서드의 AOP Advice가 다시 필터를 활성화한다. Hibernate 필터는 Session(= 트랜잭션) 범위로 관리되는데, AOP는 메서드 단위로 동작하기 때문에 상태가 메서드 진입마다 덮어써진다.

```
AdminService.kickMember()          ← AOP 제외, 필터 OFF
  └─ MemberService.findById()      ← AOP 적용, 필터 ON으로 복구
       → 소프트 삭제된 회원 조회 실패 (의도와 반대)
```

서비스 간 호출 구조가 복잡해질수록 필터 상태 추적이 불가능해진다.

### 최종 결정 — Native Query

`@SQLRestriction`은 그대로 유지하고, 관리자 전용 Repository 메서드에만 `nativeQuery = true`를 적용했다. Native Query는 Hibernate ORM 레이어를 우회하므로 `@SQLRestriction`이 적용되지 않는다.

```java
// 관리자 전용 — native query로 @SQLRestriction 우회
@Query(value = "SELECT * FROM member WHERE deleted = true", nativeQuery = true)
List<Member> findAllDeletedMembers();

// 일반 조회 — @SQLRestriction 자동 적용 (deleted = false 조건 삽입)
List<Member> findAll();
```

MySQL 문법에 대한 DB 의존성이 생기지만, 관리자 조회 쿼리는 수가 한정적이고 변경 빈도가 낮다. 필터 상태를 트랜잭션 전반에서 안전하게 관리하는 복잡도와 비교하면 감수할 만한 트레이드오프다.

### 교훈

`@SQLRestriction`과 `@Filter`/`@FilterDef`는 서로 다른 Hibernate 메커니즘이다. `@SQLRestriction`은 항상 적용되고 토글이 불가능하다. `@Filter`는 Session 단위로 제어할 수 있지만, AOP로 자동화하면 AOP(메서드 범위)와 Hibernate 필터(Session 범위) 사이의 범위 불일치 때문에 상태 예측이 어려워진다. "자동화"가 오히려 예측 불가능성을 만드는 경우, 범위가 제한된 명시적 해결책이 낫다.
