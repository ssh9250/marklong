# 개발 기록

> Marklong(주식 포트폴리오·뉴스·커뮤니티 서비스) 개발 과정에서 마주친 문제와 해결 과정을 기록합니다.

---

## 기록 #001: 인프라 의존성을 docker-compose.infra / app 으로 2분할

### 배경
TimescaleDB(PostgreSQL), Redis, Kafka 등 외부 인프라가 동시에 늘어나면서 로컬 개발·배포 환경에서 컨테이너를 어떻게 묶을지 결정이 필요했다.

### 문제 상황
- 단일 compose 파일로 묶으면 애플리케이션 코드만 재배포할 때도 DB·Redis 컨테이너가 함께 영향을 받을 위험이 있다.
- 반대로 로컬 개발 시에는 인프라만 띄우고 애플리케이션은 IDE에서 직접 실행하고 싶다.

### 해결 방법
인프라와 애플리케이션 컴포즈 파일을 분리했다.

- `docker-compose.infra.yml` — TimescaleDB(`timescale/timescaledb:latest-pg16`), Redis(`redis:7.2-alpine`). healthcheck·named volume·`restart: unless-stopped` 적용
- `docker-compose.yml` — 애플리케이션·Nginx
- `init-scripts/01-init-timescaledb.sql` 로 TimescaleDB extension 초기화
- `.env` / `.env.example` 로 DB 계정·JWT secret 등 환경변수 분리, `.gitignore` 에 `.env` 등록
- 주 DB로 PostgreSQL 대신 TimescaleDB를 선택 — 주가(StockPrice)가 시계열 데이터이고 향후 hypertable 활용을 염두에 둠

### 관련 파일
- `docker-compose.infra.yml`, `docker-compose.yml`, `Dockerfile`
- `init-scripts/01-init-timescaledb.sql`
- `.env.example`, `application.yaml`

### 교훈
- **인프라와 애플리케이션의 생명주기를 분리**: 인프라는 한 번 띄우면 오래 유지되고, 애플리케이션은 자주 재배포된다. 컴포즈 파일을 나누면 로컬 개발(인프라만 컨테이너) / 배포(전체 컨테이너) 시나리오를 같은 정의로 커버할 수 있다.
- **TimescaleDB는 PostgreSQL 호환**: 시계열 특화 기능을 쓰면서도 JPA·드라이버는 PostgreSQL 그대로 사용 가능 — 도입 비용이 낮다.

---

## 기록 #002: JWT subject를 email → userId 기반으로 전환

### 배경
초기 JWT는 `subject`에 email을 담아 발급했다. 인증 필터에서 email로 사용자를 식별하는 구조였다.

### 문제 상황
1. **email 변경 시 토큰 무효화**: 사용자가 email을 바꾸면 발급된 토큰의 subject와 실제 식별자가 어긋난다.
2. **PII가 토큰에 노출**: JWT payload는 Base64 디코딩만으로 읽히므로 email이 그대로 드러난다.
3. **소프트 삭제 식별 일관성**: 내부 조회는 모두 `userId` 기준인데 토큰만 email이라 변환 비용이 발생한다.

### 해결 방법
- `JwtTokenProvider.createAccessToken(Long userId, Role role)` — subject를 `userId`로, role을 커스텀 클레임으로 발급
- `createRefreshToken()` — UUID 단순 발급(클레임 없는 불투명 토큰)으로 단순화
- `CustomUserDetailsService.loadUserById()` 추가, 인증 필터가 userId로 사용자 로드
- JJWT 0.12.x 최신 API(`Jwts.parser().verifyWith().build()`)로 정리

### 관련 파일
- `security/jwt/JwtTokenProvider.java`
- `security/auth/CustomUserDetailsService.java`, `CustomUserDetails.java`

### 교훈
- **토큰 subject는 불변 식별자**: email·nickname처럼 사용자가 바꿀 수 있는 값을 식별자로 쓰면 토큰 수명 내내 정합성 부담이 따라온다. 변하지 않는 PK(userId)를 subject로 쓰는 것이 안전하다.
- **Access Token과 Refresh Token의 성격 분리**: Access는 클레임을 담은 검증 가능한 토큰, Refresh는 서버 저장소와 대조하는 불투명 토큰(UUID)으로 역할을 나누면 각자 단순해진다.

---

## 기록 #003: Kafka 역직렬화 실패가 컨슈머 전체를 멈추는 문제

### 배경
Django 크롤러가 처리한 뉴스를 `news.processed` 토픽으로 발행하고, Spring이 이를 소비해 DB에 저장하는 파이프라인을 구성했다.

### 문제 상황
JSON 역직렬화에 실패하는 메시지(스키마 불일치, 깨진 payload 등) 하나가 들어오면 `JsonDeserializer`가 예외를 던지고, 해당 파티션의 offset이 진행되지 못해 컨슈머가 같은 메시지에서 무한 재시도(poison message)에 빠진다.

