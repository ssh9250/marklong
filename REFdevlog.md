# 개발 기록

## 기록 #001: 공통 API 응답 객체(ApiResponse) 도입

### 배경
프로젝트 초기에는 Controller에서 반환 타입을 기본적인 Response Entity로 설정함

### 문제 상황
1. **일관성 없는 응답 형식**: 성공/실패 여부를 판단하는 기준이 API마다 달라 프론트엔드에서 분기 처리가 복잡해짐
2. **에러 응답 구조 부재**: 예외 발생 시 Spring 기본 에러 응답(Whitelabel Error Page 등)이 그대로 노출되어 클라이언트가 에러 메시지를 파싱하기 어려움
3. **GlobalExceptionHandler와의 연동 필요**: 전역 예외 핸들러에서도 동일한 형식으로 에러 응답을 내려줘야 하는데, 공통 응답 객체가 없으면 핸들러마다 별도의 응답 구조를 정의해야 함

### 해결 방법
제네릭 기반의 공통 응답 래퍼 클래스 `ApiResponse<T>` 도입

```java
@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

**적용 예시:**
```java
// Controller - 성공 응답
return ResponseEntity.ok(ApiResponse.success(orderService.createOrder(memberId, requestDto)));

// Controller - 메시지 포함 성공 응답
return ResponseEntity.ok(ApiResponse.success(orderId, "주문이 취소되었습니다."));

// GlobalExceptionHandler - 실패 응답
return ResponseEntity.status(code.getStatus())
        .body(ApiResponse.fail(code.getMessage()));
```

### 관련 파일
- `src/main/java/com/study/shop/global/response/ApiResponse.java`
- `src/main/java/com/study/shop/global/exception/GlobalExceptionHandler.java`

### 교훈
- **응답 일관성은 초기에 확립**: API가 늘어난 뒤 응답 형식을 통일하면 모든 Controller를 수정해야 하므로, 프로젝트 초기에 공통 응답 객체를 정의하는 것이 효율적
- **정적 팩토리 메서드 활용**: `success()`, `fail()` 정적 메서드로 생성을 단순화하여 Controller 코드의 가독성을 높임
- **제네릭으로 유연성 확보**: `ApiResponse<T>`로 어떤 타입의 데이터든 동일한 구조로 감쌀 수 있어 DTO 변경에 영향을 받지 않음
- **GlobalExceptionHandler와의 시너지**: 성공/실패 모두 `ApiResponse` 형식으로 통일되어 클라이언트는 항상 `success` 필드로 결과를 판단할 수 있음

---

## 기록 #002: 회원가입 시 서버 500 에러 발생

**발생일**: 2025-09-22

### 문제 상황
프론트엔드에서 회원가입 폼 제출 시 서버 500 에러 발생

### 원인 분석
Member 엔티티의 password 필드에 설정된 `@Size(max = 50)` 검증 어노테이션이 bcrypt 암호화된 비밀번호(60자 이상)와 충돌

### 해결 방법
```java
// 수정 전
@Column(nullable = false, length = 50)
@Size(min = 6, max = 50)
private String password;

// 수정 후
@Column(nullable = false, length = 200)
private String password;
```

### 관련 파일
- `src/main/java/com/study/shop/domain/member/entity/Member.java`

### 교훈
- 암호화된 데이터의 길이 고려: bcrypt 등 해시 함수 사용 시 결과 길이를 충분히 고려
- 검증 어노테이션 신중 사용: 엔티티 필드의 검증 어노테이션은 비즈니스 로직과 데이터 처리 과정을 모두 고려

---

## 기록 #003: 전역 예외 핸들러로 인한 디버깅 어려움

**발생일**: 2025-09-22

### 문제 상황
모든 예외가 전역 예외 핸들러에서 일괄 처리되어 구체적인 에러 정보 확인 불가

### 원인 분석
```java
// 문제가 된 코드
@ExceptionHandler
public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    return ResponseEntity.status(500)
            .body(ApiResponse.fail("서버 내부에 오류가 발생했습니다."));
}
```

### 해결 방법
```java
// 수정 후
@ExceptionHandler
public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    e.printStackTrace(); // 스택트레이스 출력 추가
    return ResponseEntity.status(500)
            .body(ApiResponse.fail("서버 내부에 오류가 발생했습니다."));
}
```

### 관련 파일
- `src/main/java/com/study/shop/global/exception/GlobalExceptionHandler.java`

### 교훈
- 디버깅 친화적인 예외 처리: 개발 중에는 구체적인 에러 정보를 확인할 수 있도록 로깅 필요

---

## 기록 #004: 테스트 실행 시 SLF4J 로거 충돌로 ApplicationContext 로딩 실패

**발생일**: 2026-01-15

### 문제 상황
LoginControllerTest 등 통합 테스트 실행 시 ApplicationContext 로딩 실패로 모든 테스트 실패

### 오류 메시지
```
java.lang.IllegalStateException: Failed to load ApplicationContext
Caused by: java.lang.IllegalArgumentException: LoggerFactory is not a Logback LoggerContext but Logback is on the classpath.
Either remove Logback or the competing implementation (class org.slf4j.simple.SimpleLoggerFactory)
```

### 원인 분석
클래스패스에 두 개의 SLF4J 로거 구현체가 동시에 존재하여 충돌 발생:
- **Logback** (Spring Boot 기본 로거)
- **slf4j-simple** (embedded-redis 라이브러리의 전이 의존성)

의존성 트리 분석 결과:
```
+--- it.ozimov:embedded-redis:0.7.3
|    +--- org.slf4j:slf4j-simple:1.7.21 -> 2.0.16
```

### 해결 방법
build.gradle에서 embedded-redis의 slf4j-simple 의존성 제외:

```gradle
// 수정 전
testImplementation 'it.ozimov:embedded-redis:0.7.3'

// 수정 후
testImplementation('it.ozimov:embedded-redis:0.7.3') {
    exclude group: 'org.slf4j', module: 'slf4j-simple'
}
```

### 관련 파일
- `build.gradle`

### 교훈
- 전이 의존성 충돌 주의: 서드파티 라이브러리가 가져오는 전이 의존성이 프로젝트의 기본 설정과 충돌할 수 있음
- 의존성 분석 도구 활용: `./gradlew dependencies` 명령으로 의존성 트리를 분석하여 충돌 원인 파악 가능
- 라이브러리 선택 신중: 오래된 라이브러리(embedded-redis 0.7.3, 2017년 릴리즈)는 최신 Spring Boot와 호환성 문제 발생 가능

---

## 기록 #005: SecurityConfig에 signup 엔드포인트 permitAll 누락

**발생일**: 2026-01-21

### 문제 상황
회원가입 API 테스트 시 인증 없이 접근할 수 없어 테스트 실패

### 원인 분석
SecurityConfig의 `authorizeHttpRequests`에서 `/api/auth/signup` 엔드포인트가 `permitAll()`에 포함되지 않아 인증이 필요한 상태로 설정됨

```java
// 문제가 된 코드
.requestMatchers(
    "/api/auth/login",
    "/api/auth/refresh",
    // ... swagger 관련 경로
).permitAll()
// /api/auth/signup이 누락됨
```

### 해결 방법
```java
// 수정 후
.requestMatchers(
    "/api/auth/signup",  // 추가
    "/api/auth/login",
    "/api/auth/refresh",
    // ... swagger 관련 경로
).permitAll()
```

### 관련 파일
- `src/main/java/com/study/shop/global/security/config/SecurityConfig.java`

### 교훈
- 인증이 필요 없는 공개 API(회원가입, 로그인 등)는 반드시 `permitAll()`에 명시적으로 추가
- 새로운 API 엔드포인트 추가 시 Security 설정 검토 필수

---

## 기록 #006: 통합 테스트 간 SecurityContext 및 Redis 데이터 격리 실패

**발생일**: 2026-01-21

### 문제 상황
RefreshAndLogoutControllerTest에서 여러 테스트를 연속 실행 시 일부 테스트 실패
- 이전 테스트의 인증 정보가 SecurityContext에 남아 있어 다음 테스트에 영향
- 이전 테스트에서 저장한 Redis 데이터(refreshToken, blacklist)가 다음 테스트에 영향

### 원인 분석
`@Transactional`은 DB 롤백만 처리하고, SecurityContext와 Redis는 별도로 정리되지 않음
- SecurityContext: 스레드 로컬에 저장되어 테스트 간 공유됨
- Redis: 인메모리 저장소로 트랜잭션 롤백 대상이 아님

### 해결 방법
```java
@AfterEach
void tearDown() {
    // SecurityContext 초기화
    SecurityContextHolder.clearContext();

    // Redis 데이터 초기화
    stringRedisTemplate.getConnectionFactory().getConnection().flushDb();
}
```

### 관련 파일
- `src/test/java/com/study/shop/domain/auth/controller/RefreshAndLogoutControllerTest.java`

### 교훈
- `@Transactional`은 DB만 롤백: SecurityContext, Redis, 외부 시스템 등은 별도 정리 필요
- 테스트 격리 원칙: 각 테스트는 독립적으로 실행될 수 있어야 하며, 다른 테스트에 영향을 주거나 받지 않아야 함
- `@AfterEach` 활용: 테스트 후 상태 정리가 필요한 리소스는 명시적으로 초기화

---

## 기록 #007: Category 엔티티 도메인 메서드에서 캡슐화 위반

**발생일**: 2026-02-01

### 문제 상황
Category 엔티티의 `addChild`, `removeChild` 도메인 메서드에서 `child.parent`에 직접 필드 접근하여 캡슐화를 위반하고 있음

### 원인 분석
같은 클래스 내부이기 때문에 컴파일 에러는 발생하지 않지만, 다른 객체의 private 필드에 직접 접근하는 것은 캡슐화 원칙에 위배됨

```java
// 문제가 된 코드
public void addChild(Category child) {
    this.child.add(child);
    child.parent = this;       // 직접 필드 접근 - 캡슐화 위반
}

public void removeChild(Category child) {
    this.child.remove(child);
    child.parent = null;       // 직접 필드 접근 - 캡슐화 위반
}
```

### 해결 방법
패키지 프라이빗 메서드를 활용하여 parent 변경을 캡슐화

```java
// 수정 후
public void addChild(Category child) {
    this.child.add(child);
    child.changeParent(this);
}

public void removeChild(Category child) {
    this.child.remove(child);
    child.changeParent(null);
}

// 패키지 프라이빗 - 외부 패키지에서는 접근 불가
void changeParent(Category parent) {
    this.parent = parent;
}
```

### 관련 파일
- `src/main/java/com/study/shop/domain/category/entity/Category.java`

### 교훈
- 같은 클래스라도 다른 인스턴스의 필드 직접 접근은 캡슐화 위반: Java는 같은 클래스 내 private 필드 접근을 허용하지만, 객체 간 캡슐화를 위해 메서드를 통한 접근이 바람직
- 패키지 프라이빗 메서드 활용: `changeParent()`를 패키지 프라이빗으로 두면 같은 패키지 내에서만 사용 가능하여, public setter 노출 없이 연관관계 관리 가능
- 양방향 연관관계 편의 메서드 설계 시 캡슐화 고려: JPA 양방향 매핑에서 편의 메서드 작성 시에도 객체지향 원칙을 지키는 방법을 선택

---

## 기록 #008: Item-Category 양방향 ManyToMany 연관관계 구조 개선 필요

**발생일**: 2026-02-01

### 문제 상황
Item과 Category가 양방향 `@ManyToMany`로 연관관계를 맺고 있으며, 이는 일반적으로 지양되는 설계 패턴임

#### 현재 구조
```java
// Category 엔티티
@ManyToMany
@JoinTable(name = "category_item",
        joinColumns = @JoinColumn(name = "category_id"),
        inverseJoinColumns = @JoinColumn(name = "item_id"))
