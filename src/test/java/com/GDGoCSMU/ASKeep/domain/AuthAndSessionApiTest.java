package com.GDGoCSMU.ASKeep.domain;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 도커 PostgreSQL 없이 H2 메모리 DB로 돌아가는 통합 테스트.
 * build.gradle에 testRuntimeOnly 'com.h2database:h2' 가 필요하다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthAndSessionApiTest {

    @Autowired
    MockMvc mvc;

    private String signupAndLogin(String name) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"%s"}""".formatted(email, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        String body = mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    void 회원가입_로그인_내정보_로그아웃() throws Exception {
        String token = signupAndLogin("홍길동");

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mvc.perform(post("/api/v1/users/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 로그아웃한 토큰은 더 이상 못 쓴다
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 중복이메일_잘못된비밀번호_토큰없음() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String signup = """
                {"email":"%s","password":"password123","name":"a"}""".formatted(email);
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));

        mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrongpass"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));

        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-email","password":"short","name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void 세션_생성부터_종료까지() throws Exception {
        String presenter = signupAndLogin("발표자");
        String other = signupAndLogin("청중");

        String created = mvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + presenter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Spring Boot 세션\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Spring Boot 세션"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.presenterName").value("발표자"))
                .andExpect(jsonPath("$.data.entryCode").isString())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.data.sessionId");
        String base = "/api/v1/sessions/" + id;

        mvc.perform(get("/api/v1/sessions").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sessionId == " + id + ")]").exists());
        mvc.perform(get("/api/v1/sessions?status=ENDED").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.data[?(@.sessionId == " + id + ")]").doesNotExist());
        mvc.perform(get("/api/v1/sessions?status=WRONG").header("Authorization", "Bearer " + other))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk());

        // 입장 코드는 발표자에게만 보이고, 참여하지 않은 사람에게는 숨겨진다
        mvc.perform(get("/api/v1/sessions").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.data[?(@.sessionId == " + id + ")].entryCode").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get(base).header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.data.entryCode").doesNotExist());
        mvc.perform(get("/api/v1/sessions").header("Authorization", "Bearer " + presenter))
                .andExpect(jsonPath("$.data[?(@.sessionId == " + id + ")].entryCode").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())));
        mvc.perform(get(base).header("Authorization", "Bearer " + presenter))
                .andExpect(jsonPath("$.data.entryCode").isString());

        // 발표자가 아니면 수정/시작 불가
        mvc.perform(patch(base).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SESSION_PRESENTER"));
        mvc.perform(post(base + "/start").header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());

        mvc.perform(patch(base).header("Authorization", "Bearer " + presenter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정된 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));

        // 시작 전에 종료 불가
        mvc.perform(post(base + "/end").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/start").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONGOING"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty());
        mvc.perform(post(base + "/start").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/end").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));

        mvc.perform(delete(base).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base).header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk());
        mvc.perform(get(base).header("Authorization", "Bearer " + presenter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 입장코드로_참여하고_진행중일때만_질문할수있다() throws Exception {
        // 사용자 이름(username)이 unique라 테스트마다 다른 이름을 쓴다
        String presenter = signupAndLogin("발표자-" + UUID.randomUUID().toString().substring(0, 8));
        String audience = signupAndLogin("청중-" + UUID.randomUUID().toString().substring(0, 8));
        String late = signupAndLogin("지각-" + UUID.randomUUID().toString().substring(0, 8));

        String created = mvc.perform(post("/api/v1/sessions").header("Authorization", "Bearer " + presenter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"입장 코드 세션\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.data.sessionId");
        String entryCode = JsonPath.read(created, "$.data.entryCode");
        String base = "/api/v1/sessions/" + id;
        String question = "{\"content\":\"질문입니다\",\"anonymous\":false}";

        // 참여 전에는 질문 불가
        mvc.perform(post(base + "/questions").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isForbidden());

        // 세션 ID만으로 참여하던 기존 API는 삭제됨
        mvc.perform(post(base + "/participants").header("Authorization", "Bearer " + audience))
                .andExpect(status().isNotFound());

        // 잘못된 코드 (0은 발급하지 않는 문자라 절대 존재하지 않음) / 형식 오류 / 발표자 본인
        mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENTRY_CODE"));
        mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + presenter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"%s\"}".formatted(entryCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRESENTER_CANNOT_JOIN"));

        // 소문자로 입력해도 참여되고, 다시 요청해도 같은 결과
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + audience)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\" %s \"}".formatted(entryCode.toLowerCase())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sessionId").value(id))
                    .andExpect(jsonPath("$.data.role").value("AUDIENCE"));
        }

        // 참여한 뒤에는 입장 코드가 보인다
        mvc.perform(get(base).header("Authorization", "Bearer " + audience))
                .andExpect(jsonPath("$.data.entryCode").value(entryCode));
        mvc.perform(get("/api/v1/sessions").header("Authorization", "Bearer " + audience))
                .andExpect(jsonPath("$.data[?(@.sessionId == " + id + ")].entryCode").value(org.hamcrest.Matchers.contains(entryCode)));

        // 시작 전(READY)에는 질문 불가
        mvc.perform(post(base + "/questions").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));

        // 진행 중(ONGOING)에는 질문 가능
        mvc.perform(post(base + "/start").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/questions").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.content").value("질문입니다"));

        // 종료(ENDED) 후에는 질문 불가, 새로 참여도 불가
        mvc.perform(post(base + "/end").header("Authorization", "Bearer " + presenter))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/questions").header("Authorization", "Bearer " + audience)
                        .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        mvc.perform(post("/api/v1/sessions/participants").header("Authorization", "Bearer " + late)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryCode\":\"%s\"}".formatted(entryCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_ENDED"));
    }

    @Test
    void 질문_API도_로그인이_필요하다() throws Exception {
        mvc.perform(get("/api/v1/questions/1")).andExpect(status().isUnauthorized());
    }
}
