package com.GDGoCSMU.ASKeep.domain;

import com.GDGoCSMU.ASKeep.domain.material.MaterialRepository;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 답변 출처 테스트.
 * 진짜 AI 서버 대신, 같은 형식으로 출처(sources)를 돌려주는 가짜 AI 서버를 띄운다. (H2라 DB 설치 불필요)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep-answer-source;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AnswerSourceTest {

    /** 가짜 AI 서버가 출처로 돌려줄 자료 번호 (테스트에서 업로드한 자료) */
    static final AtomicLong sourceMaterialId = new AtomicLong();
    static final AtomicBoolean imageFails = new AtomicBoolean();
    static final AtomicInteger pageCountCalls = new AtomicInteger();
    static final AtomicReference<String> renderRequest = new AtomicReference<>();
    static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx1kAAAAASUVORK5CYII=");
    static final HttpServer fakeAi = startFakeAi();
    static final String uploadDir = tempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ai.server.base-url", () -> "http://localhost:" + fakeAi.getAddress().getPort());
        registry.add("askeep.upload-dir", () -> uploadDir);
    }

    @AfterAll
    static void stopFakeAi() {
        fakeAi.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MaterialRepository materialRepository;

    @Test
    void 슬라이드_페이지_조회는_참여자만_가능하고_기존_자료도_지원한다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        String stranger = signupAndLogin();
        long sessionId = createSession(presenter);
        long materialId = uploadPdf(presenter, sessionId, "slides.pdf");
        join(audience, sessionId, presenter);
        String base = "/api/v1/materials/" + materialId;
        boolean completed = false;
        for (int i = 0; i < 50; i++) {
            var material = materialRepository.findById(materialId).orElseThrow();
            if (material.getStatus().name().equals("COMPLETED")) {
                assertEquals(5, material.getPageCount());
                completed = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(completed, "자료 처리가 완료돼야 합니다.");
        mvc.perform(get(base).header("Authorization", "Bearer " + presenter))
                .andExpect(jsonPath("$.data.pageCount").value(5));
        mvc.perform(get(base + "/pages").header("Authorization", "Bearer " + audience))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageCount").value(5))
                .andExpect(jsonPath("$.data.pages.length()").value(5))
                .andExpect(jsonPath("$.data.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.data.pages[4].imageUrl").value(base + "/pages/5/image"));
        mvc.perform(get(base + "/pages/2/image").header("Authorization", "Bearer " + audience))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
        assertTrue(renderRequest.get().contains("name=\"pageNumber\""));
        assertTrue(renderRequest.get().contains("\r\n\r\n2\r\n"));
        assertTrue(renderRequest.get().contains("%PDF-1.7"));
        for (String route : List.of("/pages", "/pages/1/image")) {
            mvc.perform(get(base + route)).andExpect(status().isUnauthorized());
            mvc.perform(get(base + route).header("Authorization", "Bearer " + stranger)).andExpect(status().isForbidden());
        }
        mvc.perform(get(base + "/pages/0/image").header("Authorization", "Bearer " + audience)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "/pages/6/image").header("Authorization", "Bearer " + audience)).andExpect(status().isNotFound());
        imageFails.set(true);
        try {
            mvc.perform(get(base + "/pages/1/image").header("Authorization", "Bearer " + presenter))
                    .andExpect(status().isBadGateway());
        } finally {
            imageFails.set(false);
        }
        // 페이지 수가 없는 기존 자료·텍스트 분석 실패 자료도 원본 PDF로 조회한다.
        var legacy = materialRepository.findById(materialId).orElseThrow();
        legacy.setPageCount(null);
        legacy.failProcessing();
        materialRepository.save(legacy);
        int calls = pageCountCalls.get();
        for (int i = 0; i < 2; i++) {
            mvc.perform(get(base + "/pages").header("Authorization", "Bearer " + presenter))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageCount").value(5));
        }
        assertEquals(calls + 1, pageCountCalls.get(), "페이지 수는 저장 후 재사용해야 합니다.");
        mvc.perform(get(base + "/pages/1/image").header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
        Files.delete(Path.of(legacy.getFilePath()));
        mvc.perform(get(base + "/pages/1/image").header("Authorization", "Bearer " + presenter)).andExpect(status().isGone());
        mvc.perform(delete(base).header("Authorization", "Bearer " + presenter)).andExpect(status().isOk());
        mvc.perform(get(base + "/pages").header("Authorization", "Bearer " + presenter)).andExpect(status().isNotFound());
    }

    @Test
    void AI_답변에_참고한_자료와_페이지가_출처로_붙는다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        long materialId = uploadPdf(presenter, sessionId, "slides.pdf");
        sourceMaterialId.set(materialId);
        join(audience, sessionId, presenter);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/start").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk());

        int questionId = postQuestion(audience, sessionId, "쿠버네티스 Pod가 뭔가요?");
        String question = waitForAiStatus(questionId, audience, "COMPLETED");

        // 가짜 AI는 3페이지 조각 2개 + 5페이지 조각 1개를 돌려준다 → 3페이지는 하나로 합쳐져 2개
        List<Map<String, Object>> sources = JsonPath.read(question, "$.data.answers[0].sources");
        assertEquals(2, sources.size(), "같은 자료의 같은 페이지는 하나로 합쳐야 합니다");

        Map<String, Object> first = sources.get(0);
        assertEquals((int) materialId, first.get("materialId"));
        assertEquals("slides.pdf", first.get("fileName"));
        assertEquals(3, first.get("pageNumber"));
        assertEquals(0.82, (Double) first.get("similarity"), 0.0001, "합칠 때는 가장 높은 관련도를 써야 합니다");

        assertEquals(5, sources.get(1).get("pageNumber"), "관련도 높은 순으로 정렬돼야 합니다");
    }

    @Test
    void 발표자_답변의_출처는_빈_목록이다() throws Exception {
        String presenter = signupAndLogin();
        String audience = signupAndLogin();
        long sessionId = createSession(presenter);
        join(audience, sessionId, presenter);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/start").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk());
        int questionId = postQuestion(audience, sessionId, "발표자님께 드리는 질문입니다");

        String answer = mvc.perform(post("/api/v1/questions/" + questionId + "/answers")
                        .header("Authorization", "Bearer " + presenter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"발표자 답변입니다\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<?> sources = JsonPath.read(answer, "$.data.sources");
        assertTrue(sources.isEmpty());
    }

    // --- helpers ---

    private String waitForAiStatus(int questionId, String token, String expected) throws Exception {
        String last = null;
        for (int i = 0; i < 50; i++) {
            last = mvc.perform(get("/api/v1/questions/" + questionId).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (expected.equals(JsonPath.read(last, "$.data.aiStatus"))) return last;
            Thread.sleep(200);
        }
        fail("aiStatus가 " + expected + "가 되지 않음: " + last);
        return last;
    }

    private long uploadPdf(String token, long sessionId, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/pdf",
                "%PDF-1.7\n테스트용 PDF".getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/v1/sessions/" + sessionId + "/materials").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Integer) JsonPath.read(body, "$.data.id")).longValue();
    }

    private int postQuestion(String token, long sessionId, String content) throws Exception {
        String body = mvc.perform(post("/api/v1/sessions/" + sessionId + "/questions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.id");
    }

    private String signupAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String name = "src-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"%s\"}".formatted(email, name)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private long createSession(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"출처 테스트 세션\"}"))
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

    private static String tempDir() {
        try {
            return Files.createTempDirectory("askeep-upload-test").toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 진짜 AI 서버와 같은 형식으로 응답하는 가짜 서버 (/documents/process, /ai/answer) */
    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/documents/process", exchange -> {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, 200, "{\"materialId\":1,\"status\":\"success\",\"chunkCount\":3,\"pageCount\":5}");
            });
            server.createContext("/documents/page-count", exchange -> {
                exchange.getRequestBody().readAllBytes();
                pageCountCalls.incrementAndGet();
                respond(exchange, 200, "{\"pageCount\":5}");
            });
            server.createContext("/documents/render-page", exchange -> {
                renderRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (imageFails.get()) {
                    respond(exchange, 500, "{\"detail\":\"render failed\"}");
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, PNG.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(PNG); }
                exchange.close();
            });
            server.createContext("/ai/answer", exchange -> {
                exchange.getRequestBody().readAllBytes();
                long id = sourceMaterialId.get();
                respond(exchange, 200, """
                        {"sessionId":1,"question":"q","answer":"Pod는 컨테이너 묶음입니다.","sources":[
                          {"chunkId":11,"materialId":%d,"chunkIndex":0,"pageNumber":3,"similarity":0.75},
                          {"chunkId":12,"materialId":%d,"chunkIndex":1,"pageNumber":3,"similarity":0.82},
                          {"chunkId":13,"materialId":%d,"chunkIndex":4,"pageNumber":5,"similarity":0.61}
                        ]}""".formatted(id, id, id));
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
