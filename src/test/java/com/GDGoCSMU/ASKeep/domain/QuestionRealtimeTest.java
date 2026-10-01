package com.GDGoCSMU.ASKeep.domain;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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