private List<Item> items = new ArrayList<>();
```

### 해결방법: 단방향 ManyToMany로의 전환

**문제점:**

1. **성능 문제** - Category → Item 조회 시 항상 조인 테이블을 거쳐야 하며, N+1 문제 발생 가능성
2. **코드 복잡성 증가** - Repository에서 복잡한 JPQL/QueryDSL 필요, 직관적이지 않은 조회 로직
3. **캐시 효율성 저하** - Category 기준 Item 목록 캐싱 어려움
4. **ORM 최적화 제한** - JPA의 양방향 최적화 기능 활용 불가

- 특정 카테고리로 Item 검색 쿼리:
```sql
SELECT i.* FROM item i
JOIN category_item ci ON i.item_id = ci.item_id
WHERE ci.category_id = ?
```

### 검토한 대안들

#### 1. 중간 엔티티 생성
```java
@Entity
public class CategoryItem {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    // 추가 속성 가능 (등록일, 순서 등)
    private LocalDateTime createdAt;
}
```

#### 2. 단방향 + Repository 메서드
```java
// ItemRepository
List<Item> findByCategoriesId(Long categoryId);

// CategoryService
public List<Item> getItemsByCategory(Long categoryId) {
    return itemRepository.findByCategoriesId(categoryId);
}
```

#### 3. QueryDSL 활용
```java
public List<Item> findItemsByCategory(Long categoryId) {
    return queryFactory
        .selectFrom(item)
        .join(item.categories, category)
        .where(category.id.eq(categoryId))
        .fetch();
}
```

### 결정 사항
현재는 ManyToMany 양방향 관계로 개발을 진행하되, 추후 중간 엔티티 방식으로 리팩토링 예정

### 관련 파일
- `src/main/java/com/study/shop/domain/category/entity/Category.java`
- `src/main/java/com/study/shop/domain/Item/entity/Item.java`

### 교훈
- 양방향 `@ManyToMany` 지양: 중간 테이블을 직접 제어할 수 없고, 추가 컬럼 확장이 불가능하여 실무에서는 중간 엔티티 방식이 권장됨
- 단방향으로도 충분히 해결 가능: Repository 메서드나 QueryDSL을 활용하면 양방향 없이도 양쪽 방향의 조회가 가능
- 설계 결정은 단계적으로: 초기 개발 시에는 단순한 구조로 시작하고, 필요에 따라 리팩토링하는 접근도 유효

**상태:** 미해결 (추후 리팩토링 예정)

---

## 기록 #009: Order 엔티티 접근 제어자를 활용한 DDD 캡슐화 설계

**발생일**: 2026-02-13

### 배경
Order 도메인 엔티티를 설계하면서 메서드의 접근 제어자를 역할에 따라 구분하여 DDD 원칙을 준수하는 캡슐화 전략을 적용

### 적용한 접근 제어자 전략

#### 1. `public` - 핵심 비즈니스 로직 (외부 레이어에서 호출)
```java
// Order.java - 주문 생성
public Order create(Member member, OrderItem... orderItems) { ... }

// Order.java - 주문 취소
public void cancel() {
    if (this.orderStatus != OrderStatus.PENDING) {
        throw new IllegalStateException("주문 수락 전에만 취소할 수 있습니다.");
    }
    this.orderStatus = OrderStatus.CANCELLED;
}

// OrderItem.java - 주문 항목 생성, 취소
public static OrderItem create(Order order, Item item, Integer quantity) { ... }
public void cancel() { ... }
```

**장점:**
- Service 레이어에서 명확하게 호출 가능하여 비즈니스 유스케이스를 직관적으로 표현
- 도메인 모델의 공개 API 역할을 하여 엔티티가 어떤 행위를 할 수 있는지 명확히 드러남
- 테스트 작성이 용이 (외부에서 직접 호출 가능)

**단점:**
- 어디서든 호출 가능하므로 의도하지 않은 곳에서 사용될 위험
- 공개 범위가 넓어 변경 시 영향 범위가 큼

#### 2. 패키지 프라이빗 (default) - 연관관계 편의 메서드 (같은 도메인 패키지 내에서만 호출)
```java
// Order.java
void assignMember(Member member) {
    this.member = member;
    member.getOrders().add(this);
}

void addOrderItem(OrderItem orderItem) {
    this.orderItems.add(orderItem);
    orderItem.assignOrder(this);
}

// OrderItem.java
void assignOrder(Order order) {
    this.order = order;
}
```

**장점:**
- 같은 `domain.order.entity` 패키지 내의 엔티티끼리만 호출 가능하여 도메인 내부 협력을 안전하게 캡슐화
- Service 레이어에서 직접 호출 불가 → 연관관계 설정을 반드시 엔티티의 public 메서드를 통해 수행하도록 강제
- public setter 노출 없이 양방향 연관관계 동기화 가능 (기록 #007의 교훈 적용)

**단점:**
- 같은 패키지에 다른 클래스가 추가되면 의도치 않게 접근 가능
- Java의 패키지 프라이빗은 하위 패키지까지 적용되지 않음 (예: `entity.sub` 패키지에서는 접근 불가)
- 접근 제어자가 명시적으로 보이지 않아 코드 리뷰 시 놓칠 수 있음

#### 3. `private` - 내부 검증 로직 (엔티티 자기 자신만 호출)
```java
// 예시: 주문 상태 검증, 가격 계산 등 내부 로직
private void validateOrderStatus() {
    if (this.orderStatus != OrderStatus.PENDING) {
        throw new IllegalStateException("주문 수락 전에만 취소할 수 있습니다.");
    }
}
```

**장점:**
- 완전한 캡슐화: 외부에서 절대 접근 불가하여 내부 구현 변경이 자유로움
- 검증 로직이나 계산 로직을 분리하여 public 메서드의 가독성 향상
- 변경 시 영향 범위가 해당 엔티티로 한정

**단점:**
- 테스트에서 직접 호출 불가하여 public 메서드를 통해 간접적으로만 테스트 가능
- 과도하게 사용하면 같은 도메인 내 엔티티 간 협력이 어려워질 수 있음

### 접근 제어자별 역할 요약

| 접근 제어자 | 역할 | 호출 범위 | 예시 |
|---|---|---|---|
| `public` | 핵심 비즈니스 로직 | Service, 외부 레이어 | `create()`, `cancel()` |
| 패키지 프라이빗 | 연관관계 편의 메서드 | 같은 패키지 내 엔티티 | `assignMember()`, `addOrderItem()` |
| `private` | 내부 검증/계산 로직 | 엔티티 자기 자신 | `validateOrderStatus()` |

### 관련 파일
- `src/main/java/com/study/shop/domain/order/entity/Order.java`
- `src/main/java/com/study/shop/domain/order/entity/OrderItem.java`

### 교훈
- **접근 제어자는 설계 의도를 표현하는 도구**: 단순히 컴파일 에러를 막기 위한 것이 아니라, 각 메서드의 역할과 호출 범위를 명확히 전달하는 수단
- **DDD에서 엔티티는 자신의 상태를 스스로 관리**: public 메서드로 비즈니스 행위를 노출하고, 내부 상태 변경은 패키지 프라이빗 또는 private으로 보호
- **패키지 구조가 접근 제어의 핵심**: `domain.order.entity` 패키지에 Order와 OrderItem을 함께 두어 패키지 프라이빗의 이점을 최대한 활용
- **기록 #007의 연장선**: Category에서 배운 캡슐화 원칙을 Order 도메인에도 일관되게 적용

---

## 기록 #010: Post.removeComment()에서 연관관계만 끊어지고 실제 삭제가 이루어지지 않는 문제

**발생일**: 2026-02-20

### 문제 상황
`Post.removeComment()` 메서드 호출 시 컬렉션에서 Comment를 제거하고 `comment.setPost(null)`로 연관관계를 끊지만, Comment 엔티티 자체는 DB에서 삭제되지 않음

#### 현재 코드
```java
public void removeComment(Comment comment) {
    this.comments.remove(comment);
    comment.setPost(null);  // 연관관계만 끊김, DB에서 삭제되지 않음
}
```

### 원인 분석
`comment.setPost(null)`은 Comment 엔티티의 외래키(post_id)를 null로 설정할 뿐, Comment 레코드 자체를 삭제하지 않음. 이로 인해:
1. **내가 작성한 댓글 조회 시 삭제된 댓글이 여전히 조회됨**: `CommentRepository.findActiveCommentByMemberId()`는 `deleted = false`인 댓글을 조회하는데, `removeComment()`는 `deleted` 플래그를 변경하지 않으므로 해당 댓글이 계속 조회됨
2. **고아 데이터 발생**: post_id가 null인 Comment가 DB에 남아 데이터 정합성 저하

### 검토한 대안

#### 1. orphanRemoval 활용
Post 엔티티의 `@OneToMany`에 이미 `orphanRemoval = true`가 설정되어 있으므로, `comments.remove(comment)` 호출 시 JPA가 자동으로 Comment를 DB에서 삭제함. 단, `comment.setPost(null)` 없이 컬렉션에서 제거만 하면 orphanRemoval이 동작함

```java
public void removeComment(Comment comment) {
    this.comments.remove(comment);
    // orphanRemoval = true이므로 컬렉션에서 제거되면 JPA가 자동 DELETE
}
```

#### 2. 소프트 삭제
Comment 엔티티의 `deleted` 플래그를 활용하여 논리적 삭제 수행. 데이터 보존이 필요한 경우에 적합

```java
public void removeComment(Comment comment) {
    comment.markAsDeleted();  // deleted = true로 변경
}
```

### 관련 파일
- `src/main/java/com/study/shop/domain/post/entity/Post.java`
- `src/main/java/com/study/shop/domain/comment/repository/CommentRepository.java`

### 교훈
- **연관관계 해제 ≠ 삭제**: `setPost(null)`은 FK를 null로 설정할 뿐 레코드를 삭제하지 않으므로, 삭제 의도라면 orphanRemoval 또는 명시적 삭제가 필요
- **orphanRemoval과 연관관계 편의 메서드의 조합 주의**: orphanRemoval이 설정된 상태에서 `setPost(null)`을 먼저 호출하면 JPA가 orphan 감지를 제대로 하지 못할 수 있음
- **소프트 삭제와 하드 삭제 전략 통일**: 프로젝트 내에서 삭제 전략을 일관되게 유지해야 조회 로직의 혼란을 방지할 수 있음

**상태:** 미해결 (소프트 삭제 방안 적용 고려중)

---

## 기록 #011: Order-OrderItem 양방향 연관관계의 순환 의존성 문제

**발생일**: 2026-02-23

### 배경
`OrderService`에서 주문 생성 로직을 구현하는 과정에서 Order와 OrderItem이 양방향 연관관계를 맺고 있어 생성 순서에 대한 순환 의존성이 발생함.

### 문제 상황
```
OrderItem 생성 → Order 참조 필요 (FK: order_id)
Order 생성 → OrderItem 참조 필요 (비즈니스 로직)
```

두 엔티티가 서로를 참조하는 구조로 인해 어느 것을 먼저 생성해야 할지 결정하지 못하는 전형적인 순환 의존성 문제였음.

### 검토한 방안

"먼저 생성하고 나중에 연관관계 설정" 3단계 패턴:

```java
// 1. OrderItem을 먼저 생성 (Order 없이)
List<OrderItem> orderItems = requestDto.getOrderItems().stream()
    .map(dto -> {
        Item item = itemRepository.findById(dto.getItemId());
        return OrderItem.createOrderItem(item, dto.getQuantity());
    })
    .collect(toList());

// 2. Order 생성
Order order = Order.createOrder(member, requestDto.getAddress());

// 3. 연관관계 나중에 설정
orderItems.forEach(order::addOrderItem);
```


**실제 구현 코드:**

```java
// OrderItem.java - Order 없이 생성하는 정적 팩토리
public static OrderItem create(Item item, Integer quantity) {
    if (item.getStock() < quantity) {
        throw new StockNotEnoughException(item.getId()); // 재고 검증 추가
    }
    item.removeStock(quantity);
    return OrderItem.builder()
            .item(item)
            .quantity(quantity)
            .price(item.getPrice()) // 주문 당시 가격 스냅샷 추가
            .build();
    // order 필드는 설정하지 않음
}

