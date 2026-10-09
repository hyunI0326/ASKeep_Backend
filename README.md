## 한 서버에서 실행

Docker Compose로 Spring Boot, FastAPI, PostgreSQL을 한 서버에서 실행합니다.
프로젝트 루트의 `.env`를 사용하며, 기존 `ai-server/.env`는 컨테이너에 복사하지 않습니다.

```bash
# 기존 .env가 있으면 그대로 사용합니다.
test -f .env || cp .env.example .env
```

`.env`에 `GEMINI_API_KEY`와 `JWT_SECRET`을 입력하세요. `JWT_SECRET`은
`openssl rand -hex 32`로 생성할 수 있습니다. `DB_PASSWORD`는 세 서비스에
동일하게 적용됩니다. 기존 DB 볼륨이 있으면 생성 당시 비밀번호를 유지해야 합니다.
배포 시 `CORS_ALLOWED_ORIGINS`에 실제 프론트 주소를 지정하세요.

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f backend ai
```

- 프론트 API 주소: `http://서버주소:8080/api/v1`, 웹소켓: `ws://서버주소:8080/ws`
- Spring → FastAPI: `http://ai:8000`, 두 서비스 → DB: `postgres:5432/askeep`
- FastAPI의 8000 포트는 호스트에 공개하지 않습니다. DB의 5432 포트는 호스트의 `127.0.0.1`에서만 접근 가능합니다.
- Spring은 PDF를 `uploads` 볼륨의 `/uploads`에 저장하고 FastAPI로 파일 자체를 multipart 전송합니다. FastAPI와 업로드 폴더를 공유할 필요가 없습니다.
- DB 준비 후 FastAPI를 시작하고, FastAPI의 `/health` 응답 후 Spring을 시작합니다.
- 첫 빌드는 임베딩 모델 다운로드 때문에 시간이 걸립니다. 모델은 AI 이미지에 포함되어 시작 시 다시 다운로드하지 않습니다.

기존 DB에 `material_chunks`가 없다면 데이터를 삭제하지 않고 초기화 SQL을 적용하세요.

```bash
docker compose exec -T postgres psql -U postgres -d askeep -v ON_ERROR_STOP=1 < db/init.sql
```

로그인 → 세션 생성 → PDF 업로드 및 `COMPLETED` 확인 → 세션 시작 → 질문 등록 →
AI 답변 확인 → 세션 종료 → 요약 조회 순서로 실제 연동을 확인합니다.
`/health`만으로 Gemini 호출이나 DB 저장 성공을 확인할 수는 없습니다.
기존 로컬 개발에서 저장한 자료의 절대경로는 새 컨테이너 경로와 다르므로, 검증용 PDF는 새로 업로드하세요.

설정 검증(실제 키나 Docker 데몬 불필요): `python3 scripts/check_compose.py`

## Render에서 Spring과 FastAPI를 별도로 실행

