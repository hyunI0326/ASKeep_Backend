package com.GDGoCSMU.ASKeep.domain;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompCommand;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 웹소켓(STOMP) 연결·인증·세션 상태 알림 테스트. 실제 서버를 띄워서 연결한다.
 * H2 메모리 DB를 쓰므로 Docker 없이 돌아간다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep-ws;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WebSocketRealtimeTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void 토큰_없이는_연결할_수_없다() throws Exception {
        CompletableFuture<String> noTokenError = new CompletableFuture<>();
        CompletableFuture<StompSession> noToken = connect(null, errorCatcher(noTokenError));
        assertThrows(ExecutionException.class, () -> noToken.get(5, TimeUnit.SECONDS));
        assertTrue(noTokenError.get(5, TimeUnit.SECONDS).contains("로그인이 필요합니다"), noTokenError.get());

        CompletableFuture<String> wrongTokenError = new CompletableFuture<>();
        CompletableFuture<StompSession> wrongToken = connect("not-a-jwt", errorCatcher(wrongTokenError));
        assertThrows(ExecutionException.class, () -> wrongToken.get(5, TimeUnit.SECONDS));
        assertTrue(wrongTokenError.get(5, TimeUnit.SECONDS).contains("유효하지 않은 토큰"), wrongTokenError.get());
    }

    @Test
    void 참여자가_아니면_세션_알림을_구독할_수_없다() throws Exception {
        String presenter = signupAndLogin();
        String stranger = signupAndLogin();
        long sessionId = createSession(presenter);

        CompletableFuture<String> error = new CompletableFuture<>();
        StompSession session = connect(stranger, errorCatcher(error)).get(5, TimeUnit.SECONDS);
        session.subscribe("/topic/sessions/" + sessionId, ignoreFrames());

        // 서버가 ERROR 프레임을 보내고 연결을 끊는다
        assertTrue(error.get(5, TimeUnit.SECONDS).contains("참여자만"), error.get());
    }

    @Test
    void 참여자는_세션_시작과_종료_알림을_받는다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        String entryCode = JsonPath.read(get("/api/v1/sessions/" + sessionId, presenter), "$.data.entryCode");
        post("/api/v1/sessions/participants", audience, "{\"entryCode\":\"%s\"}".formatted(entryCode));

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        StompSession session = connect(audience, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        session.subscribe("/topic/sessions/" + sessionId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(500);  // 구독이 서버에 등록될 시간

        post("/api/v1/sessions/" + sessionId + "/start", presenter, null);
        String started = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(started, "세션 시작 알림을 받지 못했습니다");
        assertEquals("SESSION_STATUS_CHANGED", JsonPath.read(started, "$.type"));
        assertEquals((int) sessionId, (int) JsonPath.read(started, "$.sessionId"));
        assertEquals("ONGOING", JsonPath.read(started, "$.data.status"));
        assertNotNull(JsonPath.read(started, "$.eventId"));
        assertInstanceOf(String.class, JsonPath.read(started, "$.occurredAt"));  // "2026-10-01T13:30:00" 형식

        post("/api/v1/sessions/" + sessionId + "/end", presenter, null);
        String ended = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(ended, "세션 종료 알림을 받지 못했습니다");
        assertEquals("ENDED", JsonPath.read(ended, "$.data.status"));
    }

    // --- helpers ---

    private CompletableFuture<StompSession> connect(String token, StompSessionHandlerAdapter handler) {
        StompHeaders headers = new StompHeaders();
        if (token != null) headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", (WebSocketHttpHeaders) null, headers, handler);
    }

    private StompSessionHandlerAdapter errorCatcher(CompletableFuture<String> error) {
        return new StompSessionHandlerAdapter() {
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                error.complete("ERROR frame: " + headers.getFirst("message"));
            }
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleTransportError(StompSession s, Throwable e) {
                error.complete("transport closed: " + e.getMessage());
            }
            @Override public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable e) {
                error.complete("exception: " + e.getMessage());
            }
        };
    }

    private StompFrameHandler ignoreFrames() {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {}
        };
    }

    private String signupAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String name = "ws-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/users/auth/signup", null,
                "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"%s\"}".formatted(email, name));
        String body = post("/api/v1/users/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email));
        return JsonPath.read(body, "$.data.accessToken");
    }

    private long createSession(String token) throws Exception {
        Integer id = JsonPath.read(post("/api/v1/sessions", token, "{\"title\":\"웹소켓 세션\"}"), "$.data.sessionId");
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
