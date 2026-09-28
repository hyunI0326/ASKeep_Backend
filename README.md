# ASKeep Backend

ASKeep 백엔드 서버 (Spring Boot)입니다.
이 문서는 **사용자 · 인증(로그인) · 세션** 기능을 기준으로 작성되어 있습니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security) |
| DB | PostgreSQL 17 (Docker, `pgvector/pgvector:pg17` 이미지) |
| 인증 | JWT (jjwt 0.12.6), 비밀번호는 BCrypt로 암호화 |
| 테스트 | JUnit 5, MockMvc, H2 (메모리 DB) |

> ⚠️ **Spring Boot 4**입니다
> 예: `AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`

---

## 로컬 실행 방법

### 1. 준비물
- JDK 21
- Docker Desktop (실행 중이어야 함)

### 2. DB 실행

```bash
docker compose up -d     # PostgreSQL 컨테이너 실행
docker ps                # askeep-db 가 Up 상태인지 확인
```

### 3. 서버 실행

```bash
./gradlew bootRun        # Windows: .\gradlew.bat bootRun
```

서버 주소: `http://localhost:8080`
테이블(`users`, `sessions`)은 서버가 뜰 때 자동으로 만들어집니다.

### 4. 테스트

```bash
./gradlew test           # Windows: .\gradlew.bat test
```

- `AuthAndSessionApiTest`: H2 메모리 DB를 쓰므로 Docker 없이도 돌아갑니다.
- `AsKeepApplicationTests`: 실제 PostgreSQL에 접속하므로 **Docker로 DB를 먼저 띄워야** 통과합니다.

---

## 로컬 개발 환경 설정값

로컬 개발용 값입니다. 실제 서버 비밀번호가 아니므로 공유해도 괜찮을 것 같습니다.

| 항목 | 값 | 설정 위치 |
|---|---|---|
| DB 이름 | `askeep` | `compose.yaml`, `application.yml` |
| DB 계정 / 비밀번호 | `askeep` / `askeep` | `compose.yaml`, `application.yml` |
| DB 포트 | `5432` | `compose.yaml` |
| DB 컨테이너 이름 | `askeep-db` | `compose.yaml` |
| DB 데이터 볼륨 | `askeep-data` (컨테이너를 꺼도 데이터 유지) | `compose.yaml` |
| 서버 포트 | `8080` (Spring 기본값) | - |
| JWT 만료 시간 | 24시간 (`86400000` ms) | `JwtProvider` 기본값 |

### 환경변수 (선택)

설정하지 않으면 위의 로컬 기본값이 쓰입니다.

| 환경변수 | 용도 | 비고 |
|---|---|---|
| `DB_PASSWORD` | DB 비밀번호 | 기본값 `askeep` |
| `JWT_SECRET` | JWT 서명 키 | **배포 시 반드시 설정** (32자 이상). 기본값은 코드에 공개되어 있어 운영에서 쓰면 토큰 위조가 가능합니다. |

> API 키(OpenAI 등)나 실제 서버 비밀번호는 **코드나 yml에 직접 쓰지 말고** `${OPENAI_API_KEY}`처럼 환경변수로 받아 주세요

### 자주 겪는 문제

- **5432 포트 충돌**: PC에 PostgreSQL이 이미 설치되어 실행 중이면 컨테이너가 뜨지 않습니다. 로컬 PostgreSQL을 끄고 다시 실행하시면 됩니다
- **DB 이름/계정을 바꿨는데 접속이 안 됨**: `compose.yaml`의 값은 컨테이너를 처음 만들 때만 적용됩니다. `docker compose down -v`(데이터 삭제됨) 후 `docker compose up -d`로 다시 만드세요. `application.yml`도 같은 값으로 맞춰야 합니다.
- **테스트 중 `DialectFactoryImpl` 에러**: PostgreSQL이 꺼져 있다는 뜻입니다. Docker Desktop과 DB 컨테이너를 실행하세요.

---

## 패키지 구조

