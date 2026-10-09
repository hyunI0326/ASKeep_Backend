package com.GDGoCSMU.ASKeep.domain;

import java.util.List;
import java.util.function.Predicate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerResponse;
import java.util.concurrent.CountDownLatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 질문 실시간 알림 테스트.
 * WebSocketRealtimeTest와 같은 방식으로 실제 서버를 띄우고 H2 메모리 DB를 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep-question-ws;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class QuestionRealtimeTest {
    @MockitoBean
    AiClientServer aiClient;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void 질문을_등록하면_참여자에게_QUESTION_CREATED_알림이_간다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        String entryCode = JsonPath.read(get("/api/v1/sessions/" + sessionId, presenter), "$.data.entryCode");
        post("/api/v1/sessions/participants", audience, "{\"entryCode\":\"%s\"}".formatted(entryCode));
        post("/api/v1/sessions/" + sessionId + "/start", presenter, null);

        BlockingQueue<String> received = subscribe(audience, sessionId);

        post("/api/v1/sessions/" + sessionId + "/questions", audience,
                "{\"content\":\"BPE가 뭔가요?\",\"anonymous\":true}");

        String message = waitFor(received, "QUESTION_CREATED");
        assertEquals((int) sessionId, (int) JsonPath.read(message, "$.sessionId"));

        Map<String, Object> data = JsonPath.read(message, "$.data");
        assertEquals("BPE가 뭔가요?", data.get("content"));
        assertEquals(true, data.get("anonymous"));
        assertNull(data.get("author"), "익명 질문의 작성자는 알림에서 가려져야 합니다");
        assertNotNull(data.get("id"));
        assertFalse(data.containsKey("mine"), "알림에는 mine이 없어야 합니다");
    }

    @Test
    void AI_처리_상태가_바뀌면_QUESTION_UPDATED_알림이_간다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        String entryCode = JsonPath.read(get("/api/v1/sessions/" + sessionId, presenter), "$.data.entryCode");
        post("/api/v1/sessions/participants", audience, "{\"entryCode\":\"%s\"}".formatted(entryCode));
        post("/api/v1/sessions/" + sessionId + "/start", presenter, null);

        BlockingQueue<String> received = subscribe(audience, sessionId);

        // 익명이 아닌 질문: 비동기 스레드에서도 작성자 정보를 문제없이 읽는지 함께 확인
        post("/api/v1/sessions/" + sessionId + "/questions", audience,
                "{\"content\":\"어텐션은 왜 필요한가요?\",\"anonymous\":false}");

        // 테스트에는 AI 서버가 없으므로 PROCESSING → FAILED 순서로 알림이 와야 한다
        assertNotNull(waitForAiStatus(received, "PROCESSING"));
        String failed = waitForAiStatus(received, "FAILED");

        Map<String, Object> data = JsonPath.read(failed, "$.data");
        assertEquals("어텐션은 왜 필요한가요?", data.get("content"));
        assertNotNull(data.get("author"), "익명이 아닌 질문은 작성자가 보여야 합니다");
    }

        @Test
    void 질문을_삭제하면_QUESTION_DELETED_알림이_간다() throws Exception {
        LiveSession live = startLiveSession();
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        int questionId = postQuestion(live, "지울 질문입니다");

        delete("/api/v1/questions/" + questionId, live.audience());

        String message = waitUntil(received,
                m -> "QUESTION_DELETED".equals(JsonPath.read(m, "$.type")), "QUESTION_DELETED");
        assertEquals(questionId, (int) JsonPath.read(message, "$.data.questionId"));
    }

    @Test
    void 발표자가_답변하면_답변이_포함된_QUESTION_UPDATED_알림이_간다() throws Exception {
        LiveSession live = startLiveSession();
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        int questionId = postQuestion(live, "발표자님 의견이 궁금합니다");

        post("/api/v1/questions/" + questionId + "/answers", live.presenter(), "{\"content\":\"발표자 답변입니다\"}");

        String message = waitUntil(received,
                m -> "QUESTION_UPDATED".equals(JsonPath.read(m, "$.type"))
                        && !((List<?>) JsonPath.read(m, "$.data.answers[?(@.type == 'PRESENTER')]")).isEmpty(),
                "발표자 답변이 포함된 QUESTION_UPDATED");
        assertEquals(questionId, (int) JsonPath.read(message, "$.data.id"));
    }

    @Test
    void 실패한_AI_답변을_재시도하면_PENDING_알림이_간다() throws Exception {
        LiveSession live = startLiveSession();
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        int questionId = postQuestion(live, "재시도할 질문입니다");
        waitForAiStatus(received, "FAILED");  // 테스트엔 AI 서버가 없어서 먼저 실패한다

        post("/api/v1/questions/" + questionId + "/ai-answer/retry", live.presenter(), null);

        String message = waitForAiStatus(received, "PENDING");
        assertEquals(questionId, (int) JsonPath.read(message, "$.data.id"));
    }

    @Test
    void 익명_질문_작성자는_발표자와_본인에게만_보이고_mine으로_내_질문을_구분한다() throws Exception {
        String presenter = signupAndLogin();
        String author = signupAndLogin();
        String other = signupAndLogin();
        long sessionId = createSession(presenter);
        String entryCode = JsonPath.read(get("/api/v1/sessions/" + sessionId, presenter), "$.data.entryCode");
        post("/api/v1/sessions/participants", author, "{\"entryCode\":\"%s\"}".formatted(entryCode));
        post("/api/v1/sessions/participants", other, "{\"entryCode\":\"%s\"}".formatted(entryCode));
        post("/api/v1/sessions/" + sessionId + "/start", presenter, null);

        int questionId = JsonPath.read(post("/api/v1/sessions/" + sessionId + "/questions", author,
                "{\"content\":\"익명 질문입니다\",\"anonymous\":true}"), "$.data.id");
        String path = "/api/v1/questions/" + questionId;

        Map<String, Object> byPresenter = JsonPath.read(get(path, presenter), "$.data");
        Map<String, Object> byAuthor = JsonPath.read(get(path, author), "$.data");
        Map<String, Object> byOther = JsonPath.read(get(path, other), "$.data");

        assertNotNull(byPresenter.get("author"), "발표자는 익명 질문 작성자를 볼 수 있어야 합니다");
        assertEquals(false, byPresenter.get("mine"));

        assertNotNull(byAuthor.get("author"), "작성자 본인은 자기 이름을 볼 수 있어야 합니다");
        assertEquals(true, byAuthor.get("mine"));

        assertNull(byOther.get("author"), "다른 참여자에게는 익명 질문 작성자가 가려져야 합니다");
        assertEquals(false, byOther.get("mine"));
    }

    @Test
    void 좋아요는_중복되지_않고_인기순_정렬과_실시간_수에_반영된다() throws Exception {
        LiveSession live = startLiveSession();
        String stranger = signupAndLogin();
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        int older = JsonPath.read(post("/api/v1/sessions/" + live.sessionId() + "/questions", live.audience(),
                "{\"content\":\"익명 공감 질문\",\"anonymous\":true}"), "$.data.id");
        int newer = postQuestion(live, "새 질문");
        String path = "/api/v1/questions/" + older;
        for (int i = 0; i < 2; i++) {
            String liked = send(HttpRequest.newBuilder(uri(path + "/like"))
                    .header("Authorization", "Bearer " + live.audience()).PUT(HttpRequest.BodyPublishers.noBody()));
            assertEquals(1, (int) JsonPath.read(liked, "$.data.likeCount"));
            assertEquals(true, JsonPath.read(liked, "$.data.likedByMe"));
            assertEquals(true, JsonPath.read(liked, "$.data.mine"));
        }
        Map<String, Object> data = JsonPath.read(waitUntil(received,
                m -> "QUESTION_UPDATED".equals(JsonPath.read(m, "$.type"))
                        && Integer.valueOf(older).equals(JsonPath.read(m, "$.data.id"))
                        && Integer.valueOf(1).equals(JsonPath.read(m, "$.data.likeCount")), "좋아요 알림"), "$.data");
        assertNull(data.get("author"));
        assertFalse(data.containsKey("mine"));
        assertFalse(data.containsKey("likedByMe"));
        assertEquals(false, JsonPath.read(get(path, live.presenter()), "$.data.likedByMe"));
        assertEquals(older, (int) JsonPath.read(get("/api/v1/sessions/" + live.sessionId() + "/questions?sort=popular", live.audience()), "$.data.items[0].id"));
        assertEquals(newer, (int) JsonPath.read(get("/api/v1/sessions/" + live.sessionId() + "/questions", live.audience()), "$.data.items[0].id"));
        assertEquals(403, http.send(HttpRequest.newBuilder(uri(path + "/like"))
                .header("Authorization", "Bearer " + stranger).PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        for (int i = 0; i < 2; i++) {
            String unliked = delete(path + "/like", live.audience());
            assertEquals(0, (int) JsonPath.read(unliked, "$.data.likeCount"));
            assertEquals(false, JsonPath.read(unliked, "$.data.likedByMe"));
        }
        waitUntil(received, m -> "QUESTION_UPDATED".equals(JsonPath.read(m, "$.type"))
                && Integer.valueOf(older).equals(JsonPath.read(m, "$.data.id"))
                && Integer.valueOf(0).equals(JsonPath.read(m, "$.data.likeCount")), "좋아요 취소 알림");
        assertEquals(newer, (int) JsonPath.read(get("/api/v1/sessions/" + live.sessionId() + "/questions?sort=popular", live.audience()), "$.data.items[0].id"));
        assertEquals(400, http.send(HttpRequest.newBuilder(uri("/api/v1/sessions/" + live.sessionId() + "/questions?sort=popular&afterId=0"))
                .header("Authorization", "Bearer " + live.audience()).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, http.send(HttpRequest.newBuilder(uri(path + "/answered"))
                .header("Origin", "https://askeep.vercel.app").header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void 완료표시는_발표자가_설정취소하고_직접요청은_작성자가_한다() throws Exception {
        LiveSession live = startLiveSession();
        int id = postQuestion(live, "직접 답변을 듣고 싶은 질문");
        String path = "/api/v1/questions/" + id;
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        assertEquals(403, http.send(HttpRequest.newBuilder(uri(path + "/answered"))
                .header("Authorization", "Bearer " + live.audience()).PUT(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        String completed = send(HttpRequest.newBuilder(uri(path + "/answered"))
                .header("Authorization", "Bearer " + live.presenter()).PUT(HttpRequest.BodyPublishers.noBody()));
        String answeredAt = JsonPath.read(completed, "$.data.answeredAt");
        assertNotNull(answeredAt);
        assertEquals(true, JsonPath.read(completed, "$.data.answered"));
        waitUntil(received, m -> "QUESTION_UPDATED".equals(JsonPath.read(m, "$.type"))
                && Boolean.TRUE.equals(JsonPath.read(m, "$.data.answered")), "답변 완료 알림");
        assertEquals(answeredAt, JsonPath.read(send(HttpRequest.newBuilder(uri(path + "/answered"))
                .header("Authorization", "Bearer " + live.presenter()).PUT(HttpRequest.BodyPublishers.noBody())), "$.data.answeredAt"));
        String cancelled = delete(path + "/answered", live.presenter());
        assertEquals(false, JsonPath.read(cancelled, "$.data.answered"));
        assertNull(JsonPath.read(cancelled, "$.data.answeredAt"));
        assertEquals(403, http.send(HttpRequest.newBuilder(uri(path + "/presenter-request"))
                .header("Authorization", "Bearer " + live.presenter()).POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        String requested = post(path + "/presenter-request", live.audience(), null);
        String requestedAt = JsonPath.read(requested, "$.data.presenterRequestedAt");
        assertNotNull(requestedAt);
        assertEquals(true, JsonPath.read(requested, "$.data.presenterRequested"));
        assertEquals(requestedAt, JsonPath.read(post(path + "/presenter-request", live.audience(), null), "$.data.presenterRequestedAt"));
        waitUntil(received, m -> "QUESTION_UPDATED".equals(JsonPath.read(m, "$.type"))
                && Boolean.TRUE.equals(JsonPath.read(m, "$.data.presenterRequested")), "직접 요청 알림");
        post("/api/v1/sessions/" + live.sessionId() + "/end", live.presenter(), null);
        assertEquals(409, http.send(HttpRequest.newBuilder(uri(path + "/presenter-request"))
                .header("Authorization", "Bearer " + live.audience()).POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void AI_답변이_늦게_완료돼도_완료표시와_직접요청과_좋아요는_유지된다() throws Exception {
        LiveSession live = startLiveSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiClient.answer(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new AiAnswerResponse(live.sessionId(), "질문", "AI 답변", java.util.List.of());
        });
        BlockingQueue<String> received = subscribe(live.audience(), live.sessionId());
        int id = postQuestion(live, "처리 중에도 상태가 유지되는 질문");
        String path = "/api/v1/questions/" + id;
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            send(HttpRequest.newBuilder(uri(path + "/answered")).header("Authorization", "Bearer " + live.presenter())
                    .PUT(HttpRequest.BodyPublishers.noBody()));
            post(path + "/presenter-request", live.audience(), null);
            send(HttpRequest.newBuilder(uri(path + "/like")).header("Authorization", "Bearer " + live.audience())
                    .PUT(HttpRequest.BodyPublishers.noBody()));
        } finally {
            release.countDown();
        }
        waitForAiStatus(received, "COMPLETED");
        String response = get(path, live.audience());
        assertEquals(true, JsonPath.read(response, "$.data.answered"));
        assertEquals(true, JsonPath.read(response, "$.data.presenterRequested"));
        assertEquals(1, (int) JsonPath.read(response, "$.data.likeCount"));
        assertEquals(true, JsonPath.read(response, "$.data.likedByMe"));
    }

    // --- helpers ---

    /** 웹소켓 연결 후 세션 주소를 구독하고, 받은 알림을 쌓아두는 큐를 돌려준다. */
    private BlockingQueue<String> subscribe(String token, long sessionId) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws",
                (WebSocketHttpHeaders) null, headers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        session.subscribe("/topic/sessions/" + sessionId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(500);  // 구독이 서버에 등록될 시간
        return received;
    }

    /** 원하는 종류의 알림이 올 때까지 최대 5초 기다린다. 다른 종류는 건너뛴다. */
    private String waitFor(BlockingQueue<String> received, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String message = received.poll(500, TimeUnit.MILLISECONDS);
            if (message != null && type.equals(JsonPath.read(message, "$.type"))) return message;
        }
        fail(type + " 알림을 받지 못했습니다");
        return null;
    }

    /** QUESTION_UPDATED 중 aiStatus가 원하는 값인 알림이 올 때까지 최대 5초 기다린다. */
    private String waitForAiStatus(BlockingQueue<String> received, String aiStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String message = received.poll(500, TimeUnit.MILLISECONDS);
            if (message == null) continue;
            if ("QUESTION_UPDATED".equals(JsonPath.read(message, "$.type"))
                    && aiStatus.equals(JsonPath.read(message, "$.data.aiStatus"))) return message;
        }
        fail("aiStatus=" + aiStatus + " 알림을 받지 못했습니다");
        return null;
    }

    private record LiveSession(String presenter, String audience, long sessionId) {}

    /** 발표자·청자를 만들고, 청자가 입장 코드로 참여한 뒤 세션을 시작한 상태를 돌려준다. */
    private LiveSession startLiveSession() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        String entryCode = JsonPath.read(get("/api/v1/sessions/" + sessionId, presenter), "$.data.entryCode");
        post("/api/v1/sessions/participants", audience, "{\"entryCode\":\"%s\"}".formatted(entryCode));
        post("/api/v1/sessions/" + sessionId + "/start", presenter, null);
        return new LiveSession(presenter, audience, sessionId);
    }

    /** 청자가 질문을 등록하고 질문 번호를 돌려준다. */
    private int postQuestion(LiveSession live, String content) throws Exception {
        return JsonPath.read(post("/api/v1/sessions/" + live.sessionId() + "/questions", live.audience(),
                "{\"content\":\"%s\",\"anonymous\":false}".formatted(content)), "$.data.id");
    }

    /** 조건에 맞는 알림이 올 때까지 최대 5초 기다린다. */
    private String waitUntil(BlockingQueue<String> received, Predicate<String> condition, String description) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String message = received.poll(500, TimeUnit.MILLISECONDS);
            if (message != null && condition.test(message)) return message;
        }
        fail(description + " 알림을 받지 못했습니다");
        return null;
    }

    private String delete(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).DELETE());
    }

    private String signupAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String name = "q-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/users/auth/signup", null,
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"%s\"}".formatted(email, name));
        String body = post("/api/v1/users/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email));
        return JsonPath.read(body, "$.data.accessToken");
    }

    private long createSession(String token) throws Exception {
        Integer id = JsonPath.read(post("/api/v1/sessions", token, "{\"title\":\"질문 알림 세션\"}"), "$.data.sessionId");
        return id;
    }

    private String get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).GET());
    }

    private String post(String path, String token, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return send(b);
    }

    private String send(HttpRequest.Builder builder) throws Exception {
        HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(res.statusCode() < 300, "HTTP " + res.statusCode() + ": " + res.body());
        return res.body();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
