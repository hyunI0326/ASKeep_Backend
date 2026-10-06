## 추가·변경된 API

| 기능 | 메서드·경로 | 권한 | 성공 |
|---|---|---|---|
| 입장 코드로 참여 | `POST /api/v1/sessions/participants` `{"entryCode":"ABC234"}` | 로그인 | 201 |
| 세션 요약 조회 | `GET /api/v1/sessions/{sessionId}/summary` | 발표자·참여자 | 200 |
| 세션 요약 재시도 | `POST /api/v1/sessions/{sessionId}/summary/retry` | 발표자 (FAILED일 때만) | 202 |
| 내 세션 기록 | `GET /api/v1/users/me/sessions?role=PRESENTER\|AUDIENCE` | 로그인 | 200 |
| ~~세션 ID로 참여~~ | ~~`POST /api/v1/sessions/{sessionId}/participants`~~ | **삭제됨** | |

자세한 요청·응답 형식과 에러 코드는 `api_secp.md`에 반영했습니다.
노션과 다른 점이 있거나 수정사항이 있다면 알려주시면 감사하겠습니다

## 실시간 알림 (웹소켓)
- 연결: `ws://localhost:8080/ws` (STOMP, heart-beat 10초)
- 구독: `/topic/sessions/{sessionId}`
- 인증: CONNECT 헤더에 `Authorization: Bearer {accessToken}` 필수
- 구독은 그 세션의 발표자·참여자만 가능하고, 받기 전용이라 클라이언트가 메시지를 보낼 수 없습니다
- 메시지 형식: `{ eventId, type, sessionId, occurredAt, data }`
- 현재 발송되는 알림: 세션 시작·종료 시 `SESSION_STATUS_CHANGED` (`data: { "status": "ONGOING" | "ENDED" }`)

- ## 팀원별 참고 부탁

- 질문 실시간 알림은 아래 한 줄로 보내면 됩니다 DB 커밋 후 자동으로 `/topic/sessions/{id}`에 명세 형식으로 발송됩니다.
  ```java
  eventPublisher.publishEvent(new SessionTopicEvent(sessionId, RealtimeEventType.QUESTION_CREATED, 질문객체));
  ```
  (`ApplicationEventPublisher` 주입, 타입은 `RealtimeEventType`에 정의되어 있음)
- `QuestionService.create`에서 `requireMember` → `requireLiveMember`로 한 줄 바꿨습니다 (진행 중 세션에서만 질문)
- 버그 수정: 질문 등록 시 `anonymous`를 생략하면 400이 나던 문제를 고쳤습니다. (`QuestionRequests.Create`의 `boolean` → `Boolean`, 생략 시 false)
- 질문 명세의 세션 상태 `LIVE/CLOSED`를 `ONGOING/ENDED`로 맞췄습니다

  ==============================

-`ai-server/main.py`에 `POST /sessions/summary`를 추가했습니다. 기존 `/ai/answer`와 같은 방식(Gemini, 503 재시도)이고 JSON으로 `{ sessionId, summary, tags }`를 돌려줍니다.
- 로컬에 Python이 없어 직접 실행해 보지 못했습니다. 한번 실행해서 확인 부탁드립니다. (Spring 쪽은 가짜 AI 서버로 테스트 완료)
- `AiClientServer`에 `summarize()` 메서드를 추가했습니다.

  프론트(참고)
- 청중은 발표자에게 받은 입장 코드로 `POST /api/v1/sessions/participants`를 호출해 참여한 뒤 웹소켓을 구독해야 합니다.
- 참여 API 주소가 바뀌었습니다. POST /sessions/{sessionId}/participants는 삭제됐습니다. 이미 이걸로 화면을 만들었다면 404가 날 수도 있습니다......
+ 입장 코드가 null로 옵니다. 발표자가 아니면 entryCode가 null이라, 목록에서 코드를 보여 주던 화면이 있다면 빈칸이 됩니다.
++ 웹소켓은 토큰이 없으면 연결이 안 됩니다. 연결할 때 Authorization 헤더를 꼭 넣어야 합니다.
+++질문 등록이 진행 중일 때만 됩니다. 시작 전에 질문하면 409가 나니 입력창을 막아 둬야 합니다.
  아래는 연결 예시를 넣어놓겠습니다
  
- stompjs 연결 예시:
  ```js
  const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
  client.onConnect = () => client.subscribe(`/topic/sessions/${sessionId}`, (msg) => {
    const event = JSON.parse(msg.body);  // { eventId, type, sessionId, occurredAt, data }
  });
  client.activate();
  ```


==============================

## feature/question-realtime — 질문 실시간 알림