// 패키지 프라이빗 - Order.addOrderItem() 에서만 호출 가능
void assignOrder(Order order) {
    this.order = order;
}

// Order.java - 연관관계 진입점
public void addOrderItem(OrderItem orderItem) {
    this.orderItems.add(orderItem);
    orderItem.assignOrder(this); // 패키지 프라이빗 메서드 호출
}

// OrderService.java - 실제 서비스 레이어
List<OrderItem> orderItems = requestDto.getOrderItems().stream()
    .map(dto -> {
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new ItemNotFoundException(dto.getItemId()));
        return OrderItem.create(item, dto.getQuantity()); // 1단계: Order 없이 생성
    }).toList();

Order order = Order.create(member, requestDto.getAddress()); // 2단계: Order 생성
orderItems.forEach(order::addOrderItem); // 3단계: 연관관계 설정
```

### 대안 분석

#### 대안 1: 단방향 연관관계로 변경
```java
// OrderItem이 Order를 참조하지 않는 구조
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
@JoinColumn(name = "order_id") // Order가 FK 주인
private List<OrderItem> orderItems;
```

**장점:**
- 순환 의존성 문제 자체가 사라짐
- 코드 단순화

**단점:**
- `@OneToMany`에 `@JoinColumn`을 사용하면 INSERT 후 별도의 UPDATE SQL이 추가 발생하는 JPA 특성 문제 (성능 저하)
- OrderItem → Order 역탐색 불가 (별도 Repository 쿼리 필요)

#### 대안 2: 중간 단계 저장 (지연 초기화)
```java
// OrderItem 먼저 저장 후 Order에 추가
OrderItem savedItem = orderItemRepository.save(
    OrderItem.builder().item(item).quantity(qty).build()
);
order.addOrderItem(savedItem);
```

**장점:** 구현이 직관적이고 단순함

**단점:**
- 불완전한 상태(order_id = null)의 OrderItem이 DB에 저장될 위험
- 불필요한 UPDATE SQL 추가 발생
- 트랜잭션 내 중간 저장이 복잡성을 높임

#### 대안 3: 생성 메서드에 Order를 직접 전달 (원래 시도했던 방식)
```java
// 문제가 된 방식 - 순환 의존성
public static OrderItem create(Order order, Item item, Integer quantity) {
    // Order 생성 시 OrderItem 필요, OrderItem 생성 시 Order 필요 → 교착 상태
}
```

**결론:** 양쪽 모두 상대방을 먼저 요구하므로 근본적으로 해결 불가. 채택 방식(Order 없이 생성 → 나중에 연관관계 설정)이 이 제약을 우회하는 유일한 실용적 해결책임.

#### 대안 4: 현재 채택 방식 - "먼저 생성, 나중에 연관관계 설정"

**장점:**
- 직관적이고 가독성 높음
- JPA 영속성 전이(`cascade = ALL`)를 통해 Order 저장 시 OrderItem 자동 저장
- `orphanRemoval`과 함께 엔티티 생명주기 자동 관리
- 패키지 프라이빗 `assignOrder()`로 외부 레이어의 직접 조작 차단

**단점:**
- OrderItem 생성 시점과 연관관계 설정 시점이 분리되어 중간에 불완전한 상태(order = null)로 존재
- `assignOrder()`의 이점을 살리려면 Order와 OrderItem이 같은 패키지에 위치해야 함

### 실제 구현의 추가 개선 사항

제안된 해결책에서 실제 구현으로 오면서 다음이 추가됨:

1. **재고 검증 (`StockNotEnoughException`)**: `OrderItem.create()` 내에서 생성 시점에 즉시 재고 부족 여부를 검증하여 생성 후 롤백 없이 빠른 실패(fail-fast) 처리
2. **가격 스냅샷**: 주문 당시의 `item.getPrice()`를 `price` 필드로 캡처하여 이후 상품 가격 변경 시에도 주문 금액의 데이터 정합성 유지
3. **패키지 프라이빗 `assignOrder()`**: 기록 #009의 교훈 적용 - Service 레이어에서 직접 연관관계 조작 불가, 반드시 `order.addOrderItem()`을 통해서만 설정 가능

### 관련 파일
- `src/main/java/com/study/shop/domain/order/entity/Order.java`
- `src/main/java/com/study/shop/domain/order/entity/OrderItem.java`
- `src/main/java/com/study/shop/domain/order/service/OrderService.java`

### 교훈
- **"먼저 생성, 나중에 연관관계 설정" 패턴**: JPA 양방향 연관관계의 순환 의존성은 한쪽을 먼저 생성(빈 상태)하고, 편의 메서드(`addOrderItem`)로 나중에 연결하는 방식으로 해결
- **패키지 프라이빗과 순환 의존성 해결의 시너지**: 연관관계 설정 메서드를 패키지 프라이빗으로 관리하면 Service가 직접 조작할 수 없어 자연스럽게 올바른 생성 순서가 강제됨
- **팩토리 메서드에서 불변 데이터 캡처**: 가격 등 시간이 지나면 변할 수 있는 데이터는 생성 시점에 스냅샷으로 저장하여 데이터 정합성 유지 (e-커머스의 기본 원칙)
- **기록 #009와의 연계**: 접근 제어자 전략(public/패키지 프라이빗/private 분리)이 순환 의존성 해결과 자연스럽게 결합되어 더 나은 설계로 발전

**상태:** 해결됨

---

## [핵심] 기록 #012: N+1 문제 해결 과정 — findAllPosts 메서드 개선

**작성일**: 2026-03-14
**관련 작업**: `findAllPosts` 메서드 개선 (JpaRepository → QueryDSL + Paging + DTO Projection)

---

### 배경

모든 Post를 조회하는 `findAllPosts` 메서드를 단순 JpaRepository 방식에서, N+1 문제를 해결하고 페이징 및 댓글 수 집계 기능을 제공하는 방식으로 단계적으로 개선함.

---

### Inner Join vs Left Join 선택 기준

- **Left Join**: 연관 엔티티가 없는 경우에도 기준 엔티티 조회 가능 → 항상 안전
- **Inner Join**: 연관 엔티티가 반드시 존재함이 보장될 때 사용

**Post → Member 경우:**
- 일반적으로 게시글에는 작성자가 반드시 존재 → Inner Join 안전
- 단, 탈퇴 회원을 Hard Delete로 처리하되 Cascade 삭제 미적용 구조라면 `post.member`가 `null`이 될 수 있으므로 → Left Join 필요

---

### N:1 vs 1:N Fetch Join의 차이

**N:1 조회 (예: Post → Member)**
- Join 결과 row 수 = Post 수와 동일 → Cartesian Product 없음
- Fetch Join + Paging 함께 사용 안전

**1:N 조회 (예: Post → Comments, Collection Fetch Join)**
- Join 시 결과 row가 Comment 수만큼 증가 → Cartesian Product 발생
- **Join 결과 row가 자식 엔티티(Comment) 수 N배만큼 증가**
- JPA는 이 경우 **페이징을 메모리에서 처리**하여 OOM 위험 발생
  → Hibernate 경고: `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
- **따라서 Collection Fetch Join + Paging은 일반적으로 금지**

**Collection Fetch Join + Paging이 꼭 필요한 경우 대안:**
1. **2단계 조회**: 부모(Post)를 먼저 Paging 조회 후, 자식(Comment)을 `IN` 조건으로 별도 조회
2. **Batch Size**: `@BatchSize` 또는 `hibernate.default_batch_fetch_size` 설정으로 N+1을 IN 쿼리로 최적화
3. **DTO Projection**: 집계 함수(`count()`)로 필요한 값만 선택 조회 → 이 방법을 최종 채택

> 참고: 1:N Fetch Join이 필요한 단건 조회는 애초에 Paging이 필요한 상황이 거의 없다.

---

### 쿼리 설계 원칙

> **비즈니스 요구사항(필요한 데이터)을 먼저 파악하고, 그에 맞는 쿼리 전략을 선택하는 것이 가장 중요하다.**

| 상황 | 필요 데이터 | 페이징 | 적합한 전략 |
|---|---|---|---|
| Post 목록 조회 | Post, Member, 댓글 수 | 필요 | N:1 Join + DTO Projection + Paging |
| Post 단건 조회 | Post, Comments, Member | 불필요 | 1:N Fetch Join 또는 2단계 조회 |

---

### Count Query 분리의 중요성

→ **페이징(Page) 사용 시 content 쿼리와 count 쿼리를 분리해야, 집계에 불필요한 Join/orderBy를 제거하여 성능을 최적화할 수 있다.**

- content 쿼리와 count 쿼리의 `from`, `join`, `where` 절은 기본적으로 일치시켜야 하지만,
- **집계 목적상 불필요한 join이나 orderBy는 count 쿼리에서 생략 가능**

```java
Long total = queryFactory
    .select(post.count())
    .from(post)
    // .join(post.member, member)
    // ↑ member가 반드시 존재한다는 보장 하에 생략 가능
    //   보장이 없으면 left join 필요 → 구조 복잡도 증가
    .fetchOne();
```

---

### 단계별 코드 개선 이력

#### 1단계: JPA @Query (초기)

```java
@Query(value = "select p from Post p join fetch p.member",
       countQuery = "select count(p) from Post p")
Page<Post> findAllWithMember(Pageable pageable);
```

- N+1 해결 + 페이징 지원
- 동적 조건 추가 어려움, 댓글 수 집계 불가

#### 2단계: QueryDSL 전환

```java
@Override
public List<PostListDto> findAllPosts() {
    return queryFactory
        .selectFrom(post)
        .join(post.member, member)
        .orderBy(post.createdAt.desc())
        .fetch();
}
```

- 타입 안전성, 컴파일 타임 오류 감지, 동적 조건 조합 가능
- 페이징 및 댓글 수 집계 여전히 미지원

#### 3단계: QueryDSL + DTO Projection + Paging (최종)

```java
@Override
public Page<PostListDto> findAllPosts(Pageable pageable) {
    List<PostListDto> content = queryFactory
        .select(Projections.constructor(PostListDto.class,
            post.id, post.title, post.content, post.createdAt, comment.count()
        ))
        .from(post)
        .join(post.member, member)
        .leftJoin(post.comments, comment) // 댓글 없는 게시글도 포함 → leftJoin
        .groupBy(post.id)
        .orderBy(post.createdAt.desc())
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long total = queryFactory
        .select(post.count())
        .from(post)
        .fetchOne();

    return new PageImpl<>(content, pageable, total);
}
```

> **왜 이 코드는 Post → Comments(1:N) + Paging임에도 안전한가?**
>
> `Fetch Join`이 아닌 **일반 Join + `groupBy` + `count()` 구조**이기 때문.
> - Fetch Join: 연관 엔티티 객체 전체를 영속성 컨텍스트에 로딩 → row 뻥튀기 발생, JPA가 메모리 페이징 시도
> - 일반 Join + groupBy: SQL 집계 결과를 DTO로 받음 → row 뻥튀기 없음, DB 레벨 페이징 안전

---

### Fetch Join vs 일반 Join 정리

| 구분 | Fetch Join | 일반 Join |
|---|---|---|
| 목적 | 연관 엔티티 객체를 영속성 컨텍스트에 로딩 | 집계/필터 조건으로 연관 테이블 활용 |
| 반환 타입 | Entity | DTO (Projection) |
| 1:N + Paging | 위험 (메모리 페이징 경고, OOM 가능) | 안전 (groupBy + 집계 함수 활용 시) |

---

### 핵심 요약

