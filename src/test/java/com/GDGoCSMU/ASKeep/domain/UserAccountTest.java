package com.GDGoCSMU.ASKeep.domain;

import com.GDGoCSMU.ASKeep.domain.user.service.PasswordResetMailer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 학과, 내 정보 수정, 비밀번호 변경·재설정, 회원 탈퇴 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:askeep;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class UserAccountTest {

    private static final String PASSWORD = "password123";

    @Autowired
    MockMvc mvc;

    /** 실제 메일 대신 보낸 인증코드를 가로챈다 */
    @MockitoBean
    PasswordResetMailer mailer;

    record Account(String email, String name, String token) {}

    private Account signupAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = suffix + "@test.com";
        String name = "user-" + suffix;
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","name":"%s","department":"컴퓨터과학과"}"""
                                .formatted(email, PASSWORD, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.department").value("컴퓨터과학과"));
        return new Account(email, name, login(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .transform(body -> JsonPath.<String>read(body, "$.data.accessToken")));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               String token, String body) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---------- 회원가입 학과 ----------

    @Test
    void 회원가입_학과_필수_내정보에_포함() throws Exception {
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"%s"}"""
                                .formatted(UUID.randomUUID() + "@test.com", UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.startsWith("department")));

        Account a = signupAndLogin();
        me(a.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").value("컴퓨터과학과"));
    }

    @Test
    void 회원가입_중복이름은_409() throws Exception {
        Account a = signupAndLogin();
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"%s","department":"경영학과"}"""
                                .formatted(UUID.randomUUID() + "@test.com", a.name())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));
    }

    // ---------- 이름·학과 변경 ----------

    @Test
    void 이름_학과_변경() throws Exception {
        Account a = signupAndLogin();
        Account b = signupAndLogin();
        String newName = "새이름-" + UUID.randomUUID().toString().substring(0, 8);

        json(patch("/api/v1/users/me"), a.token(), """
                {"name":"%s","department":"경영학과"}""".formatted(newName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName))
                .andExpect(jsonPath("$.data.department").value("경영학과"));

        // 학과만 변경하면 이름은 그대로
        json(patch("/api/v1/users/me"), a.token(), """
                {"department":"디자인학과"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName))
                .andExpect(jsonPath("$.data.department").value("디자인학과"));

        // 자기 이름 그대로 보내도 중복 아님
        json(patch("/api/v1/users/me"), a.token(), """
                {"name":"%s"}""".formatted(newName))
                .andExpect(status().isOk());

        json(patch("/api/v1/users/me"), a.token(), """
                {"name":"%s"}""".formatted(b.name()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NAME"));

        json(patch("/api/v1/users/me"), a.token(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_PROFILE_UPDATE"));

        json(patch("/api/v1/users/me"), a.token(), """
                {"name":"   "}""")
                .andExpect(status().isBadRequest());

        json(patch("/api/v1/users/me"), a.token(), """
                {"name":"탈퇴한 사용자#1"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESERVED_NAME"));
    }

    // ---------- 비밀번호 변경 ----------

    @Test
    void 비밀번호_변경시_기존토큰_무효_새토큰_발급() throws Exception {
        Account a = signupAndLogin();
        // 같은 사용자가 다른 기기에서 로그인한 토큰
        String otherDevice = JsonPath.read(login(a.email(), PASSWORD).andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");

        json(patch("/api/v1/users/me/password"), a.token(), """
                {"currentPassword":"wrong-password","newPassword":"newPassword123"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD"));

        json(patch("/api/v1/users/me/password"), a.token(), """
                {"currentPassword":"password123","newPassword":"password123"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_PASSWORD"));

        json(patch("/api/v1/users/me/password"), a.token(), """
                {"currentPassword":"password123","newPassword":"short"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        String body = json(patch("/api/v1/users/me/password"), a.token(), """
                {"currentPassword":"password123","newPassword":"newPassword123"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.data.accessToken");

        me(a.token()).andExpect(status().isUnauthorized());
        me(otherDevice).andExpect(status().isUnauthorized());
        me(newToken).andExpect(status().isOk());

        login(a.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(a.email(), "newPassword123").andExpect(status().isOk());
    }

    // ---------- 회원 탈퇴 ----------

    @Test
    void 회원탈퇴() throws Exception {
        Account a = signupAndLogin();

        String sessionBody = json(post("/api/v1/sessions"), a.token(), """
                {"title":"탈퇴 테스트 세션"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer sessionId = JsonPath.read(sessionBody, "$.data.sessionId");
        mvc.perform(post("/api/v1/sessions/{id}/start", sessionId).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        json(delete("/api/v1/users/me"), a.token(), """
                {"password":"wrong-password"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD"));

        // 진행 중인 세션이 있으면 탈퇴 불가
        json(delete("/api/v1/users/me"), a.token(), """
                {"password":"password123"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOSTING_LIVE_SESSION"));

        mvc.perform(post("/api/v1/sessions/{id}/end", sessionId).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        json(delete("/api/v1/users/me"), a.token(), """
                {"password":"password123"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 토큰·로그인 모두 막힘
        me(a.token()).andExpect(status().isUnauthorized());
        login(a.email(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));

        // 같은 이메일·이름으로 다시 가입 가능
        mvc.perform(post("/api/v1/users/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"%s","department":"경영학과"}"""
                                .formatted(a.email(), a.name())))
                .andExpect(status().isCreated());

        // 탈퇴한 사람이 만든 세션은 남아 있고 발표자 이름은 익명 처리
        Account viewer = signupAndLogin();
        mvc.perform(get("/api/v1/sessions/{id}", sessionId).header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presenterName").value(org.hamcrest.Matchers.startsWith("탈퇴한 사용자#")));
    }

    // ---------- 비밀번호 재설정 ----------

    @Test
    void 비밀번호_재설정() throws Exception {
        Account a = signupAndLogin();

        // 가입하지 않은 이메일도 똑같이 성공 응답, 메일은 안 보냄
        String unknown = UUID.randomUUID() + "@test.com";
        json(post("/api/v1/users/auth/password-reset/request"), "", """
                {"email":"%s"}""".formatted(unknown))
                .andExpect(status().isOk());
        verify(mailer, never()).send(eq(unknown), anyString(), anyLong());

        // 로그인 없이 요청 가능, 대소문자·공백 무시
        mvc.perform(post("/api/v1/users/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(a.email().toUpperCase())))
                .andExpect(status().isOk());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(a.email()), code.capture(), eq(10L));

        // 1분 안에 다시 요청하면 메일을 또 보내지 않음
        mvc.perform(post("/api/v1/users/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(a.email())))
                .andExpect(status().isOk());
        verify(mailer, times(1)).send(eq(a.email()), anyString(), anyLong());

        String wrongCode = code.getValue().equals("000000") ? "111111" : "000000";
        confirm(a.email(), wrongCode, "resetPassword123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));

        confirm(a.email(), code.getValue(), "resetPassword123")
                .andExpect(status().isOk());

        // 기존 토큰 무효, 새 비밀번호로 로그인
        me(a.token()).andExpect(status().isUnauthorized());
        login(a.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(a.email(), "resetPassword123").andExpect(status().isOk());

        // 코드는 한 번만 사용 가능
        confirm(a.email(), code.getValue(), "another123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
    }

    @Test
    void 비밀번호_재설정_코드_5번_틀리면_폐기() throws Exception {
        Account a = signupAndLogin();
        mvc.perform(post("/api/v1/users/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(a.email())))
                .andExpect(status().isOk());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(a.email()), code.capture(), anyLong());

        String wrongCode = code.getValue().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            confirm(a.email(), wrongCode, "resetPassword123").andExpect(status().isBadRequest());
        }
        // 맞는 코드여도 이미 폐기됨
        confirm(a.email(), code.getValue(), "resetPassword123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
        login(a.email(), PASSWORD).andExpect(status().isOk());
    }

    private ResultActions confirm(String email, String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/v1/users/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","code":"%s","newPassword":"%s"}""".formatted(email, code, newPassword)));
    }
}