```
com.GDGoCSMU.ASKeep
├── domain                      # 기능별 코드
│   ├── user
│   │   ├── controller          # AuthController(가입/로그인/로그아웃), UserController(내 정보)
│   │   ├── dto                 # SignupRequest, LoginRequest, LoginResponse, UserResponse
│   │   ├── entity              # User, Role
│   │   ├── repository          # UserRepository
│   │   └── service             # AuthService, UserService
│   └── session
│       ├── controller          # SessionController
│       ├── dto                 # SessionCreateRequest, SessionUpdateRequest, SessionResponse
│       ├── entity              # Session, SessionStatus
│       ├── repository          # SessionRepository
│       └── service             # SessionService, EntryCodeGenerator
└── global                      # 모든 기능이 같이 쓰는 코드
    ├── common                  # ApiResponse (공통 응답 형식)
    ├── exception               # ErrorCode, BusinessException, GlobalExceptionHandler
    └── security                # SecurityConfig, JwtProvider, JwtAuthenticationFilter, LoginUser, TokenBlacklist
```

새 기능(자료, 질문, 답변 등)도 `domain/<기능명>/controller|dto|entity|repository|service` 구조로 만들어 주세요.

요청 처리 흐름: `Controller` → `Service` → `Repository` → DB
- Controller: 요청 받기, 입력값 검증(`@Valid`), 응답 반환
- Service: 비즈니스 로직, 트랜잭션
- Entity를 그대로 응답하지 않고 DTO(`XxxResponse.from(entity)`)로 바꿔서 응답합니다.

---

## 공통 응답 형식

모든 API는 아래 형식으로 응답합니다. (`ApiResponse`)

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "LOGIN_FAILED", "message": "이메일 또는 비밀번호가 올바르지 않습니다." } }
```

- 성공 응답은 컨트롤러에서 `ApiResponse.ok(data)` 또는 `ApiResponse.ok()`로 반환합니다.
- 실패는 서비스에서 `throw new BusinessException(ErrorCode.XXX)`만 하면 `GlobalExceptionHandler`가 위 형식으로 바꿔 줍니다.
- 새 에러가 필요하면 `ErrorCode`에 추가하세요

### 에러 코드

| 코드 | HTTP | 메시지 |
|---|---|---|
| `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. (검증 실패 시 필드명과 이유가 들어감) |
| `UNAUTHORIZED` | 401 | 로그인이 필요합니다. |
| `FORBIDDEN` | 403 | 권한이 없습니다. |
| `INTERNAL_ERROR` | 500 | 서버 오류가 발생했습니다. |
| `DUPLICATE_EMAIL` | 409 | 이미 가입된 이메일입니다. |
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰입니다. |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. |
| `SESSION_NOT_FOUND` | 404 | 세션을 찾을 수 없습니다. |
| `NOT_SESSION_PRESENTER` | 403 | 세션 발표자만 할 수 있습니다. |
| `SESSION_ALREADY_STARTED` | 409 | 이미 시작되었거나 종료된 세션입니다. |
| `SESSION_NOT_IN_PROGRESS` | 409 | 진행 중인 세션이 아닙니다. |
| `SESSION_ALREADY_ENDED` | 409 | 종료된 세션은 수정할 수 없습니다. |

---

## 인증 (JWT) 동작 흐름

```
[회원가입]  POST /api/v1/users/auth/signup
            → 이메일 중복 확인 → 비밀번호 BCrypt 암호화 → users 테이블 저장

[로그인]    POST /api/v1/users/auth/login
            → 이메일로 사용자 조회 → 비밀번호 비교 → JWT(accessToken) 발급

[인증 요청] 헤더에  Authorization: Bearer <accessToken>
            → JwtAuthenticationFilter가 토큰 검증 (서명, 만료, 로그아웃 여부)
            → 성공하면 LoginUser(userId, role)를 로그인 사용자로 등록
            → SecurityConfig 규칙에 따라 통과 / 401 응답

[로그아웃]  POST /api/v1/users/auth/logout
            → 해당 토큰을 TokenBlacklist에 등록 → 이후 그 토큰은 사용 불가
```

