# ASKeep 프론트엔드 API 명세

현재 백엔드 구현 기준(2026-10-01)입니다. 아래 경로에는 공통 접두사 `/api/v1`를 생략했습니다.

## 1. 공통 규칙

### 요청과 인증

- 회원가입과 로그인을 제외한 모든 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.
- JSON 본문을 보내는 요청은 `Content-Type: application/json`을 사용합니다. 자료 업로드만 `multipart/form-data`를 사용합니다.
- 액세스 토큰은 HS256 JWT이며 기본 만료 시간은 3,600초입니다. 만료되면 다시 로그인해야 합니다. 토큰 갱신 API는 구현되어 있지 않습니다.
- 날짜·시간은 시간대 정보가 없는 ISO 8601 문자열입니다. 예: `2026-10-01T10:30:00`.
- 경로의 ID(`sessionId`, `materialId`, `questionId`, `answerId`)는 정수입니다.

### 공통 응답

데이터가 있는 성공 응답:

```json
{
  "success": true,
  "data": {"id": 1}
}
```

데이터가 없는 성공 응답(로그아웃·삭제):

```json
{"success": true}
```

오류 응답:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_INPUT",
    "message": "title: 공백일 수 없습니다"
  }
}
```

`error.message`는 상황에 따라 달라집니다. 필드 검증 오류는 현재 첫 번째 오류의 필드명과 메시지만 포함하며, 별도의 `fieldErrors` 객체는 없습니다.

| HTTP 상태 | 대표 `error.code` | 의미 |
|---:|---|---|
| 400 | `INVALID_INPUT` | 요청 본문·쿼리·유효성 오류 |
| 401 | `UNAUTHORIZED`, `LOGIN_FAILED`, `INVALID_TOKEN` | 미인증, 로그인 실패, 잘못된 토큰 |
| 403 | `FORBIDDEN`, `NOT_SESSION_PRESENTER` | 해당 작업 권한 없음 |
| 404 | `RESOURCE_NOT_FOUND`, `SESSION_NOT_FOUND`, `USER_NOT_FOUND` | 대상 없음 |
| 409 | `CONFLICT`, `DUPLICATE_EMAIL`, 세션 상태 오류 코드 | 중복 또는 현재 상태에서 허용되지 않는 작업 |
| 413 | HTTP 상태 문자열 | 파일 용량 초과 |
| 415 | HTTP 상태 문자열 | PDF 형식 오류 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

### 목록과 비동기 처리

자료·질문의 일반 목록은 `page`(기본 0), `size`(기본 20, 1~100)를 받습니다. `data`는 아래 형식입니다. 세션 목록과 답변 목록에는 이 페이지 형식을 사용하지 않습니다.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

자료 업로드와 질문 등록, AI 재시도는 `202 Accepted`를 반환한 뒤 AI 처리를 진행합니다. 반환 직후 상태가 `PENDING`이었다가 `PROCESSING`, `COMPLETED` 또는 `FAILED`로 바뀔 수 있으므로 상세·목록 API로 상태를 다시 조회하세요.

## 2. 인증·사용자

| 기능 | 메서드·경로 | 요청 본문 | 성공 |
|---|---|---|---|
| 회원가입 | `POST /users/auth/signup` | `SignupRequest` | 201, `UserResponse` |
| 로그인 | `POST /users/auth/login` | `LoginRequest` | 200, `LoginResponse` |
| 로그아웃 | `POST /users/auth/logout` | 없음 | 200, 데이터 없음 |
| 내 정보 | `GET /users/me` | 없음 | 200, `UserResponse` |

`SignupRequest`:

```json
{
  "email": "alice@example.com",
  "password": "password123",
  "name": "앨리스"
}
```

`email`은 필수·이메일 형식·최대 100자, `password`는 필수·8~64자, `name`은 필수·최대 30자입니다. 이메일은 앞뒤 공백을 제거하고 소문자로 저장합니다. 비밀번호는 응답에 포함하지 않습니다. 중복 이메일은 `409 DUPLICATE_EMAIL`입니다.

`LoginRequest`: `{"email":"alice@example.com","password":"password123"}`. 잘못된 이메일 또는 비밀번호는 `401 LOGIN_FAILED`입니다.

`UserResponse`(`data`):

```json
{
  "userId": 1,
  "email": "alice@example.com",
  "name": "앨리스",
  "role": "USER",
  "createdAt": "2026-10-01T10:30:00"
}
```

`LoginResponse`(`data`):

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "userId": 1,
    "email": "alice@example.com",
    "name": "앨리스",
    "role": "USER",
    "createdAt": "2026-10-01T10:30:00"
  }
}
```

`expiresIn`의 단위는 초입니다. 로그아웃한 토큰은 만료될 때까지 서버 메모리에서 차단합니다. 서버가 재시작되면 이 차단 목록은 초기화됩니다.