정헌님 웹소켓(`SessionTopicEvent`) 위에 질문·답변 알림을 연결한 브랜치입니다.

### 알림 종류

| 상황 | 알림 | `data` |
|---|---|---|
| 질문 등록 | `QUESTION_CREATED` | 질문 객체 (`aiStatus: PENDING`) |
| AI 답변 생성 시작 | `QUESTION_UPDATED` | 질문 객체 (`aiStatus: PROCESSING`) |
| AI 답변 완료 | `QUESTION_UPDATED` | 질문 객체 (`aiStatus: COMPLETED`, `answers`에 AI 답변 포함) |
| AI 답변 실패 | `QUESTION_UPDATED` | 질문 객체 (`aiStatus: FAILED`) |
| AI 재시도 | `QUESTION_UPDATED` | 질문 객체 (`aiStatus: PENDING`) |
| 질문 수정 | `QUESTION_UPDATED` | 질문 객체 |
| 질문 삭제 | `QUESTION_DELETED` | `{ "questionId": 1 }` |
| 발표자 답변 등록·수정·삭제 | `QUESTION_UPDATED` | 질문 객체 (바뀐 `answers` 포함) |

### 질문 객체 (`data`)

REST 질문 조회 응답(`QuestionResponse`)과 같은 형식입니다.

```json
{
  "id": 1,
  "sessionId": 3,
  "content": "어텐션은 왜 필요한가요?",
  "anonymous": false,
  "author": { "id": 7, "username": "민식" },
  "aiStatus": "COMPLETED",
  "answers": [
    { "id": 5, "questionId": 1, "content": "AI 답변 내용", "type": "AI", "author": null, "createdAt": "2026-10-01T22:30:05" }
  ],
  "createdAt": "2026-10-01T22:30:00",
  "updatedAt": "2026-10-01T22:30:05"
}
```

- 익명 질문은 알림에서 `author`가 항상 `null`입니다 (모든 구독자에게 같은 메시지가 가기 때문)
- AI 답변의 `author`는 항상 `null`입니다

### 프론트 참고

- 질문 하나에 알림이 여러 번 옵니다: `CREATED` → `UPDATED(PROCESSING)` → `UPDATED(COMPLETED 또는 FAILED)`
- `QUESTION_CREATED` / `QUESTION_UPDATED`: 같은 `id`가 없으면 추가, 있으면 **`updatedAt`이 더 최신일 때만 통째로 교체**. 순서가 뒤바뀌어 와도 오래된 내용이 덮어쓰지 않습니다
- `QUESTION_DELETED`: `data.questionId`에 해당하는 질문을 목록에서 제거
- 로컬에서 AI 서버를 안 켜면 AI 단계는 항상 `FAILED`로 끝납니다 (정상)

### 백엔드 변경

- `QuestionService`
  - `create()`: 질문 저장 직후 `QUESTION_CREATED` 발행 (AI 처리 시작보다 먼저 보내서 순서 보장)
  - `processAsync()`: AI 상태가 바뀔 때마다 `QUESTION_UPDATED` 발행, 실패 시 `log.warn` 추가 (기존엔 실패해도 로그가 없었음)
  - `update()`, `retry()`, `answer()`, `updateAnswer()`, `deleteAnswer()`: 처리 후 `QUESTION_UPDATED` 발행
  - `delete()`: 삭제 전 세션 번호를 챙겨두고, 삭제 후 `QUESTION_DELETED` 발행
  - `publishUpdated()` 추가: 비동기 스레드에서는 DB 연결이 닫혀 있어 작성자 정보를 못 읽는 문제가 있어, `TransactionTemplate`으로 조회 후 발행
- 권한 확인 등 기존 로직은 변경 없습니다

### 테스트

`src/test/.../domain/QuestionRealtimeTest.java` (H2 사용, DB 설치 불필요)

- 질문 등록 → `QUESTION_CREATED`, 익명이면 `author` 가려짐
- AI 처리 → `PROCESSING` → `FAILED` 순서로 `QUESTION_UPDATED` (익명 아닌 질문으로 작성자 정보 확인)
- 질문 삭제 → `QUESTION_DELETED`
- 발표자 답변 → 답변이 포함된 `QUESTION_UPDATED`
- 실패한 AI 재시도 → `PENDING` 알림

```
.\gradlew.bat test --tests "com.GDGoCSMU.ASKeep.domain.QuestionRealtimeTest"
```

### 논의 필요

- 질문 수정이 AI 처리 전(`PENDING`)에만 가능한데, AI가 등록 직후 바로 시작돼서 실제로는 수정이 거의 불가능합니다. 수정 기능을 빼거나, 수정 시 AI를 다시 돌리는 방식으로 수정이 필요함.