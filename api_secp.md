# ASKeep 프론트엔드 API 명세

원격 `feature/user-auth`의 사용자·세션 API와 현재 자료·질문·답변 API를 통합한 기준입니다.

## 공통 규칙

- 기본 경로: `/api/v1`
- 회원가입·로그인을 제외한 요청: `Authorization: Bearer {accessToken}`
- 일반 요청과 응답: `Content-Type: application/json`
- 업로드 요청: `multipart/form-data`, 파일 필드 이름 `file`
- JWT: HS256, 기본 만료 3600초. 배포 시 32바이트 이상의 `JWT_SECRET` 설정
- 날짜·시간: ISO 8601 LocalDateTime 문자열, 예: `2026-09-30T10:00:00`

성공 응답은 `{"success":true,"data":...}`입니다. 본문이 없는 성공은 `{"success":true}`를 반환합니다. 오류는 다음 형식입니다.

```json
{"success":false,"error":{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다."}}
```

목록 중 자료·질문은 `data`에 `{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}` 형태가 담깁니다. `page`는 0부터 시작하고 `size`는 기본 20, 최대 100입니다.

## 인증·사용자

| 기능 | 메서드·경로 | 요청 | 성공 상태·`data` |
|---|---|---|---|
| 회원가입 | `POST /users/auth/signup` | JSON: `email`, `password`(8~64자), `name`(최대 30자) | 201, 사용자 |
| 로그인 | `POST /users/auth/login` | JSON: `email`, `password` | 200, 토큰·사용자 |
| 로그아웃 | `POST /users/auth/logout` | Bearer JWT, 본문 없음 | 200, 본문 없음 |
| 내 정보 | `GET /users/me` | Bearer JWT | 200, 사용자 |

사용자 형식:

```json
{"userId":1,"email":"alice@example.com","name":"alice","role":"USER","createdAt":"2026-09-30T10:00:00"}
```

로그인 `data` 형식:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {"userId":1,"email":"alice@example.com","name":"alice","role":"USER","createdAt":"2026-09-30T10:00:00"}
}
```

## 세션

새 세션의 상태는 `READY → ONGOING → ENDED`입니다. 기존 DB에 저장된 `ACTIVE`도 진행 중 상태로 읽을 수 있습니다.

| 기능 | 메서드·경로 | 요청 | 성공 상태·`data` |
|---|---|---|---|
| 생성 | `POST /sessions` | JSON: `title`(필수, 최대 100자), `description`(선택) | 201, 세션 |
| 목록 | `GET /sessions` | 선택 쿼리 `status=READY\|ONGOING\|ENDED` | 200, 세션 배열 |
| 상세 | `GET /sessions/{sessionId}` | 본문 없음 | 200, 세션 |
| 수정 | `PATCH /sessions/{sessionId}` | JSON: `title`, `description` 중 변경할 필드 | 200, 세션 |
| 삭제 | `DELETE /sessions/{sessionId}` | 본문 없음 | 200, 본문 없음 |
| 시작 | `POST /sessions/{sessionId}/start` | 본문 없음 | 200, 세션 |
| 종료 | `POST /sessions/{sessionId}/end` | 본문 없음 | 200, 세션 |
| 참여 | `POST /sessions/{sessionId}/participants` | 본문 없음 | 201, 참여 정보 |

세션 `data` 형식:

```json
{
  "sessionId": 1,
  "title": "발표 세션",
  "description": "세션 설명",
  "presenterId": 1,
  "presenterName": "alice",
  "entryCode": "ABC234",
  "status": "READY",
  "startedAt": null,
  "endedAt": null,
  "createdAt": "2026-09-30T10:00:00"
}
```

참여 `data`: `{"sessionId":1,"userId":2,"role":"AUDIENCE"}`. 세션 수정·삭제·시작·종료는 발표자만 할 수 있습니다.

## 자료

자료 `data`: `{"id":1,"sessionId":1,"fileName":"file.pdf","contentType":"application/pdf","fileSize":1234,"status":"PENDING","createdAt":"...","updatedAt":"..."}`

| 기능 | 메서드·경로 | 요청 | 성공 상태·`data` |
|---|---|---|---|
| 업로드 | `POST /sessions/{sessionId}/materials` | `multipart/form-data`: `file`(PDF, 최대 50 MB) | 202, 자료 |
| 목록 | `GET /sessions/{sessionId}/materials` | 선택 쿼리 `page`, `size` | 200, 페이지 |
| 상세 | `GET /materials/{materialId}` | 본문 없음 | 200, 자료 |
| 삭제 | `DELETE /materials/{materialId}` | 본문 없음 | 200, 본문 없음 |
| AI 재시도 | `POST /materials/{materialId}/retry` | 본문 없음 | 202, 자료 |

자료 상태: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. 업로드와 재시도는 비동기 AI 작업을 시작합니다.

## 질문

질문 `data`: `{"id":1,"sessionId":1,"content":"질문","anonymous":false,"author":{"id":2,"username":"bob"},"aiStatus":"PENDING","answers":[],"createdAt":"...","updatedAt":"..."}`. 익명 질문은 `author:null`입니다.

| 기능 | 메서드·경로 | 요청 | 성공 상태·`data` |
|---|---|---|---|
| 등록 | `POST /sessions/{sessionId}/questions` | JSON: `content`(필수), `anonymous`(기본 `false`) | 202, 질문 |
| 목록 | `GET /sessions/{sessionId}/questions` | 선택 쿼리 `page`, `size` | 200, 페이지 |
| 새 질문 조회 | `GET /sessions/{sessionId}/questions?afterId={id}` | `afterId` | 200, `{"items":[],"nextAfterId":0}` |
| 상세 | `GET /questions/{questionId}` | 본문 없음 | 200, 질문 |
| 수정 | `PATCH /questions/{questionId}` | JSON: `content`, `anonymous` 중 변경할 필드 | 200, 질문 |
| 삭제 | `DELETE /questions/{questionId}` | 본문 없음 | 200, 본문 없음 |
| AI 답변 재시도 | `POST /questions/{questionId}/ai-answer/retry` | 본문 없음 | 202, 질문 |

`aiStatus`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`. 새 질문 조회는 최대 100개를 반환합니다.

## 답변

답변 `data`: `{"id":1,"questionId":1,"content":"답변","type":"PRESENTER","author":{"id":1,"username":"alice"},"createdAt":"..."}`. AI 답변은 `type:"AI"`, `author:null`입니다.

| 기능 | 메서드·경로 | 요청 | 성공 상태·`data` |
|---|---|---|---|
| 목록 | `GET /questions/{questionId}/answers` | 본문 없음 | 200, 답변 배열 |
| 등록 | `POST /questions/{questionId}/answers` | JSON: `content`(필수) | 201, 답변 |
| 수정 | `PATCH /answers/{answerId}` | JSON: `content`(필수) | 200, 답변 |
| 삭제 | `DELETE /answer/{answerId}` | 본문 없음 | 200, 본문 없음 |

답변 삭제 경로는 현재 구현대로 `/answer/{answerId}` 단수형입니다.
