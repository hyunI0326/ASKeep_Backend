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
