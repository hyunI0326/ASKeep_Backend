package com.GDGoCSMU.ASKeep.domain;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 세션 종료 → AI 요약, 내 세션 기록 테스트.
 * 진짜 AI 서버 대신 같은 형식으로 응답하는 가짜 AI 서버를 띄운다. (H2라 Docker 불필요)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep-summary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SessionSummaryAndHistoryTest {

    static final AtomicBoolean aiFails = new AtomicBoolean(false);
    static final List<String> summaryRequests = new CopyOnWriteArrayList<>();
    static final HttpServer fakeAi = startFakeAi();

    @DynamicPropertySource
    static void aiServer(DynamicPropertyRegistry registry) {
        registry.add("ai.server.base-url", () -> "http://localhost:" + fakeAi.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAi() {
        fakeAi.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void 세션이_끝나면_AI_요약이_저장되고_실패하면_재시도할_수_있다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        String stranger = signupAndLogin();
        long sessionId = createSession(presenter, "JPA 스터디");
        join(audience, sessionId, presenter);
        String base = "/api/v1/sessions/" + sessionId;

        mvc.perform(post(base + "/start").header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
        mvc.perform(post(base + "/questions").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"N+1 문제가 뭔가요?\"}"))
                .andExpect(status().isAccepted());

        // 종료 전에는 요약 없음
        mvc.perform(get(base + "/summary").header("Authorization", "Bearer " + audience))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMARY_NOT_FOUND"));

        mvc.perform(post(base + "/end").header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());

        String done = waitForSummary(base, audience, "COMPLETED");
        assertEquals("JPA 스터디에서 N+1 문제를 다뤘습니다.", JsonPath.read(done, "$.data.summary"));
        assertEquals(List.of("JPA", "N+1"), JsonPath.read(done, "$.data.tags"));  // '#' 제거, 빈 태그·중복 제거
        String sent = summaryRequests.stream().filter(r -> r.contains("\"sessionId\":" + sessionId)).findFirst().orElseThrow();
        assertTrue(sent.contains("JPA 스터디") && sent.contains("N+1 문제가 뭔가요?"), sent);

        // 권한: 참여 안 한 사람은 조회 불가, 재시도는 발표자만 + FAILED일 때만
        mvc.perform(get(base + "/summary").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SESSION_PARTICIPANT"));
        mvc.perform(post(base + "/summary/retry").header("Authorization", "Bearer " + audience))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SESSION_PRESENTER"));
        mvc.perform(post(base + "/summary/retry").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMARY_RETRY_NOT_ALLOWED"));

        // AI 서버가 실패하면 FAILED → 발표자가 재시도하면 COMPLETED
        long second = createSession(presenter, "두 번째 세션");
        String base2 = "/api/v1/sessions/" + second;
        mvc.perform(post(base2 + "/start").header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
        aiFails.set(true);
        try {
            mvc.perform(post(base2 + "/end").header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
            waitForSummary(base2, presenter, "FAILED");
        } finally {
            aiFails.set(false);
        }
        mvc.perform(post(base2 + "/summary/retry").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isAccepted());
        waitForSummary(base2, presenter, "COMPLETED");

        // 요약이 있는 세션도 삭제된다 (요약이 세션을 참조해도 오류 없음)
        mvc.perform(delete(base2).header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
        mvc.perform(get(base2 + "/summary").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 내_세션_기록은_만든_세션과_참여한_세션을_보여준다() throws Exception {
        String alice = signupAndLogin();
        String bob = signupAndLogin();
        long aliceSession = createSession(alice, "앨리스 세션");
        long bobSession = createSession(bob, "밥 세션");
        join(alice, bobSession, bob);

        String all = mvc.perform(get("/api/v1/users/me/sessions").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, (int) JsonPath.read(all, "$.data.length()"));
        // 최신순: 밥 세션이 나중에 만들어짐
        assertEquals((int) bobSession, (int) JsonPath.read(all, "$.data[0].session.sessionId"));
        assertEquals("AUDIENCE", JsonPath.read(all, "$.data[0].myRole"));
        assertEquals((int) aliceSession, (int) JsonPath.read(all, "$.data[1].session.sessionId"));
        assertEquals("PRESENTER", JsonPath.read(all, "$.data[1].myRole"));
        assertNull(JsonPath.read(all, "$.data[1].summaryStatus"));  // 종료 전

        mvc.perform(get("/api/v1/users/me/sessions?role=PRESENTER").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].session.title").value("앨리스 세션"));
        mvc.perform(get("/api/v1/users/me/sessions?role=AUDIENCE").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].questionCount").value(0))
                .andExpect(jsonPath("$.data[0].tags").isEmpty())
                .andExpect(jsonPath("$.data[0].session.entryCode").isString())
                .andExpect(jsonPath("$.data[0].session.title").value("밥 세션"));
        mvc.perform(get("/api/v1/users/me/sessions?role=WRONG").header("Authorization", "Bearer " + alice))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me/sessions")).andExpect(status().isUnauthorized());

        // 종료하면 요약 상태가 함께 나온다
        mvc.perform(post("/api/v1/sessions/" + aliceSession + "/start").header("Authorization", "Bearer " + alice));
        mvc.perform(post("/api/v1/sessions/" + aliceSession + "/questions").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"태그와 질문 수 확인\",\"anonymous\":true}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/sessions/" + aliceSession + "/end").header("Authorization", "Bearer " + alice));
        waitForSummary("/api/v1/sessions/" + aliceSession, alice, "COMPLETED");
        mvc.perform(get("/api/v1/users/me/sessions?role=PRESENTER").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data[0].questionCount").value(1))
                .andExpect(jsonPath("$.data[0].tags").value(org.hamcrest.Matchers.contains("JPA", "N+1")))
                .andExpect(jsonPath("$.data[0].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].summaryStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/users/me/sessions").param("tag", "jpa").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].session.sessionId").value(aliceSession));
        mvc.perform(get("/api/v1/users/me/sessions").param("tag", "N+1").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/users/me/sessions").param("tag", "없는 태그").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/users/me/sessions").param("tag", " ").header("Authorization", "Bearer " + alice))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    /** 비동기 요약이 기대 상태가 될 때까지 최대 10초 기다린다 */
    private String waitForSummary(String base, String token, String expected) throws Exception {
        String last = null;
        for (int i = 0; i < 50; i++) {
            last = mvc.perform(get(base + "/summary").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (last.contains("\"status\":\"" + expected + "\"")) return last;
            Thread.sleep(200);
        }
        fail("요약 상태가 " + expected + "가 되지 않음: " + last);
        return last;
    }

    private String signupAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String name = "sum-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"%s\"}".formatted(email, name)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private long createSession(String token, String title) throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Integer) JsonPath.read(body, "$.data.sessionId")).longValue();
    }

    private void join(String token, long sessionId, String presenterToken) throws Exception {
        String session = mvc.perform(get("/api/v1/sessions/" + sessionId).header("Authorization", "Bearer " + presenterToken))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(session, "$.data.entryCode");
        mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"%s\"}".formatted(code)))
                .andExpect(status().isCreated());
    }

    /** 진짜 AI 서버와 같은 형식으로 응답하는 가짜 서버 (/sessions/summary, /ai/answer) */
    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/sessions/summary", exchange -> {
                String req = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                summaryRequests.add(req);
                if (aiFails.get()) {
                    respond(exchange, 500, "{\"detail\":\"gemini error\"}");
                    return;
                }
                Number id = JsonPath.read(req, "$.sessionId");
                respond(exchange, 200, """
                        {"sessionId":%d,"summary":"JPA 스터디에서 N+1 문제를 다뤘습니다.","tags":["#JPA"," N+1 ","",  "JPA"]}"""
                        .formatted(id.longValue()));
            });
            server.createContext("/ai/answer", exchange -> {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, 200, "{\"sessionId\":1,\"question\":\"q\",\"answer\":\"AI 답변입니다\",\"sources\":[]}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