## 3. 세션

| 기능 | 메서드·경로 | 요청 | 성공 |
|---|---|---|---|
| 생성 | `POST /sessions` | JSON: `title`(필수), `description`(선택) | 201, `SessionResponse` |
| 목록 | `GET /sessions` | 선택 쿼리 `status` | 200, `SessionResponse[]` |
| 상세 | `GET /sessions/{sessionId}` | 없음 | 200, `SessionResponse` |
| 수정 | `PATCH /sessions/{sessionId}` | JSON: `title`, `description` 중 변경할 필드 | 200, `SessionResponse` |
| 삭제 | `DELETE /sessions/{sessionId}` | 없음 | 200, 데이터 없음 |
| 시작 | `POST /sessions/{sessionId}/start` | 없음 | 200, `SessionResponse` |
| 종료 | `POST /sessions/{sessionId}/end` | 없음 | 200, `SessionResponse` |
| 참여 | `POST /sessions/participants` | JSON: `entryCode`(필수) | 201, `ParticipantResponse` |

생성 요청 예시:

```json
{"title":"1주차 발표","description":"질문을 받는 세션"}
```

`title`은 공백만으로 구성할 수 없고 최대 100자입니다. 수정 요청은 보낸 필드만 변경합니다. `description:null`은 변경 없음으로 처리됩니다.

`SessionResponse`(`data`):

```json
{
  "sessionId": 1,
  "title": "1주차 발표",
  "description": "질문을 받는 세션",
  "presenterId": 1,
  "presenterName": "앨리스",
  "entryCode": "ABC234",
  "status": "READY",
  "startedAt": null,
  "endedAt": null,
  "createdAt": "2026-10-01T10:30:00"
}
```

- 새 세션 상태는 `READY → ONGOING → ENDED`입니다. 기존 DB의 `ACTIVE`도 진행 중 상태로 읽고 종료할 수 있습니다.
- 목록은 생성일 내림차순의 전체 세션 배열입니다. `?status=READY`, `?status=ONGOING`, `?status=ACTIVE`, `?status=ENDED`로 필터링할 수 있습니다. 로그인 사용자별 필터와 페이지 처리는 없습니다.
- 조회·목록은 로그인 사용자에게 열려 있습니다. 수정·삭제·시작·종료는 발표자만 할 수 있습니다. 종료된 세션은 수정할 수 없습니다. 처리 중인 자료가 있는 세션은 삭제할 수 없습니다.
- `entryCode`는 새 세션에 발급되는 6자리 코드입니다(영문 대문자·숫자, 헷갈리는 `0 O 1 I` 제외). 이전 DB에서 생성된 세션은 `entryCode:null`일 수 있으며, 이런 세션에는 참여할 수 없습니다.
- 참여는 입장 코드로만 합니다. 세션 ID만으로 참여하던 `POST /sessions/{sessionId}/participants`는 삭제되었습니다.

참여 요청 예시: `{"entryCode":"ABC234"}`. 대소문자와 앞뒤 공백은 무시합니다.

| 상황 | 응답 |
| --- | --- |
| 형식 오류(영문·숫자 6자리 아님) | 400 `INVALID_INPUT` |
| 코드에 해당하는 세션 없음 | 404 `INVALID_ENTRY_CODE` |
| 발표자 본인이 참여 시도 | 409 `PRESENTER_CANNOT_JOIN` |
| 종료된 세션 | 409 `SESSION_ENDED` |
| 이미 참여한 사용자가 다시 요청 | 201, 기존 참여 정보 반환 |

`ParticipantResponse`(`data`): `{"sessionId":1,"userId":2,"role":"AUDIENCE"}`.

## 4. 자료

| 기능 | 메서드·경로 | 요청 | 성공 |
|---|---|---|---|
| 업로드 | `POST /sessions/{sessionId}/materials` | `multipart/form-data`: `file` | 202, `MaterialResponse` |
| 목록 | `GET /sessions/{sessionId}/materials` | 선택 쿼리 `page`, `size` | 200, 페이지<`MaterialResponse`> |
| 상세 | `GET /materials/{materialId}` | 없음 | 200, `MaterialResponse` |
| 삭제 | `DELETE /materials/{materialId}` | 없음 | 200, 데이터 없음 |
| AI 처리 재시도 | `POST /materials/{materialId}/retry` | 없음 | 202, `MaterialResponse` |

업로드 파일은 최대 50 MiB의 PDF여야 하며, 파일 이름 확장자 `.pdf`와 파일 시작 부분의 `%PDF-`를 확인합니다. 업로드·삭제·재시도는 발표자만, 목록·상세 조회는 발표자와 참여자가 사용할 수 있습니다. 처리 중인 자료는 삭제할 수 없고, AI 처리 재시도는 `FAILED` 상태에서만 가능합니다.