### 해결 방법
- consumer `value-deserializer`를 `ErrorHandlingDeserializer`로 감싸고 delegate를 `JsonDeserializer`로 지정 → 역직렬화 실패 시 예외 대신 `null` 메시지로 전달
- `KafkaConsumer.consumeNews()`에서 `newsEvent == null`이면 로그 남기고 스킵
- 비즈니스 저장 실패는 try-catch로 격리하고 `// todo: DLT(dead letter topic) 연동` 주석으로 후속 과제 명시
- `NewsService.saveFromKafka()`는 `existsBySourceId()`로 중복 수신을 방어(컨슈머 at-least-once 특성 대응)

### 관련 파일
- `application.yaml` (spring.kafka.consumer)
- `infra/kafka/KafkaConsumer.java`, `infra/kafka/dto/NewsEvent.java`
- `domain/news/service/NewsService.java`

### 교훈
- **역직렬화 실패는 비즈니스 실패와 다르게 다뤄야 한다**: 깨진 메시지는 재시도해도 영원히 실패한다. `ErrorHandlingDeserializer`로 독을 무력화해 파이프라인 전체가 멈추는 것을 막았다.
- **컨슈머는 at-least-once를 가정**: 같은 메시지가 두 번 올 수 있으므로 `sourceId` 기준 멱등 저장으로 중복을 방어한다.
- **DLT는 후속 과제로 명시**: 당장 구현하지 않더라도 실패 메시지 적재 전략을 TODO로 남겨 설계 의도를 추적 가능하게 했다.

---

## 기록 #004: 뉴스 본문/메타데이터 차등 보관 스케줄러

### 배경
뉴스는 수집량이 빠르게 누적된다. 본문(content)은 용량이 크지만, 목록·검색에 쓰이는 메타데이터(제목·종목코드·감성·중요도)는 더 오래 보고 싶다.

### 문제 상황
- 본문까지 무기한 보관하면 저장 비용이 선형으로 증가한다.
- 그렇다고 뉴스 전체를 짧게 지우면 "과거 종목 관련 뉴스 흐름" 같은 메타 기반 조회가 불가능해진다.

### 해결 방법
- `News`(메타)와 `NewsContent`(본문)를 별도 테이블로 분리하고 `NewsContent.newsId`로 참조
- `NewsSchedular`에서 차등 TTL 스케줄링
  - 매일 03:00 — `NewsContent` 30일 경과분 삭제
  - 매일 03:30 — `News` 90일 경과분 삭제
- 본문이 먼저 사라져도 메타데이터로 목록/검색은 90일까지 유지
- 같은 패턴을 `RefreshTokenService.deleteExpiredTokens()`(매일 03:00)에도 적용 — 만료 토큰 정리

### 관련 파일
- `domain/news/schedular/NewsSchedular.java`
- `domain/news/domain/News.java`, `NewsContent.java`
- `config/SchedulingConfig.java` (`@EnableScheduling`)

### 교훈
- **데이터의 보관 가치는 컬럼마다 다르다**: 무거운 본문과 가벼운 메타를 한 테이블에 두면 보관 정책을 분리할 수 없다. 테이블을 쪼개니 "본문은 30일, 메타는 90일" 같은 차등 정책이 가능해졌다.
- **스케줄 시간을 겹치지 않게 분산**: 본문 03:00, 메타 03:30으로 어긋나게 둬 동시 대량 삭제로 인한 부하 집중을 피했다.

---

## 기록 #005: Role enum 접두사 제거에 따른 Security 연쇄 수정

### 배경
초기 `Role` enum은 `ROLE_USER`, `ROLE_ADMIN` 형태였다. Spring Security의 `hasRole()`이 내부적으로 `ROLE_` 접두사를 자동으로 붙이는 규칙과 충돌 소지가 있었다.

### 문제 상황
- enum 값 자체에 `ROLE_`이 박혀 있으면 `SimpleGrantedAuthority` 생성·`hasRole`/`hasAuthority` 사용 시 접두사 중복(`ROLE_ROLE_USER`) 또는 불일치가 발생하기 쉽다.
- enum 값을 바꾸면 SecurityConfig·EventService 등 enum을 직접 비교하는 모든 지점이 함께 깨진다.

### 해결 방법
- `Role`: `ROLE_USER/ROLE_ADMIN` → `USER/ADMIN`로 단순화
- `SecurityConfig`: `hasRole(...)` → `hasAuthority(...)`로 조정, `/api/events/admin`에 ADMIN 권한 명시
- `EventService`: `role == Role.ROLE_ADMIN` → `role == Role.ADMIN` 일괄 수정
- `User`·`domain/user` 패키지 이동과 함께 정리

### 관련 파일
- `domain/user/domain/Role.java`
- `config/SecurityConfig.java`
- `domain/event/service/EventService.java`

### 교훈
- **enum 값에 프레임워크 규약을 박지 않는다**: `ROLE_` 접두사는 Security 내부 규약이지 도메인 값의 일부가 아니다. 도메인은 `USER/ADMIN`으로 두고, 접두사가 필요한 곳에서만 부여하는 편이 결합도를 낮춘다.
- **enum 리네이밍은 전역 영향**: 값 비교가 코드 전반에 흩어져 있으면 enum 변경이 연쇄 수정을 부른다. CLAUDE.md에 "Role은 접두사 없이 그대로 사용" 규칙으로 명문화해 재발을 방지했다.