1. **N:1 Fetch Join + Paging → 안전**
2. **1:N (Collection) Fetch Join + Paging → 위험, 대안 필요**
3. **일반 Join + groupBy + 집계 함수 → 1:N이라도 Paging 안전**
4. **Count Query는 content 쿼리와 분리하여 불필요한 Join 제거로 성능 최적화**
5. **비즈니스 요구사항 → 필요 데이터 파악 → 쿼리 전략 선택 순서로 설계**

**상태:** 해결됨

---

## [핵심] 기록 #013: 소프트 삭제(Soft Delete) 도입 — 구현 전략과 Cascade/orphanRemoval 충돌 관리

**작성일**: 2026-03-16
**관련 도메인**: Member, Order, Comment

---

### 소프트 삭제를 도입한 이유

하드 삭제(Hard Delete)는 DB에서 레코드를 완전히 제거하지만, 다음과 같은 문제가 발생할 수 있다.

| 문제 | 설명 |
|---|---|
| 데이터 복구 불가 | 실수로 삭제한 경우 복원 수단 없음 |
| 참조 무결성 파괴 | 연관 엔티티의 FK가 null/dangling 상태가 됨 |
| 감사 추적 불가 | 누가 언제 삭제했는지 이력 없음 |
| 통계/분석 데이터 손실 | 탈퇴 회원의 주문·게시글 집계 불가 |

소프트 삭제는 `deleted = true` 플래그만 설정하고 레코드는 보존하여 위 문제들을 해결한다.

---

### 도메인별 소프트 삭제 적용 현황

#### Member — `@SQLDelete` + `@SQLRestriction` (Hibernate 자동 처리)

```java
@SQLDelete(sql = "UPDATE member SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Member {
    @Column(nullable = false)
    private Boolean deleted = false;
}
```

- `@SQLDelete`: `repository.delete(member)` 호출 시 JPA가 `DELETE` 대신 `UPDATE` SQL을 실행
- `@SQLRestriction`: 이 엔티티를 포함하는 모든 JPA/JPQL 쿼리에 `WHERE deleted = false` 조건 자동 추가
- 서비스 코드 변경 없이 탈퇴 회원이 조회에서 자동으로 제외됨

#### Order — `@SQLDelete` + `@SQLRestriction` (동일 방식)

```java
@SQLDelete(sql = "UPDATE orders SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Order {
    @Column(nullable = false)
    private Boolean deleted = false;
}
```

- **미해결 TODO**: 주문 소프트 삭제 시 OrderItem의 재고 복구 로직 미구현 (`Order.java` 주석 참고)

#### Comment — 수동 소프트 삭제 (엔티티 메서드 + Repository 명시 필터링)

```java
// @SQLDelete/@SQLRestriction 없음 — 수동 처리
public class Comment {
    private boolean deleted = false;

    public void delete() {
        this.deleted = true;
    }
}
```

```java
// 일반 조회: 명시적으로 deleted = false 필터링
@Query("select c from Comment c where c.member.id = :memberId and c.deleted = false")
List<Comment> findActiveCommentByMemberId(Long memberId);

// 관리자 조회: 소프트 삭제 댓글 포함 전체 조회
@Query("select c from Comment c where c.post.id = :postId")
List<Comment> findAllByPostId(Long postId);
```

- `@SQLRestriction`이 없으므로 조회 시 `deleted = false` 조건을 **직접 명시**해야 함
- 빠뜨릴 경우 삭제된 댓글도 함께 조회되는 버그 위험이 있음

---

### `@SQLDelete` + Cascade/orphanRemoval 충돌 문제

#### 충돌 시나리오: Member 소프트 삭제 시 자식 처리

Member는 Post, Comment, Order, Item에 `cascade = ALL, orphanRemoval = true`가 적용되어 있다.

```java
// Member.java
@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> posts;

@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments;

@OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Order> orders;
```

`memberRepository.delete(member)` 호출 시 실제 실행 흐름:

1. JPA → `em.remove(member)` 호출
2. `cascade = ALL(REMOVE)` → 자식 엔티티들에도 `em.remove()` 전파
3. **Member**: `@SQLDelete` 가로챔 → `UPDATE member SET deleted = true` (소프트 삭제)
4. **Post**: `@SQLDelete` 없음 → `DELETE FROM post WHERE id = ?` **(하드 삭제)**
5. **Comment**: `@SQLDelete` 없음 → `DELETE FROM comment WHERE id = ?` **(하드 삭제)**
6. **Order**: `@SQLDelete` 있음 → `UPDATE orders SET deleted = true` (소프트 삭제)

**Member 소프트 삭제 시 Post와 Comment는 하드 삭제된다.** 설계 의도와 불일치할 수 있다.

#### 충돌 시나리오: orphanRemoval + Comment 수동 소프트 삭제

```java
// Post.java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments;
```

Post 삭제 또는 `post.getComments().remove(comment)` 호출 시:
- `orphanRemoval = true` → JPA가 Comment에 `em.remove()` 호출
- Comment에 `@SQLDelete` 없음 → **하드 DELETE** 실행
- `comment.delete()`로 소프트 삭제하려던 의도와 충돌

| 삭제 주체 | 자식 엔티티 | 결과 | 비고 |
|---|---|---|---|
| Member 소프트 삭제 | Post | 하드 삭제 | 설계에 따라 의도적일 수 있음 |
| Member 소프트 삭제 | Comment | 하드 삭제 | 동일 |
| Member 소프트 삭제 | Order | 소프트 삭제 | 일관성 있음 |
| Post 삭제 (orphanRemoval) | Comment | 하드 삭제 | Comment 수동 소프트 삭제 의도와 충돌 |

---

### Cascade/orphanRemoval 충돌 최소화 전략

#### 전략 1: 자식 엔티티에도 `@SQLDelete` 적용 (일관성 확보)

Post와 Comment에도 `@SQLDelete`를 추가하면 cascade REMOVE가 전파되더라도 하드 삭제 대신 소프트 삭제로 처리된다.

```java
@SQLDelete(sql = "UPDATE post SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class Post {
    private Boolean deleted = false;
}
```

**장점**: cascade 흐름을 유지하면서 전 도메인 소프트 삭제 일관성 확보, 서비스 코드 변경 최소화
**단점**: 모든 자식 엔티티에 `deleted` 컬럼 추가 필요, 관리 포인트 증가

#### 전략 2: orphanRemoval 제거 + 명시적 소프트 삭제 메서드 사용

```java
// cascade에서 REMOVE 제거, orphanRemoval = false
@OneToMany(mappedBy = "post", cascade = CascadeType.PERSIST, orphanRemoval = false)
private List<Comment> comments;

// 삭제 시 명시적으로 comment.delete() 호출
public void removeComment(Comment comment) {
    comment.delete(); // 소프트 삭제
}
```

**장점**: orphanRemoval로 인한 의도치 않은 하드 삭제 방지
**단점**: 자동 삭제 보장이 없어지므로 서비스 로직에서 명시적 삭제 처리 필요

#### 현재 프로젝트 상태

Member/Order는 `@SQLDelete` 방식, Comment는 수동 방식으로 **혼용** 중.
단기적으로는 동작하지만 삭제 전략 문서화 및 통일이 필요하다.

---

### `@SQLRestriction` 동작 방식과 관리자 우회 패턴

`@SQLRestriction("deleted = false")`는 해당 엔티티에 대한 모든 JPA 쿼리에 조건을 자동 추가한다.

```sql
-- @SQLRestriction 적용 시
SELECT * FROM member WHERE email = ?
→ 실제 실행: SELECT * FROM member WHERE email = ? AND deleted = false
```

**관리자 기능에서 우회가 필요한 경우:**
- 네이티브 쿼리(`nativeQuery = true`)를 사용하면 `@SQLRestriction` 우회 가능
- 또는 Comment처럼 `@SQLRestriction` 없이 Repository 쿼리에서 조건을 직접 제어

```java
// @SQLRestriction 없는 Comment: Repository에서 직접 구분
@Query("select c from Comment c where c.member.id = :memberId and c.deleted = false") // 일반 조회
@Query("select c from Comment c where c.post.id = :postId")                           // 관리자 조회
```

`@SQLRestriction` 방식은 실수를 방지하는 대신 관리자 기능 구현이 복잡하고,
수동 방식은 유연하지만 필터 누락 버그 위험이 있다.

---

### 남은 과제 (TODO)

- [ ] **Order 소프트 삭제 시 재고 복구 로직 추가** (`Order.java` 주석 참고)
- [ ] **Post 소프트 삭제 적용 여부 결정** (현재 cascade REMOVE → 하드 삭제)
- [ ] **Comment 소프트 삭제 전략 통일** (`@SQLDelete` 방식 전환 or 현행 유지)
- [ ] **삭제 전략 명문화**: 하드/소프트 삭제 기준을 프로젝트 차원에서 정의

---

### 핵심 요약

1. **`@SQLDelete`**: JPA의 `delete()` 호출을 가로채어 UPDATE로 변환 → 서비스 코드 변경 없이 소프트 삭제 적용
2. **`@SQLRestriction`**: 모든 JPA 쿼리에 자동 필터 추가 → 실수 방지, 단 관리자 우회 처리 필요
3. **cascade REMOVE + 소프트 삭제**: 자식 엔티티에 `@SQLDelete` 없으면 하드 삭제 발생 → 반드시 확인 필요
4. **orphanRemoval + 소프트 삭제**: 컬렉션에서 제거 시 orphanRemoval이 하드 삭제를 유발 → 충돌 주의
5. **전략 일관성**: 도메인마다 삭제 방식이 다르면 조회 로직 혼란 발생 → 프로젝트 전체에서 통일된 전략 권장

### 관련 파일

- `src/main/java/com/study/shop/domain/member/entity/Member.java`
- `src/main/java/com/study/shop/domain/order/entity/Order.java`
- `src/main/java/com/study/shop/domain/comment/entity/Comment.java`
- `src/main/java/com/study/shop/domain/comment/repository/CommentRepository.java`
- `src/main/java/com/study/shop/admin/dto/AdminCommentResponseDto.java`

**상태:** 부분 적용 (전략 통일 필요)

---

## 기록 #014: searchPosts 설계 오류 — GET + @RequestBody, count 쿼리 member join 누락

**발생일**: 2026-03-18
**관련 도메인**: Post

---

### 문제 상황 1: GET 요청에 @RequestBody 사용

```java
// 문제가 된 코드
@GetMapping
public ResponseEntity<ApiResponse<Page<PostListDto>>> searchPosts(
        @PageableDefault(...) Pageable pageable,
        @RequestBody(required = false) PostSearchConditionDto cond  // ← 비표준
) { ... }
```

**HTTP 스펙상 GET 요청에 body를 포함하는 것은 권장하지 않는다:**
- 일부 HTTP 프록시/서버가 GET body를 무시하거나 거부
- Swagger/OpenAPI가 GET + body를 렌더링하지 못함
- 브라우저, curl 등 기본 HTTP 클라이언트 동작과 불일치

검색 조건은 Query Parameter로 전달하는 것이 REST 설계 원칙에 부합한다.

---

### 문제 상황 2: searchPosts count 쿼리에 member join 누락

```java
// content 쿼리: member join 있음 → writerContains() 사용 가능
List<PostListDto> content = queryFactory
        .select(...)
        .from(post)
        .join(post.member, member)  // ← join 있음
        .where(writerContains(cond.getWriter()), ...)
        ...

// count 쿼리: member join 누락
Long total = queryFactory
        .select(post.count())
        .from(post)
        // .join(post.member, member) ← 없음!
        .where(writerContains(cond.getWriter()), ...)  // member.nickname 참조 → 오류
        ...
```

`writerContains()`는 `member.nickname.containsIgnoreCase(writer)`를 참조하는데, count 쿼리에 `join(post.member, member)`가 없어 writer 조건 사용 시 쿼리 오류 발생.

---

### 문제 상황 3: fetchOne() 언박싱 NPE 위험