- 이메일은 저장/조회할 때 공백 제거 + 소문자로 바꿉니다. (`Test@A.com` = `test@a.com`)
- 로그인 실패 시 "이메일 없음"과 "비밀번호 틀림"을 같은 에러(`LOGIN_FAILED`)로 응답합니다. (가입 여부 노출 방지)
- JWT 내용: `sub` = userId, `role` = USER/ADMIN, `jti` = 랜덤 ID, 만료 24시간
- 서버는 로그인 상태를 저장하지 않습니다 (STATELESS). 매 요청마다 토큰으로 사용자를 확인합니다.
- ⚠️ `TokenBlacklist`는 **메모리에 저장**되므로, 서버를 재시작하면 로그아웃한 토큰이 (만료 전까지) 다시 쓸 수 있게 됩니다. 배포 단계에서 필요하면 Redis 등으로 바꿀 예정입니다.

### 다른 기능에서 로그인 사용자 쓰는 법

컨트롤러 파라미터에 `@AuthenticationPrincipal LoginUser loginUser`를 추가하면 됩니다.

```java
@PostMapping
public ApiResponse<QuestionResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                            @Valid @RequestBody QuestionCreateRequest request) {
    Long userId = loginUser.userId();   // 로그인한 사용자 ID
    ...
}
```

User / Session 엔티티가 필요하면 아래 메서드를 쓰세요. (없으면 알아서 404 에러를 던집니다)

- `UserService.getUser(userId)` → `User`
- `SessionService.findSession(sessionId)` → `Session`

### 접근 권한 (SecurityConfig)

| 경로 | 권한 |
|---|---|
| `POST /api/v1/users/auth/signup`, `POST /api/v1/users/auth/login` | 누구나 |
| `/api/v1/users/**` (로그아웃, 내 정보 포함) | 로그인 필요 |
| `/api/v1/sessions/**` (조회 포함) | 로그인 필요 |
| **그 외 모든 경로** (자료/질문/답변, AI 서버 연동 등) | **현재 누구나 접근 가능** |

> 다른 기능의 API도 로그인이 필요하다면 `SecurityConfig`에 `.authenticated()` 규칙을 추가해 주세요. 팀에서 정해지면 바꿀 예정입니다.

---

## API 명세

로그인이 필요한 API는 헤더에 `Authorization: Bearer <accessToken>`을 넣어야 합니다.

### 사용자 / 인증

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/v1/users/auth/signup` | ❌ | 회원가입 (201) |
| POST | `/api/v1/users/auth/login` | ❌ | 로그인, 토큰 발급 |
| POST | `/api/v1/users/auth/logout` | ✅ | 로그아웃 (현재 토큰 무효화) |
| GET | `/api/v1/users/me` | ✅ | 내 정보 조회 |

**회원가입 요청**

```json
{ "email": "user@example.com", "password": "password123", "name": "홍길동" }
```

- `email`: 필수, 이메일 형식, 최대 100자
- `password`: 필수, 8~64자
- `name`: 필수, 최대 30자

**로그인 요청 / 응답**

```json
// 요청
{ "email": "user@example.com", "password": "password123" }