`MaterialResponse`(`data`):

```json
{
  "id": 1,
  "sessionId": 1,
  "fileName": "lecture.pdf",
  "contentType": "application/pdf",
  "fileSize": 12345,
  "status": "PENDING",
  "createdAt": "2026-10-01T10:30:00",
  "updatedAt": "2026-10-01T10:30:00"
}
```

`fileSize` 단위는 바이트입니다. `status` 값은 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`입니다. 목록은 ID 내림차순입니다. 파일 다운로드 API는 구현되어 있지 않습니다.

## 5. 질문

| 기능 | 메서드·경로 | 요청 | 성공 |
|---|---|---|---|
| 등록 | `POST /sessions/{sessionId}/questions` | JSON: `content`(필수), `anonymous`(선택) | 202, `QuestionResponse` |
| 목록 | `GET /sessions/{sessionId}/questions` | 선택 쿼리 `page`, `size` | 200, 페이지<`QuestionResponse`> |
| 신규 질문 조회 | `GET /sessions/{sessionId}/questions?afterId={id}` | `afterId`(0 이상) | 200, Polling 응답 |
| 상세 | `GET /questions/{questionId}` | 없음 | 200, `QuestionResponse` |
| 수정 | `PATCH /questions/{questionId}` | JSON: `content`, `anonymous` 중 변경할 필드 | 200, `QuestionResponse` |
| 삭제 | `DELETE /questions/{questionId}` | 없음 | 200, 데이터 없음 |
| AI 답변 재시도 | `POST /questions/{questionId}/ai-answer/retry` | 없음 | 202, `QuestionResponse` |

등록 요청 예시: `{"content":"이 부분을 설명해 주세요","anonymous":false}`. `content`는 공백만으로 구성할 수 없습니다. `anonymous` 생략 시 `false`입니다.

`QuestionResponse`(`data`):

```json
{
  "id": 1,
  "sessionId": 1,
  "content": "이 부분을 설명해 주세요",
  "anonymous": false,
  "author": {"id": 2, "username": "밥"},
  "aiStatus": "PENDING",
  "answers": [],
  "createdAt": "2026-10-01T10:30:00",
  "updatedAt": "2026-10-01T10:30:00"
}
```

`author.username`에는 사용자의 `name` 값이 들어갑니다. 익명 질문은 `author:null`입니다. `aiStatus` 값은 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`입니다.

- 등록·조회는 세션 발표자 또는 참여자만 가능합니다. 등록은 세션이 진행 중(`ONGOING`)일 때만 가능하며, 시작 전·종료 후에는 409 `SESSION_NOT_IN_PROGRESS`입니다. 질문 수정은 작성자만, AI 처리가 시작되기 전(`PENDING`)에만 가능합니다.
- 삭제는 작성자 또는 발표자가 할 수 있습니다. AI 답변 재시도는 발표자만, `FAILED` 상태에서만 가능합니다.
- 일반 목록은 ID 내림차순입니다. Polling은 `afterId`보다 큰 ID를 오름차순으로 최대 100개 반환합니다.

Polling 응답 예시:

```json
{
  "success": true,
  "data": {
    "items": [],
    "nextAfterId": 12
  }
}
```

새 항목이 없으면 `nextAfterId`는 요청한 `afterId`와 같습니다.

## 6. 답변

| 기능 | 메서드·경로 | 요청 | 성공 |
|---|---|---|---|
| 목록 | `GET /questions/{questionId}/answers` | 없음 | 200, `AnswerResponse[]` |
| 등록 | `POST /questions/{questionId}/answers` | JSON: `content`(필수) | 201, `AnswerResponse` |
| 수정 | `PATCH /answers/{answerId}` | JSON: `content`(필수) | 200, `AnswerResponse` |
| 삭제 | `DELETE /answer/{answerId}` | 없음 | 200, 데이터 없음 |

등록·수정 요청 예시: `{"content":"발표자의 답변입니다"}`. `content`는 공백만으로 구성할 수 없습니다.

`AnswerResponse`(`data`):

```json
{
  "id": 1,
  "questionId": 1,
  "content": "발표자의 답변입니다",
  "type": "PRESENTER",
  "author": {"id": 1, "username": "앨리스"},
  "createdAt": "2026-10-01T10:35:00"
}
```

`type`은 `PRESENTER` 또는 `AI`입니다. AI 답변은 `author:null`입니다. 목록은 ID 오름차순이며, 세션 발표자·참여자가 조회할 수 있습니다. 발표자만 답변을 등록할 수 있고, 작성한 발표자 답변만 수정할 수 있습니다. 삭제는 작성자 또는 발표자가 할 수 있으며, AI 답변은 발표자만 삭제할 수 있습니다.

현재 삭제 경로는 `/answers/{answerId}`가 아닌 **`/answer/{answerId}`**입니다.