```java
Long total = queryFactory.select(post.count()).from(post)...fetchOne();
return new PageImpl<>(content, pageable, total);  // Long → long 언박싱 시 NPE 가능
```

`fetchOne()`은 결과가 없으면 `null`을 반환하고, `PageImpl` 생성자는 `long`(primitive)을 받으므로 언박싱 과정에서 NPE가 발생할 수 있다.
실제로 count 쿼리에서 null이 나오는 경우는 거의 없지만, 컴파일러 관점에서는 null 가능성이 있다.

---

### 해결 방법

#### 1. getAllPosts + searchPosts 통합 (엔드포인트 통일)

조건 없는 전체 조회는 조건이 모두 비어 있는 `searchPosts`와 동일하므로 하나로 합침.

```java
// GET /api/posts?page=0&size=20&title=foo&writer=bar&from=2024-01-01T00:00:00
@GetMapping
public ResponseEntity<ApiResponse<Page<PostListDto>>> searchPosts(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @ModelAttribute PostSearchConditionDto cond
) {
    return ResponseEntity.ok(ApiResponse.success(postService.searchPosts(pageable, cond)));
}
```

- `@ModelAttribute`: Query Parameter를 DTO에 자동 바인딩 (HTTP 표준 준수)
- 조건 없이 호출하면 전체 조회처럼 동작

#### 2. LocalDateTime 파싱을 위한 @DateTimeFormat 추가

`@ModelAttribute`로 `LocalDateTime`을 바인딩하려면 포맷을 명시해야 한다.

```java
@Getter
@NoArgsConstructor
public class PostSearchConditionDto {
    private String title;
    private String writer;
    private String content;
    private Boolean hidden;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;
}
```

요청 예시: `?from=2024-01-01T00:00:00&to=2024-12-31T23:59:59`

#### 3. count 쿼리에 member join 추가

```java
long total = Optional.ofNullable(queryFactory
        .select(post.count())
        .from(post)
        .join(post.member, member)  // ← 추가
        .where(
                titleContains(cond.getTitle()),
                contentContains(cond.getContent()),
                writerContains(cond.getWriter()),  // member.nickname 참조 가능
                hiddenEq(false),
                createdAtAfter(cond.getFrom()),
                createdAtBefore(cond.getTo())
        )
        .fetchOne()).orElse(0L);
```

#### 4. fetchOne() NPE 방어

`Optional.ofNullable(...).orElse(0L)` 패턴으로 null 안전하게 처리.
`findAllPostsWithComments()`의 count 쿼리에도 동일하게 적용.

---

### @RequestBody vs @ModelAttribute vs @RequestParam 선택 기준

| 방식 | 적합한 경우 | 비고 |
|---|---|---|
| `@RequestBody` | POST/PUT/PATCH 요청, JSON body | GET에 사용 금지 |
| `@ModelAttribute` | GET 요청, 다수의 파라미터를 DTO로 묶을 때 | Query Parameter를 DTO에 바인딩 |
| `@RequestParam` | GET 요청, 파라미터가 1~2개로 단순할 때 | 각 필드를 개별 파라미터로 선언 |

검색 조건처럼 파라미터가 많은 경우 `@ModelAttribute`로 DTO에 묶는 것이 가독성과 유지보수성이 좋다.

---

### 관련 파일

- `src/main/java/com/study/shop/domain/post/controller/PostController.java`
- `src/main/java/com/study/shop/domain/post/repository/PostRepositoryImpl.java`
- `src/main/java/com/study/shop/domain/post/dto/PostSearchConditionDto.java`

### 교훈

- **GET 요청의 조건은 Query Parameter**: REST 원칙상 GET에 body를 포함하면 안 되며, 검색 조건은 `@ModelAttribute`로 DTO에 바인딩
- **동적 조건 쿼리에서 content/count 일관성 유지**: 조건에 사용된 테이블(member 등)은 content 쿼리와 count 쿼리 모두에 join이 필요
- **fetchOne() 반환값은 항상 null 가능**: count 집계라도 `Optional.ofNullable(...).orElse(0L)`로 방어
- **기록 #012 연장**: count 쿼리 분리 전략 적용 시 content 쿼리의 join 조건과 동기화가 필수

**상태:** 해결됨

---

## 기록 #015: Page vs Slice 페이징 전략 선택 — 게시글(Offset)과 상품(Cursor) 분리 적용

**작성일**: 2026-03-19
**관련 도메인**: Post, Item

---

### 배경

Post 비즈니스 로직에 페이징을 도입하는 과정에서 Spring Data의 `Page`와 `Slice` 중 어느 것을 선택할지 검토했다.
도메인 특성이 서로 달라 Post와 Item에 각각 다른 전략을 적용하기로 결정했다.

---

### Page vs Slice 핵심 차이

| 구분 | Page | Slice |
|---|---|---|
| count 쿼리 | 실행함 (전체 수 파악) | 실행하지 않음 |
| 제공 정보 | 전체 데이터 수, 총 페이지 수 | `hasNext()` 여부만 |
| 적합한 UI | 페이지 번호 네비게이션 | 무한스크롤, 더보기 |
| 대용량 성능 | offset이 커질수록 저하 | Cursor 방식과 결합 시 일정하게 유지 |

---

### 게시글 — Page (Offset 기반)

게시판은 전통적인 페이지 번호 UI를 제공해야 하므로 전체 게시글 수와 총 페이지 수가 필요하다.
데이터 쿼리와 count 쿼리를 분리하여 `PageImpl`로 포장해 반환한다.