FastAPI 서비스는 Root Directory `ai-server`, Dockerfile Path `./Dockerfile`로 배포합니다.
Spring의 `AI_SERVER_BASE_URL`에는 실제 FastAPI 서비스 주소를 설정하고,
FastAPI의 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`는 Spring과 같은 DB를 가리키게 설정합니다.
`GEMINI_API_KEY`는 FastAPI 서비스에 설정합니다. AI API에는 별도 인증이 없으므로 같은 Render 워크스페이스와 지역의 Private Service로 연결합니다.

자료 처리는 `POST /documents/process`에 multipart 필드 `materialId`, `sessionId`, `file`을 보냅니다.
JSON `filePath` 요청은 더 이상 받지 않습니다. Spring과 FastAPI를 모두 새 코드로 빌드·배포해야 합니다.
FastAPI는 받은 PDF를 처리 후 폐기하고 청크·임베딩만 DB에 저장합니다.
Spring에는 재시도용 원본 PDF가 필요하므로, 재시작 후에도 재시도하려면 Spring의 업로드 폴더에 영속 저장소가 필요합니다.

임베딩은 PyTorch 대신 ONNX Runtime과 SentencePiece로 같은 multilingual-e5-small의 INT8 파일을 실행합니다.
384차원, mean pooling, 정규화와 `passage:`/`query:` 접두사를 유지합니다.
512MiB 환경을 위해 임베딩을 한 번에 한 문서씩 순차 처리합니다.
로컬 메모리 회귀 확인: `GEMINI_API_KEY=memory-check-not-a-real-key ai-server/.venv/bin/python ai-server/check_embedding_memory.py`
이 검사는 시작 및 샘플 임베딩의 최대 RSS가 512MiB 미만인지 확인하며, Render Linux에서의 실측은 별도로 확인해야 합니다.

## 질문·세션 체크리스트 기능

- 답변 완료: 발표자가 `PUT /api/v1/questions/{id}/answered`로 설정하고 `DELETE`로 취소합니다.
- 나도 궁금해요: 세션 멤버가 `PUT /api/v1/questions/{id}/like`로 설정하고 `DELETE`로 취소합니다.
- 질문 목록: `sort=latest`(기본) 또는 `sort=popular`(공감 수 내림차순, 동률은 최신순).
- 직접 묻기 요청: 진행 중 세션의 질문 작성자가 `POST /api/v1/questions/{id}/presenter-request`로 요청합니다.
- 질문 REST 응답에 `answered`, `answeredAt`, `presenterRequested`, `presenterRequestedAt`, `likeCount`, `likedByMe`를 추가했습니다. 기존 `mine`은 익명 질문에도 제공합니다.
- 상태 변경은 기존 `QUESTION_UPDATED` 알림으로 전달합니다. 알림에서 `mine`, `likedByMe`는 빠지므로 프론트는 기존 개인별 값을 유지하거나 다시 조회하세요.
- 입장 코드는 발표자와 참여한 청자에게 제공하고 미참여자에게는 숨깁니다.
- `GET /api/v1/users/me/sessions`에 `tags`, `questionCount`, `joinedAt`을 추가했습니다. `?tag=JPA`로 태그를 필터링할 수 있습니다.

Spring 재배포가 필요합니다. 운영 DB 스키마를 수동 관리한다면 **Spring이 사용하는 DB**에 `db/question-engagement.sql`을 먼저 적용하세요.
현재 기본 `ddl-auto=update`도 새 필드와 공감 테이블을 생성합니다. 기존 청자의 입장 시각은 기록이 없어 `null`일 수 있습니다.
프론트 UI 연동과 실제 개발 서버 배포는 별도 작업입니다. 요청·응답 예시는 `api_secp.md`에 정리했습니다.

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
+ 입장 코드는 발표자와 이미 참여한 청자에게 제공됩니다. 미참여자에게는 null입니다.
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


==============================

## feature/question-visibility — 익명 질문 작성자 공개 범위, mine

### REST 질문 응답 변경

- `author`: 익명 질문이면 **발표자와 작성자 본인에게만** 보이고, 다른 참여자에게는 `null`
- `mine` 추가: 요청한 사람이 작성자인지 (`true` / `false`). 수정·삭제 버튼 표시용

| 질문 | 발표자 | 작성자 본인 | 다른 참여자 |
|---|---|---|---|
| 익명 아님 | 표시 | 표시 | 표시 |
| 익명 | 표시 | 표시 | `null` |

### 웹소켓 알림은 그대로

- 익명 질문의 `author`는 누구에게나 `null` (모든 구독자에게 같은 메시지가 가기 때문)
- `mine`은 알림에 **포함되지 않습니다.** 알림으로 질문을 교체할 때 **기존 `mine` 값은 유지**해주세요
- 발표자가 익명 질문 작성자를 봐야 하면 `GET /api/v1/questions/{id}` 조회

### 백엔드 변경

- `QuestionResponse`를 용도별로 분리: `forViewer(...)` (REST용), `forBroadcast(...)` (알림용)
- `QuestionController`는 `forViewer`, `QuestionService`의 알림은 `forBroadcast` 사용

### 테스트

- 같은 익명 질문을 발표자·작성자·다른 참여자가 조회했을 때 `author`, `mine` 확인
- 알림에 `mine`이 없는지 확인


==============================

## feature/ai-answer-sources — AI 답변 출처

AI 서버가 이미 보내주던 출처(어느 자료의 몇 페이지를 참고했는지)를 저장해서 답변에 같이 내려줍니다.

### 답변 객체에 추가된 필드

```json
{
  "id": 41,
  "questionId": 23,
  "content": "Pod는 컨테이너 묶음입니다...",
  "type": "AI",
  "author": null,
  "sources": [
    { "materialId": 4, "fileName": "CP_L4_2(Docker_K8s).pdf", "pageNumber": 3, "similarity": 0.82 },
    { "materialId": 4, "fileName": "CP_L4_2(Docker_K8s).pdf", "pageNumber": 5, "similarity": 0.61 }
  ],
  "createdAt": "2026-10-09T17:20:05"
}
```

| 필드 | 설명 |
|---|---|
| `materialId` | 자료 번호 |
| `fileName` | 자료 파일 이름 (자료가 삭제돼도 남음) |
| `pageNumber` | 페이지 번호 (1부터) |
| `similarity` | 질문과의 관련도 (0~1, 클수록 관련 높음) |

- 질문 응답의 `answers[]`, 답변 목록 API, 웹소켓 알림 모두 같은 형식입니다
- 관련도 높은 순으로 정렬됩니다
- AI는 자료 조각을 최대 5개 참고하는데, 같은 자료의 같은 페이지는 하나로 합칩니다 (관련도는 가장 높은 값)
- 발표자 답변과 이 기능 이전에 저장된 AI 답변은 `sources: []`입니다

### 백엔드 변경

- `AnswerSource` 엔티티 추가 (`answer_sources` 테이블, 서버 시작 시 자동 생성)
- `Answer`에 `sources` 목록 추가 (답변 저장·삭제 시 출처도 함께 저장·삭제)
- `AiAnswerResponse`: `sources`를 `List<Object>`에서 형식이 있는 `Source`로 변경
- `QuestionService.processAsync()`: AI 답변 저장 시 출처를 정리해서 함께 저장 (`addSources()`)
- `AnswerResponse`에 `sources` 추가

### 테스트

`AnswerSourceTest` (가짜 AI 서버 사용, H2)

- 같은 페이지 출처가 합쳐지는지, 관련도·파일 이름·정렬 확인
- 발표자 답변의 `sources`가 빈 목록인지

```
.\gradlew.bat test --tests "com.GDGoCSMU.ASKeep.domain.AnswerSourceTest"
```