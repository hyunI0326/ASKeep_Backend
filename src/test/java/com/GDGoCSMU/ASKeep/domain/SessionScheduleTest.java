package com.GDGoCSMU.ASKeep.domain;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 세션 예정 시작/종료 일시 + 날짜 형식(+09:00 오프셋) */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SessionScheduleTest {

    /** 응답 날짜 끝에 Z 또는 +09:00 같은 오프셋이 붙어 있는지 */
    private static final String OFFSET_PATTERN = ".*(Z|[+-]\\d{2}:\\d{2})$";

    @Autowired
    MockMvc mvc;

    private String signupAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@test.com","password":"password123","name":"user-%s","department":"컴퓨터과학과"}"""
                                .formatted(suffix, suffix)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@test.com","password":"password123"}""".formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions json(MockHttpServletRequestBuilder builder, String token, String body) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 예정일시_오프셋으로_보내면_같은_시각으로_돌려준다() throws Exception {
        String token = signupAndLogin();
        String body = json(post("/api/v1/sessions"), token, """
                        {"title":"예정 세션","scheduledStartAt":"2026-11-20T14:00:00+09:00","scheduledEndAt":"2026-11-20T15:30:00+09:00"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledStartAt").value(matchesPattern(OFFSET_PATTERN)))
                .andExpect(jsonPath("$.data.createdAt").value(matchesPattern(OFFSET_PATTERN)))
                .andReturn().getResponse().getContentAsString();

        // 서버 시간대가 달라도(예: CI는 UTC) 가리키는 순간은 같아야 한다
        assertEquals(OffsetDateTime.parse("2026-11-20T14:00:00+09:00").toInstant(),
                OffsetDateTime.parse(JsonPath.<String>read(body, "$.data.scheduledStartAt")).toInstant());
        assertEquals(OffsetDateTime.parse("2026-11-20T15:30:00+09:00").toInstant(),
                OffsetDateTime.parse(JsonPath.<String>read(body, "$.data.scheduledEndAt")).toInstant());
    }

    @Test
    void 오프셋_없이_보내면_서버_시간으로_본다() throws Exception {
        String token = signupAndLogin();
        json(post("/api/v1/sessions"), token, """
                        {"title":"오프셋 없는 세션","scheduledStartAt":"2026-11-20T14:00:00"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledStartAt").value(startsWith("2026-11-20T14:00:00")))
                .andExpect(jsonPath("$.data.scheduledStartAt").value(matchesPattern(OFFSET_PATTERN)));
    }

    @Test
    void 예정일시는_선택이고_잘못된_값은_400() throws Exception {
        String token = signupAndLogin();
        json(post("/api/v1/sessions"), token, """
                        {"title":"예정 없는 세션"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledStartAt").doesNotExist())
                .andExpect(jsonPath("$.data.scheduledEndAt").doesNotExist());

        json(post("/api/v1/sessions"), token, """
                        {"title":"거꾸로 세션","scheduledStartAt":"2026-11-20T15:00:00+09:00","scheduledEndAt":"2026-11-20T14:00:00+09:00"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        json(post("/api/v1/sessions"), token, """
                        {"title":"이상한 날짜","scheduledStartAt":"내일 2시"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정은_보낸_값만_바꾸고_내세션_목록에도_나온다() throws Exception {
        String token = signupAndLogin();
        String created = json(post("/api/v1/sessions"), token, """
                        {"title":"수정할 세션","scheduledStartAt":"2026-11-20T14:00:00+09:00","scheduledEndAt":"2026-11-20T15:00:00+09:00"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.data.sessionId");
        String start = JsonPath.read(created, "$.data.scheduledStartAt");

        // 종료 일시만 보내면 시작 일시와 제목은 그대로
        String updated = json(patch("/api/v1/sessions/" + id), token, """
                        {"scheduledEndAt":"2026-11-20T16:00:00+09:00"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정할 세션"))
                .andExpect(jsonPath("$.data.scheduledStartAt").value(start))
                .andReturn().getResponse().getContentAsString();
        String end = JsonPath.read(updated, "$.data.scheduledEndAt");
        assertEquals(OffsetDateTime.parse("2026-11-20T16:00:00+09:00").toInstant(), OffsetDateTime.parse(end).toInstant());

        // 기존 시작(14:00)보다 이른 종료로 바꾸면 400
        json(patch("/api/v1/sessions/" + id), token, """
                        {"scheduledEndAt":"2026-11-20T13:00:00+09:00"}""")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/users/me/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].session.scheduledStartAt").value(start))
                .andExpect(jsonPath("$.data[0].session.scheduledEndAt").value(end));
    }
}