**count 쿼리를 분리하는 이유:**
`join fetch`나 `groupBy`가 포함된 쿼리를 그대로 count에 사용하면 Hibernate가 변환에 실패하거나
잘못된 결과를 반환하기 때문이다. (기록 #012, #014에서도 동일한 원칙 적용)

**Offset 기반 페이징의 한계:**
- `offset`은 `page * size`번째 데이터부터 가져오는 방식으로, offset 값이 커질수록 앞의 데이터를 전부 읽고 버리는 구조
- 데이터가 많아질수록 성능이 저하되는 구조적 한계가 있음
- 게시판처럼 총 데이터 수가 반드시 필요한 경우에는 이 한계를 감수하고 Page를 선택

---

### 상품 — Slice (Cursor 기반)

C2C 마켓플레이스 특성상 상품 목록은 무한스크롤이 자연스럽고, 총 상품 수가 필요하지 않다.

**Slice를 선택한 이유:**
- count 쿼리를 실행하지 않으므로 Page보다 빠름
- Cursor 방식과 결합하면 데이터가 많아져도 성능이 일정하게 유지됨

**Cursor 방식 동작:**
마지막으로 조회한 데이터의 기준값을 다음 요청에 넘겨 그 이후 데이터만 가져오는 구조.
`hasNext()`만 제공하므로 "다음 페이지가 있는가"만 알 수 있고, 총 페이지 수나 전체 데이터 수는 알 수 없다.

**복합 커서 (createdAt + id) 를 사용하는 이유:**
`createdAt`만 단독으로 사용하면 같은 시각에 등록된 데이터가 있을 때 중복이나 누락이 발생할 수 있다.
`id`를 함께 복합 커서로 사용해 정렬 안정성을 확보한다.

---

### 전략 선택 기준 요약

| 상황 | 권장 전략 |
|---|---|
| 페이지 번호 UI, 전체 데이터 수 필요 | Page (Offset) |
| 무한스크롤 / 더보기, 총 개수 불필요 | Slice (Cursor) |
| 대용량 실시간 피드, Offset 성능 우려 | Slice (Cursor) |
| 관리자 전체 탐색, 폭넓은 데이터 접근 | Page (편의성) |

---

### 대용량 대응 추가 최적화 전략

게시글 수가 매우 많아지면 페이징 전략 외에 Redis 캐싱과 DB 인덱싱을 함께 고려할 수 있다.

#### Redis 캐싱 (최신 글 목록)

게시판 첫 1~2페이지는 거의 모든 사용자가 반복 조회하는 **read-heavy** 패턴이므로 캐싱 효과가 크다.
특히 `count(*)` 쿼리는 전체 테이블을 스캔하는 비용이 크기 때문에 캐싱 시 효과가 두드러진다.

**주의사항:**
- 글 등록/수정/삭제 시 **캐시 무효화(eviction)** 로직이 반드시 필요 — 누락 시 오래된 데이터 노출
- 검색 조건 조합이 많은 `searchPosts` 전체를 캐싱하면 캐시 키 설계가 복잡해짐 → **조건 없는 기본 목록(첫 페이지)** 위주로 캐싱하는 것이 현실적
- Offset 기반의 구조적 성능 한계(뒷 페이지로 갈수록 느려지는 것)는 캐싱으로 해결되지 않음 — 앞 페이지 캐싱 전략임을 전제로 해야 함

#### DB 인덱싱

캐싱과 독립적으로 인덱스를 설정해두면 캐시 미스 시나 캐시되지 않은 페이지 조회 시 성능을 보완할 수 있다.

| 대상 | 인덱스 종류 | 효과 | 비고 |
|---|---|---|---|
| Post.createdAt | 단일 인덱스 | 중간 | `ORDER BY createdAt DESC` 정렬 비용 절감 |
| Item.(createdAt, id) | 복합 인덱스 | 높음 | Cursor 방식의 WHERE 조건에 필수 |
| Post.title, Post.content | B-Tree 인덱스 | 낮음 | `LIKE '%keyword%'` 양방향 와일드카드는 인덱스 미사용 |
| Post.hidden | 단일 인덱스 | 낮음 | 선택도(selectivity)가 낮아 효과 미미 |

**title/content 검색 성능 개선이 필요한 경우:**
B-Tree 인덱스로는 한계가 있으므로 MySQL `FULLTEXT INDEX` 또는 Elasticsearch 도입이 근본적인 해결책이다.

**Item Cursor 페이징에서 복합 인덱스가 중요한 이유:**
```sql
WHERE (created_at < ?) OR (created_at = ? AND id < ?)
```
위와 같은 조건에서 `(createdAt, id)` 복합 인덱스가 있어야 인덱스 레인지 스캔이 동작한다.
단일 `createdAt` 인덱스만 있으면 id 조건까지 커버하지 못해 효율이 떨어진다.

#### 최적화 수단별 효과 비교

| 최적화 수단 | 효과 | 비고 |
|---|---|---|
| Redis 캐싱 (앞 페이지) | 높음 | 무효화 전략 설계 필수 |
| Post.createdAt 인덱스 | 중간 | 정렬/범위 조회 최적화 |
| Item.(createdAt, id) 복합 인덱스 | 높음 | Cursor 방식에서 필수 |
| Post.title/content 인덱스 | 낮음 | LIKE 양방향 와일드카드는 효과 없음 |

Redis 캐싱과 DB 인덱싱은 서로 보완적이다. 캐시 히트 시 DB까지 도달하지 않으므로 인덱스의 중요도가 낮아지지만, 캐시 미스나 캐시되지 않은 페이지 조회 시 인덱스가 성능을 담당한다.

---

### 관련 파일

- `src/main/java/com/study/shop/domain/post/repository/PostRepositoryImpl.java`
- `src/main/java/com/study/shop/domain/Item/repository/ItemRepositoryImpl.java`

### 교훈

- **UI 요구사항이 페이징 전략을 결정한다**: 페이지 번호가 필요하면 Page, 무한스크롤이면 Slice — 기술 선택 전에 UX 요구사항을 먼저 파악
- **복합 커서로 안정성 확보**: Cursor 기반 페이징에서 단일 필드(createdAt)만 사용하면 동일 값 데이터에서 중복/누락 발생 → id를 함께 사용
- **count 쿼리 분리는 Page 사용 시 항상 고려**: groupBy, fetch join이 포함된 쿼리는 count에서 오류가 발생하므로 분리 필수 (기록 #012 참고)
- **도메인별 적합한 전략을 선택**: 하나의 전략을 모든 도메인에 일괄 적용하기보다, 도메인의 특성과 트래픽 패턴에 맞는 전략을 개별 선택
- **캐싱과 인덱싱은 보완 관계**: 캐시 히트 시 DB를 건드리지 않으므로 인덱스의 역할이 줄어들지만, 캐시 미스 대비 인덱스는 여전히 필요
- **LIKE 양방향 와일드카드는 인덱스 우회**: title/content 검색 성능이 중요하다면 FULLTEXT INDEX 또는 Elasticsearch를 고려

## 기록 #016: Lombok @Builder 사용 시 필드 기본값 무시 문제

### 문제 상황
엔티티 필드에 `= false`, `= 0`, `= new ArrayList<>()` 등 기본값을 선언해도, `@Builder`를 통해 객체를 생성할 경우 해당 값이 적용되지 않고 `null`이 할당된다.

```java
// 의도: hidden = false 이어야 하지만 실제로는 null
private Boolean hidden = false;
```

### 원인
Lombok `@Builder`는 내부적으로 별도의 빌더 클래스를 생성하는데, 이 빌더 클래스의 필드 초기값은 Java 기본값(`null`, `0`, `false`)으로 설정된다. 따라서 원본 클래스에 선언한 초기값은 빌더 경로에서 무시된다.

### 해결 방법
기본값이 필요한 필드에 `@Builder.Default` 추가

```java
@Builder.Default
private Boolean hidden = false;
```

### 영향 받는 엔티티 (수정)
| 엔티티 | 필드 |
|---|---|
| `Comment` | `boolean deleted = false` |
| `Item` | `List<CategoryItem> categoryItems = new ArrayList<>()` |
| `Category` | `List<CategoryItem> categoryItems`, `List<Category> child` |

## 기록 #017: `Page<T>` Redis 캐싱 시 직렬화 문제

### 배경
게시글 목록 조회(`GET /api/posts`) 결과인 `Page<PostListDto>`를 `@Cacheable`로 Redis에 캐싱하여 DB 부하를 줄이려 했다.
조회수 Write-behind 패턴은 `StringRedisTemplate`으로 단순 문자열을 다뤄서 문제가 없었지만, 복잡한 객체 그래프를 캐싱하려니 연속적인 직렬화 문제가 발생했다.

---

### 발생한 문제

**문제 1 — PageImpl 역직렬화 실패**

Redis에 저장은 됐지만 두 번째 요청에서 꺼낼 때 실패했다.
`PageImpl`은 Jackson 역직렬화에 필요한 기본 생성자나 `@JsonCreator`가 없어서 JSON → 객체 변환이 불가능하다.

```
Cannot construct instance of `org.springframework.data.domain.PageImpl`
(no Creators, like default constructor, exist):
cannot deserialize from Object value
```

**문제 2 — LocalDateTime 직렬화 실패**

`PostListDto`의 `createTime` 필드가 `LocalDateTime` 타입인데, Jackson 기본 설정은 Java 8 날짜/시간 타입을 지원하지 않는다.

```
Java 8 date/time type `java.time.LocalDateTime` not supported by default:
add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
```

---

### 시도한 해결 과정

**시도 1 — CachePage 커스텀 DTO 도입 (문제 1 대응)**

`PageImpl` 대신 직렬화 가능한 커스텀 클래스를 만들어 우회했다.

```java
public class CachePage<T> implements Serializable {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    public static <T> CachePage<T> from(Page<T> page) {
        return new CachePage<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
```

서비스/컨트롤러 반환 타입을 `Page<PostListDto>` → `CachePage<PostListDto>`로 전부 변경했다.

**시도 2 — JavaTimeModule 등록 (문제 2 대응)**

`CacheConfig`의 `ObjectMapper`에 `JavaTimeModule`을 등록하고, `DefaultTyping`까지 설정해야 제네릭 타입 정보가 보존됐다.

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
objectMapper.activateDefaultTyping(
    objectMapper.getPolymorphicTypeValidator(),
    ObjectMapper.DefaultTyping.NON_FINAL
);
GenericJackson2JsonRedisSerializer serializer =
    new GenericJackson2JsonRedisSerializer(objectMapper);
```

---

### 문제의 본질

우회책이 쌓이는 구조가 됐다.

```
PageImpl 직렬화 안 됨      →  CachePage 도입          →  컨트롤러/서비스 반환 타입 전부 변경
LocalDateTime 직렬화 안 됨  →  ObjectMapper 커스텀     →  CacheConfig 복잡도 증가
제네릭 타입 정보 소실        →  DefaultTyping 설정      →  보안 고려사항 추가 발생
```

각 문제는 해결 가능하지만, 해결책이 또 다른 복잡도를 낳는 구조였다.
원래 목적인 "DB 부하 감소"를 위해 투자하는 엔지니어링 비용이 기대 효과를 초과한다고 판단했다.

---

### 최종 결정 — 목록 캐싱 제거

`@Cacheable`, `@CacheEvict`, `CachePage`, `ObjectMapper` 커스텀 전부 제거하고 단순 DB 조회로 롤백했다.

```java
@Transactional(readOnly = true)
public Page<PostListDto> searchPosts(PostSearchConditionDto condition, Pageable pageable) {
    return postRepository.searchPosts(condition, pageable);
}
```

Write-behind 조회수 캐싱(`StringRedisTemplate` + `@Scheduled`)은 단순 문자열만 다루므로 그대로 유지했다.

---

### 교훈

- **캐싱 도입 전 직렬화 가능성을 먼저 검토해야 한다**: 단순 `String` / `Long` 값은 캐싱이 쉽지만, Spring 내부 클래스(`PageImpl`)나 Java 8 날짜 타입(`LocalDateTime`)이 포함된 복잡한 객체 그래프는 예상보다 설정 비용이 크다.
- **과설계를 인식하고 롤백하는 것도 엔지니어링 판단이다**: "동작은 하지만 복잡도가 과도하다"고 판단해서 단순한 방향으로 되돌리는 결정 자체가 중요한 기술적 판단이다.
- **조회수 캐싱과 목록 캐싱은 난이도가 완전히 다르다**: 조회수는 `Redis INCR` 단일 정수 연산으로 직렬화가 불필요하지만, 목록은 복잡한 객체 그래프로 직렬화 설정, 캐시 무효화 전략, 페이징 정보 처리가 모두 필요하다.

**상태:** 설계 완료

---

## 기록 #018: `@ModelAttribute` 바인딩 시 DTO 필드 값이 null로 들어오는 문제

**발생일**: 2026-04-03

### 문제 상황

MockMvc로 GET 요청 테스트 중, 쿼리 파라미터로 전달한 값이 DTO에 바인딩되지 않는 문제가 발생했다.

```java
mockMvc.perform(get("/api/posts")
        .param("title", "Spring"));
```

컨트롤러:

```java
@GetMapping
public ResponseEntity<ApiResponse<Page<PostListDto>>> searchPosts(
        @ModelAttribute PostSearchConditionDto cond,
        Pageable pageable
) {
    log.info("condition: {}", cond);
}
```

로그 결과:

```
PostSearchConditionDto(title=null, writer=null, ...)
```

파라미터가 분명히 전달됐음에도 DTO 필드에 `null`이 할당됐다.

### 원인

`@ModelAttribute`는 내부적으로 다음 순서로 동작한다.

1. **기본 생성자**로 객체 생성
2. **setter**를 통해 요청 파라미터 값 주입

`PostSearchConditionDto`에 `@Getter`만 선언되어 있고 `@Setter`가 누락된 상태였기 때문에, Spring이 객체를 생성한 뒤 값을 주입하지 못하고 `null`을 그대로 유지했다.

### 해결 방법

DTO에 `@Setter`와 `@NoArgsConstructor`를 추가했다.

```java
@Getter
@Setter
@NoArgsConstructor
public class PostSearchConditionDto {
    private String title;
    private String writer;
    // ...
}
```

### 관련 파일

- `src/main/java/com/study/shop/domain/post/dto/PostSearchConditionDto.java`

### 교훈

- **`@ModelAttribute`는 setter 기반 바인딩이다**: 기본 생성자와 setter가 모두 있어야 하며, 요청 파라미터명이 필드명과 일치해야 한다. 셋 중 하나라도 빠지면 조용히 `null`로 남는다.
- **Lombok 사용 시 `@Getter`만 쓰는 습관이 실수로 이어진다**: 응답 DTO는 불변으로 `@Getter`만 써도 충분하지만, `@ModelAttribute`로 바인딩되는 요청 DTO는 반드시 `@Setter`가 필요하다.
- **테스트 코드에서도 실제 바인딩이 그대로 동작한다**: MockMvc의 `.param()`도 실제 HTTP 요청과 동일하게 `@ModelAttribute` 바인딩을 거치므로, DTO 구조 검증에 유용하다.


## 기록 #019: `Slice<T>` 직렬화 시 `hasNext` 필드가 JSON에 포함되지 않는 문제

**발생일**: 2026-04-06

### 문제 상황

`GET /api/items` 통합 테스트에서 `$.data.hasNext` 값을 검증하려 했으나, 실제 응답 JSON에 해당 필드가 존재하지 않아 테스트가 실패했다.

```java
mockMvc.perform(get("/api/items"))
        .andExpect(jsonPath("$.data.hasNext").value(false));  // ← 필드 없음 → 실패
```

컨트롤러 반환 타입:

```java
public ResponseEntity<ApiResponse<Slice<ItemListDto>>> searchItems(...) {
    return ResponseEntity.ok(ApiResponse.success(itemService.searchItems(...)));
}
```

### 원인

Spring Data의 `SliceImpl`은 `hasNext()` 메서드를 가지고 있지만, Jackson은 기본적으로 `get*()` 또는 `is*()` 접두사를 가진 메서드만 JavaBean getter로 인식하여 JSON 필드로 직렬화한다. `has*()` 접두사는 인식하지 않기 때문에 `hasNext()`는 직렬화 대상에서 제외된다.

실제 `SliceImpl` 직렬화 결과에 포함되는 필드:

```
content, pageable, size, number, numberOfElements, first, last, empty, sort
```

`last` 필드(`isLast()` → `is*` 규칙)는 직렬화되지만, `hasNext` 필드는 포함되지 않는다.

참고로 `Page`를 반환하면 Spring MVC가 `PageImpl`을 `PageModule`을 통해 별도 처리하여 더 풍부한 페이징 정보가 직렬화되므로, `Slice`임에도 `Page`처럼 보이는 현상이 발생할 수 있다.

### 임시 해결 방법

`hasNext` 대신 `last` 필드(값이 반대)로 검증하도록 테스트를 수정했다.

```java
// hasNext=false ↔ last=true
.andExpect(jsonPath("$.data.last").value(true));
```

### 근본 해결 방법 (예정)

`Slice<T>`를 커스텀 DTO로 래핑하여 `hasNext`를 명시적 필드로 노출한다.

```java
public class SliceResponse<T> {
    private final List<T> content;
    private final boolean hasNext;  // 명시적 필드 → isHasNext()로 직렬화
    // ...
    public SliceResponse(Slice<T> slice) {
        this.content = slice.getContent();
        this.hasNext = slice.hasNext();
    }
}
```

컨트롤러 반환 타입을 `Slice<ItemListDto>` → `SliceResponse<ItemListDto>`로 변경하면 응답 구조가 명시적이고 예측 가능해진다.

### 관련 파일

- `src/main/java/com/study/shop/domain/Item/controller/ItemController.java`
- `src/main/java/com/study/shop/domain/Item/repository/ItemRepositoryImpl.java` 
- `src/test/java/com/study/shop/domain/Item/controller/ItemControllerTest.java`

### 교훈

- **`has*()` 메서드는 Jackson이 자동 직렬화하지 않는다**: JavaBean 규약상 getter는 `get*`(일반) 또는 `is*`(boolean)만 해당된다. `has*`로 시작하는 메서드는 규약 밖이다.
- **Spring Data `Slice`는 API 응답 타입으로 그대로 반환하면 안 된다**: 직렬화 동작이 불투명하고 `hasNext` 같은 핵심 정보가 누락된다. 전용 응답 DTO로 변환하여 계약을 명확히 해야 한다.
- **`Page`와 `Slice`의 직렬화 동작이 다르다**: `Page`는 Spring MVC의 `PageModule`로 별도 처리되어 더 많은 정보가 직렬화되지만, `Slice`는 별도 모듈이 없어 기본 Jackson 직렬화에 의존한다.

**상태:** 임시 조치 완료 (`last` 필드로 우회), 커스텀 DTO 도입 예정

---

## 기록 #020: C2C 구조 전환 — Order:OrderItem 1:N → 1:1 리팩토링 시 발생한 버그들

**발생일**: 2026-04-07

### 배경

기존에는 하나의 `Order`에 여러 판매자의 `OrderItem`을 담는 1:N 구조로 설계되어 있었다. 그러나 C2C 중고거래 플랫폼에서 서로 다른 판매자의 상품이 한 주문에 묶이면 거래 상태 머신(수락/배송/완료), 판매자 권한 검증, 재고 복원 등 비즈니스 로직이 지나치게 복잡해진다. 이를 해결하기 위해 **Order:OrderItem = 1:1** 구조로 변경했다.

### 발생한 버그 목록

#### 버그 1. `Order.create()` 내 `calculateTotalPrice()` 호출 시 NPE

```java
// 변경 전 (버그)
public static Order create(Member member, String address) {
    Order order = Order.builder()...build();
    order.assignMember(member);
    order.calculateTotalPrice();  // orderItem이 null인 상태에서 호출 → NPE
    return order;
}
```

`Order.create()`는 `OrderItem` 없이 호출되고, `addOrderItem()`은 서비스 레이어에서 별도로 호출된다. 따라서 `create()` 시점에는 `orderItem`이 항상 `null`이다.

**해결**: `calculateTotalPrice()` 호출을 `addOrderItem()` 내부로 이동시켜, `orderItem`이 할당된 직후에 계산하도록 변경했다.

```java
public void addOrderItem(OrderItem orderItem) {
    this.orderItem = orderItem;
    orderItem.assignOrder(this);
    calculateTotalPrice();  // orderItem 할당 직후 계산
}
```

#### 버그 2. `OrderDetailDto` / `OrderResponseDto`에서 1:N 잔재 코드 → 컴파일 에러

```java
// 변경 전 (버그) — 1:N 시절 코드가 그대로 남아 있음
List<OrderItemResponseDto> orderItemDtos = order.getOrderItems()  // 존재하지 않는 메서드
        .stream().map(OrderItemResponseDto::from).collect(Collectors.toList());
```

`Order` 엔티티는 이미 `getOrderItem()`(단수)만 가지고 있어 컴파일 에러가 발생한다.

**해결**: `List.of()`로 단일 `OrderItem`을 감싸도록 변경했다.

```java
.orderItemDtoList(List.of(OrderItemResponseDto.from(order.getOrderItem())))
```

응답 DTO의 `orderItemDtoList` 필드는 항상 크기 1인 리스트를 반환하지만, API 계약의 일관성과 향후 구조 변경 가능성을 고려해 List 타입을 유지했다.

```
DELETE /api/order/{id}         → 구매자 취소 (cancelOrder)
DELETE /api/order/{id}/reject  → 판매자 거절 (rejectOrder)
```

### 관련 파일

- `src/main/java/com/study/shop/domain/order/entity/Order.java`
- `src/main/java/com/study/shop/domain/order/dto/OrderDetailDto.java`
- `src/main/java/com/study/shop/domain/order/dto/OrderResponseDto.java`
- `src/main/java/com/study/shop/domain/order/controller/OrderController.java`
- `src/test/java/com/study/shop/domain/order/controller/OrderControllerTest.java`

### 교훈

- **도메인 구조 변경 시 연관 DTO와 컨트롤러를 함께 점검해야 한다**: 엔티티를 1:N → 1:1로 바꿨을 때 DTO의 `getOrderItems()` 잔재가 컴파일 에러를 일으켰다. 구조 변경은 해당 도메인 레이어 전체를 함께 검토해야 한다.
- **생성 메서드에서 아직 할당되지 않은 연관 객체를 참조하면 NPE가 발생한다**: `create()` 내에서 `null`인 `orderItem`을 참조했다. 연관 객체가 필요한 계산은 해당 객체가 실제로 할당되는 시점(`addOrderItem`)으로 이동하는 것이 안전하다.
- **테스트에서 한 회원이 여러 역할을 겸하면 권한 검증이 무력화된다**: C2C처럼 역할이 명확히 분리된 도메인에서는 테스트 픽스처도 역할별로 분리해야 실제 비즈니스 로직을 검증할 수 있다.

**상태:** 수정 완료

---

## 기록 #021: 낙관적 락 + @Retryable 고부하 환경 이슈 — StockNotEnoughException 재시도 진입, MySQL 데드락, 비관적 락 비교

**발생일**: 2026-04-14

### 배경

재고 차감 동시성 제어를 위해 `@Version` 기반 낙관적 락과 `@Retryable`을 도입했다. 부하 테스트(k6, 50 VUs, 10s) 중 두 가지 문제가 발생했고, 비관적 락과의 성능 비교 실험도 함께 진행했다.

---

### 버그 1. `StockNotEnoughException`이 `@Retryable` 내부로 진입

#### 증상

```
ExhaustedRetryException: Cannot locate recovery method
  Caused by: StockNotEnoughException: 재고가 부족합니다.
```

`@Retryable(retryFor = ObjectOptimisticLockingFailureException.class)`로 설정했음에도 `StockNotEnoughException` 발생 시 `@Recover` 탐색이 시작되고, 매칭되는 `@Recover`가 없으면 `ExhaustedRetryException`으로 감싸져 500이 반환됐다.

#### 원인

spring-retry는 `retryFor`에 없는 예외도 `@Recover` 탐색을 시도한다. 재시도 대상이 아니더라도 `@Recover`가 없으면 `ExhaustedRetryException`을 던지는 것이 spring-retry의 동작 방식이다.

#### 해결 1 — `noRetryFor`로 명시적 제외

```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class},
    noRetryFor = {StockNotEnoughException.class, ItemNotFoundException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 100)
)
```

#### 해결 2 — `@Recover`를 `RuntimeException`으로 범용화

```java
@Recover
public OrderDetailDto recoverCreateOrder(RuntimeException e, ...) {
    if (e instanceof ObjectOptimisticLockingFailureException
            || e instanceof CannotAcquireLockException) {
        throw new OptimisticLockingFailureException("요청 충돌이 발생하였습니다.");
    }
    throw e; // StockNotEnoughException 등은 그대로 re-throw
}
```

---

### 버그 2. MySQL InnoDB 데드락 발생

#### 증상

```
CannotAcquireLockException: Deadlock found when trying to get lock
  update item set ... version=? where id=? and version=?