// 응답
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "userId": 1,
      "email": "user@example.com",
      "name": "홍길동",
      "role": "USER",
      "createdAt": "2026-09-28T14:00:00"
    }
  }
}
```

- `expiresIn`: 초 단위 (86400초 = 24시간)
- `role`: 가입하면 기본 `USER` (`ADMIN`도 정의되어 있지만 아직 쓰는 곳 없음)

### 세션

"세션"은 **발표/스터디 세션**을 뜻함 (로그인 세션이 아닙니다)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/v1/sessions` | ✅ | 세션 생성 (201), 만든 사람이 발표자 |
| GET | `/api/v1/sessions` | ✅ | 세션 목록 (최신순), `?status=READY\|ONGOING\|ENDED`로 필터 가능 |
| GET | `/api/v1/sessions/{sessionId}` | ✅ | 세션 상세 |
| PATCH | `/api/v1/sessions/{sessionId}` | ✅ 발표자만 | 제목 수정 (보낸 필드만 수정) |
| DELETE | `/api/v1/sessions/{sessionId}` | ✅ 발표자만 | 세션 삭제 |
| POST | `/api/v1/sessions/{sessionId}/start` | ✅ 발표자만 | 세션 시작 |
| POST | `/api/v1/sessions/{sessionId}/end` | ✅ 발표자만 | 세션 종료 |

**세션 생성 요청**

```json
{ "title": "1주차 스터디" }
```

- `title`: 필수, 최대 100자 (수정할 때는 공백만 입력 불가)

**세션 응답 (`SessionResponse`)**

```json
{
  "sessionId": 1,
  "title": "1주차 스터디",
  "presenterId": 1,
  "presenterName": "홍길동",
  "entryCode": "K7P2QX",
  "status": "READY",
  "startedAt": null,
  "endedAt": null,
  "createdAt": "2026-09-28T14:00:00"
}
```

**세션 상태 흐름**

```
READY (생성됨) ──start──▶ ONGOING (진행중) ──end──▶ ENDED (종료)
```

- 순서를 건너뛰면 에러: READY가 아닌데 start → `SESSION_ALREADY_STARTED`, ONGOING이 아닌데 end → `SESSION_NOT_IN_PROGRESS`
- `ENDED` 세션은 제목 수정 불가 (`SESSION_ALREADY_ENDED`)
- `startedAt`, `endedAt`은 시작/종료할 때 자동 기록

**입장 코드 (`entryCode`)**

- 세션 생성 시 자동 발급되는 **6자리 영문 대문자 + 숫자** (예: `K7P2QX`)
- 헷갈리는 문자 `0`, `O`, `1`, `I`는 쓰지 않습니다.
- 세션마다 겹치지 않습니다 (DB unique).

---

## DB 테이블

`ddl-auto: update` 설정이라 **엔티티를 수정하면 테이블이 자동으로 바뀝니다.** (개발용 설정임)
컬럼 삭제나 이름 변경은 자동으로 반영되지 않으니, 꼬이면 `docker compose down -v`로 DB를 초기화 요망

### users

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | bigint, PK | 자동 증가 |
| `email` | varchar(100), unique | 소문자로 저장 |
| `password` | varchar | BCrypt 암호화 값 (원문 저장 금지) |
| `name` | varchar(30) | |
| `role` | varchar(20) | `USER` / `ADMIN` |
| `created_at`, `updated_at` | timestamp | 자동 기록 |

### sessions

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | bigint, PK | 자동 증가 |
| `title` | varchar(100) | |
| `presenter_id` | bigint, FK → users.id | 세션을 만든 사람 |
| `entry_code` | varchar(6), unique | 입장 코드 |
| `status` | varchar(20) | `READY` / `ONGOING` / `ENDED` |
| `started_at`, `ended_at` | timestamp, nullable | 시작/종료 시각 |
| `created_at`, `updated_at` | timestamp | 자동 기록 |

==============================

## 팀원 참고 사항

- **Docker 필수**: 서버 실행에 PostgreSQL 컨테이너가 필요합니다. (`docker compose up -d`)
- **공통 응답·에러 형식을 지켜 주세요**: `ApiResponse`로 응답하고, 에러는 `BusinessException(ErrorCode.XXX)`로 던집니다
- **로그인 사용자 ID**는 `@AuthenticationPrincipal LoginUser`로 받습니다. 요청 본문으로 userId를 받지 마세요
- **SecurityConfig**: 현재 users/sessions 외 경로는 전부 열려 있습니다. 새 API에 로그인이 필요하면 규칙을 추가해야 합니다
- 
- 