```

`@Version` 낙관적 락을 적용했음에도 50명 동시 요청 시 MySQL에서 데드락이 발생했다.

#### 원인

낙관적 락은 **커밋 시점**에 version 불일치를 감지하는 방식이다. 커밋 이전에 InnoDB 행 잠금(row lock) 경합이 먼저 발생하면 DB 레벨 데드락을 막지 못한다. H2는 InnoDB의 갭 락·넥스트 키 락 같은 복잡한 잠금 메커니즘이 없어 동일 조건에서 데드락이 발생하지 않았다.

#### 해결 — `CannotAcquireLockException`도 재시도 대상에 추가

```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class,
                CannotAcquireLockException.class},
    noRetryFor = {StockNotEnoughException.class, ItemNotFoundException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 100)
)
```

`GlobalExceptionHandler`에도 함께 추가:

```java
@ExceptionHandler({OptimisticLockingFailureException.class,
                   CannotAcquireLockException.class})
public ResponseEntity handleLockException(Exception e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(e.getMessage()));
}
```

---

### 비교 실험 — 낙관적 락 vs 비관적 락 (k6, 50 VUs, 10s)

MySQL 데드락 문제를 확인한 후 비관적 락(`SELECT FOR UPDATE`)을 동일 조건으로 테스트해 트레이드오프를 측정했다.

#### 비관적 락 적용

```java
// ItemRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Item i WHERE i.id = :id")
Optional<Item> findByIdWithLock(@Param("id") Long id);

// OrderService — findById → findByIdWithLock 교체
Item item = itemRepository.findByIdWithLock(id)
        .orElseThrow(() -> new ItemNotFoundException(id));
```

#### 결과 비교

| 항목 | 락 없음 | 낙관적 락 (H2) | 낙관적 락 (MySQL) | 비관적 락 |
|---|---|---|---|---|
| 성공률 (201) | 21% | 93% | 63% | 56%* |
| 충돌/오류 (409) | 500 데드락 | 6% | 36% | 0% |
| 평균 응답시간 | 빠름 | 2~3ms | 2~3ms | 70ms |
| 데이터 정합성 | 미보장 | 보장 | 보장 | 보장 |
| 데드락 발생 | 있음 | 없음 | 있음 | 없음 |

\* 재고 소진으로 인한 400 포함

### 관련 파일

- `src/main/java/com/study/shop/domain/order/service/OrderService.java`
- `src/main/java/com/study/shop/domain/Item/repository/ItemRepository.java`
- `src/main/java/com/study/shop/global/exception/GlobalExceptionHandler.java`

### 교훈

- **`@Retryable`의 `retryFor`는 재시도 예외만 거른다. `@Recover` 탐색은 별개다**: `retryFor`에 없는 예외도 `@Recover`가 없으면 `ExhaustedRetryException`으로 감싸진다. 재시도 불필요한 예외는 `noRetryFor`로 명시적으로 제외해야 한다.
- **낙관적 락은 MySQL InnoDB 행 잠금 데드락을 막지 못한다**: version 불일치 감지는 커밋 시점이고, 데드락은 그 이전 행 잠금 단계에서 발생한다. 재고처럼 동일 행 경합이 심한 도메인에서는 `CannotAcquireLockException`도 재시도 대상에 포함해야 한다.
- **테스트 환경(H2)과 운영 환경(MySQL)의 동시성 동작이 다르다**: H2에서 93% 성공하던 낙관적 락이 MySQL에서는 63%로 낮아졌다. 부하 테스트는 반드시 실제 운영 DB와 동일한 환경에서 진행해야 신뢰할 수 있다.
- **락 전략은 도메인 특성에 따라 선택해야 한다**: 낙관적 락은 충돌이 드문 환경에 적합하고, 비관적 락은 경합이 심한 도메인에서 정합성을 보장하지만 응답시간이 약 20~30배(2ms → 70ms) 느려진다.

**상태:** 해결 완료

---

## 기록 #022: `@QueryProjection` 잘못된 위치 사용 — Q 클래스 전체 컴파일 오류

**발생일**: 2026-04-25
**관련 도메인**: Admin Member 검색

---

### 배경

관리자 회원 검색 기능(`MemberQueryRepository.searchMembers`) 구현 중 QueryDSL DTO Projection 방식으로 `@QueryProjection`을 도입하려 했다.
`@QueryProjection`은 DTO 생성자를 Q 클래스(`QMemberSearchConditionDto`)로 생성하여 컴파일 타임에 타입 체크가 가능하다는 점이 매력적이었다.

---

### 문제 상황

```java
// 잘못된 사용 — 어노테이션을 클래스 레벨에 붙임
@Getter
@NoArgsConstructor
@QueryProjection  // ← 여기가 잘못됨
public class MemberSearchConditionDto {
    private String email;
    private String nickname;
    ...
}
```

`@QueryProjection`은 **생성자** 위에 선언해야 Q 클래스 생성이 가능하지만, 클래스 선언부에 붙이면서 APT(Annotation Processing Tool)가 잘못된 Q 클래스 생성을 시도해 프로젝트 전체의 Q 클래스 컴파일이 실패했다.

---

### 올바른 사용법

```java
// 올바른 사용 — 생성자 위에 선언
@Getter
@NoArgsConstructor
public class MemberSearchConditionDto {

    @QueryProjection  // ← 생성자에 붙여야 함
    public MemberSearchConditionDto(String email, String nickname, RoleType roleType, Boolean isDeleted) {
        this.email = email;
        ...
    }
}
```

`@QueryProjection`이 생성자에 붙어야 APT가 `QMemberSearchConditionDto(StringPath email, ...)` 형태의 Q 클래스를 생성하고, 이를 통해 `new QMemberSearchConditionDto(member.email, ...)` 방식으로 타입 안전한 Projection이 가능하다.

---

### 최종 결정 — `Projections.constructor()` 방식 채택

`@QueryProjection`은 문제를 수정해도 DTO에 `com.querydsl` 의존성이 생긴다는 단점이 있다. 이 DTO가 QueryDSL에 종속되면 테스트나 다른 레이어에서 재사용할 때 QueryDSL 없이는 동작하지 않는다.

세 가지 방식을 비교한 후 `Projections.constructor()`로 결정했다.

| 방식 | 특징 | 단점 |
|---|---|---|
| `@QueryProjection` | 컴파일 타임 타입 체크, IDE 자동완성 | DTO에 QueryDSL 의존성 생김 |
| `Projections.constructor()` | 생성자 호출, 타입+순서 기준 매핑 | 파라미터 순서가 맞지 않으면 런타임 오류 |
| `Projections.fields()` | 필드명 기준 매핑, 생성자 불필요 | 리플렉션, IDE 추적 불가, 오타 컴파일 타임에 미탐지 |

`Projections.constructor()`는 QueryDSL 의존성 없이 타입 기반 매핑이 가능하고, 파라미터 수와 타입이 생성자와 불일치하면 즉시 예외가 발생해 디버깅이 쉽다. `fields()` 방식은 리플렉션이라 IDE에서 필드 추적이 안 되고 필드명 오타를 컴파일 타임에 잡을 수 없어 제외했다.

```java
// 최종 코드 — Projections.constructor() 적용
queryFactory
    .select(Projections.constructor(MemberListResponseDto.class,
            member.email, member.nickname, member.role))
    .from(member)
    .where(...)
    .fetch();
```

---

### 관련 파일

- `src/main/java/com/study/shop/domain/member/dto/MemberSearchConditionDto.java`
- `src/main/java/com/study/shop/domain/member/dto/MemberListResponseDto.java`
- `src/main/java/com/study/shop/domain/member/repository/MemberQueryRepository.java`

### 교훈

- **`@QueryProjection`은 생성자 위에 선언해야 한다**: 클래스 레벨에 붙이면 APT가 잘못된 처리를 시도해 프로젝트 전체 Q 클래스 컴파일 오류로 번진다.
- **DTO는 QueryDSL 의존성을 갖지 않는 것이 낫다**: `@QueryProjection`은 타입 안전성이 강점이지만, 도메인 DTO가 QueryDSL에 종속되면 재사용성이 떨어진다.
- **`fields()` 방식은 편리해 보이지만 안전하지 않다**: 필드명 매핑은 IDE에서 추적이 안 되고 오타를 런타임에서야 발견하게 된다. `constructor()` 방식이 타입+순서 불일치를 즉시 노출해 더 안전하다.

**상태:** 해결됨

---

## 기록 #023: 연관 엔티티 고유값을 파생 필드로 저장하는 방식의 문제 (post.writer, comment.writer)

**작성일**: 2026-02-01  
**관련 도메인**: Post, Comment, Member

---

### 문제 상황

`Post.writer`, `Comment.writer` 필드에 `Member.email`을 직접 복사해 저장하는 방식을 사용했다. JOIN을 줄이려는 편의 목적이었지만, 이는 동일한 정보가 두 테이블에 중복 저장되는 데이터 정규화 위반이다.

```java
// 문제가 있는 구조
// Member 테이블:  id=1, email=hong@test.com
// Post 테이블:    id=1, writer="hong@test.com", member_id=1
// → 같은 정보가 두 군데 저장됨

@Entity
public class Post {
    private String writer;   // member.email 복사 저장 ← 문제

    @ManyToOne(fetch = LAZY)
    private Member member;   // 같은 정보를 연관관계로도 보유
}
```

### 원인 분석

회원이 이메일을 변경하면 `member` 테이블 1건 수정으로 끝나야 하는데, 파생 필드 구조에서는 `post.writer`, `comment.writer` 등 관련 테이블의 모든 레코드를 일괄 업데이트해야 한다. 동기화를 빠뜨리면 두 값이 불일치하는 데이터 정합성 문제가 생기고, 데이터가 많아질수록 업데이트 비용이 선형으로 증가한다.

### 해결 방법

파생 필드를 제거하고 연관관계에서 직접 접근하는 방식으로 변경했다.

```java
// v1 — Object Reference에서 직접 접근
post.getMember().getNickname()
post.getMember().getEmail()

// v2 목표 — ID Reference (MSA 전환 시 Aggregate 경계 명확화)
public class Post {
    private Long memberId;   // FK만 저장, 객체 참조 없음
    // 필요 시 MemberService.findById(memberId)로 명시적 조회
}
```

`Post → Member`는 N:1 관계이므로 Fetch Join + Paging 조합도 안전하게 사용할 수 있다. 파생 필드로 JOIN을 피하는 것보다 정규화 유지가 장기적으로 이점이 크다.

### 교훈

- **파생 필드는 단기 편의, 장기 기술 부채**: "자주 바뀌지 않을 것"이라는 가정은 위험하다. 정규화 원칙이 가장 안전한 기본값이다.
- **Object Reference → ID Reference 진화 방향**: 실무에서도 동일한 이유로 Object Reference(v1) → ID Reference(v2, MSA)로 발전시킨다. ID Reference는 Aggregate 경계를 명확히 하고 영속성 컨텍스트 의존도를 낮추는 DDD 원칙과 일치한다.
- **연관관계가 있으면 파생 필드는 불필요**: `@ManyToOne`으로 Member를 이미 참조하고 있다면, 거기서 직접 꺼내면 된다.

**상태:** 해결됨

---

## 기록 #024: `@SQLRestriction` 관리자 우회 전략 탐색 — Hibernate Filter + AOP 한계, Native Query 채택

**작성일**: 2026-04-28  
**관련 도메인**: Member, Order (Admin)

---

### 배경

`@SQLRestriction("deleted = false")`를 핵심 도메인에 적용한 뒤, 관리자 기능을 구현하면서 문제가 생겼다. `@SQLRestriction`은 해당 엔티티를 대상으로 하는 모든 JPQL 쿼리에 `deleted = false` 조건을 자동 삽입하므로, 탈퇴 회원 목록처럼 소프트 삭제된 데이터 자체가 필요한 경우에 우회할 방법이 없었다.

---

### 시도 1: Hibernate `@FilterDef` + `@Filter` + Session 직접 제어

`@SQLRestriction`을 제거하고 Hibernate의 `@FilterDef` + `@Filter`로 교체하면 Session 단위로 필터를 켜고 끌 수 있다.

```java
@FilterDef(name = "deletedFilter", parameters = @ParamDef(name = "deleted", type = Boolean.class))
@Filter(name = "deletedFilter", condition = "deleted = :deleted")
@Entity
public class Member { ... }

// 서비스에서 Session을 꺼내 직접 제어
Session session = entityManager.unwrap(Session.class);
session.enableFilter("deletedFilter").setParameter("deleted", false); // 일반 서비스
session.disableFilter("deletedFilter");                               // 관리자 서비스
```

**문제점**: 서비스 계층마다 Session을 꺼내 필터를 수동 조작해야 하므로 코드가 분산되고, 빠뜨릴 경우 소프트 삭제된 데이터가 노출되는 버그 위험이 있다.

---

### 시도 2: Spring AOP로 필터 자동 활성화 + 관리자 서비스에서만 비활성화

코드 분산 문제를 해결하기 위해 AOP Around Advice로 일반 서비스 메서드 진입 시 자동으로 필터를 활성화하고, 관리자 서비스(`admin.service` 패키지)는 AOP 적용 대상에서 제외하는 방식을 검토했다.

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

**문제점 — 동일 트랜잭션 내 필터 상태 덮어쓰기**

관리자 서비스가 AOP 대상 밖이라 필터가 꺼진 상태로 진입하더라도, 같은 트랜잭션 내에서 일반 서비스 메서드(`@Transactional(REQUIRED)` 조인)를 호출하는 순간, 그 메서드의 AOP Advice가 다시 필터를 활성화한다.

```
AdminService.kickMember()            ← AOP 제외, 필터 OFF
  └─ MemberService.findById(id)      ← AOP 적용, 필터 ON 으로 복구
  └─ CommentService.deleteAll(id)    ← AOP 적용, 필터 ON 유지
       → 소프트 삭제된 회원 조회 실패 (의도와 반대)
```

Hibernate 필터는 Session(= 트랜잭션) 범위로 관리되지만, AOP는 메서드 단위로 동작한다. 서비스 간 호출이 깊어질수록 필터 상태 추적이 불가능해지고, 이 문제를 완전히 막으려면 메서드 진입 시 "현재 admin 컨텍스트인지" 여부를 별도로 전파해야 하는 또 다른 복잡도가 생긴다.

---

### 최종 결정: Native Query

`@SQLRestriction`은 그대로 유지하고, 관리자 전용 Repository 메서드에만 `nativeQuery = true`를 명시했다. Native Query는 Hibernate ORM 레이어를 우회하므로 `@SQLRestriction`이 적용되지 않는다.

```java
// 관리자 전용 — native query로 @SQLRestriction 우회
@Query(value = "SELECT * FROM member WHERE deleted = true", nativeQuery = true)
List<Member> findAllDeletedMembers();

@Query(value = "SELECT * FROM member WHERE deleted = false OR deleted = true", nativeQuery = true)
List<Member> findAllIncludingDeleted();

// 일반 조회 — @SQLRestriction 자동 적용
List<Member> findAll(); // → WHERE ... AND deleted = false 자동 추가
```

---

### 트레이드오프 비교

| 방식 | 장점 | 단점 |
|---|---|---|
| `@Filter` + Session 직접 제어 | JPQL 재사용 가능, DB 독립성 | 코드 분산, 누락 시 버그 위험 |
| `@Filter` + AOP 자동화 | 코드 집중화, 일반 서비스는 신경 불필요 | 동일 트랜잭션 내 필터 상태 불안정, 예측 어려움 |
| **Native Query** (채택) | 명시적, 예측 가능, 구현 단순 | MySQL SQL 문법 의존성 |

관리자 기능은 수가 한정적이고 변경 빈도가 낮다. 범위가 제한된 DB 의존성이 트랜잭션 전반에 걸친 필터 상태 관리 복잡도보다 감수할 만한 트레이드오프라고 판단했다.

---

### 교훈

- **`@SQLRestriction`과 `@Filter`/`@FilterDef`는 다른 메커니즘이다**: `@SQLRestriction`은 항상 적용되고 토글 불가. `@Filter`는 Session 단위로 제어 가능하지만 처음부터 `@SQLRestriction` 대신 `@Filter`를 선택했어야 활용할 수 있다.
- **AOP와 Hibernate 필터의 범위 불일치**: AOP는 메서드 범위, Hibernate 필터는 Session(트랜잭션) 범위다. 이 둘을 조합하면 호출 스택 어디서든 상태가 뒤집힐 수 있어 유지보수가 어렵다.
- **"자동화"가 예측 불가능성을 만들 때는 명시적인 방법이 낫다**: 복잡한 설정보다 관리자 쿼리에 `nativeQuery = true`를 명시하는 것이 더 읽기 쉽고 안전하다.

**상태:** 해결됨

---
