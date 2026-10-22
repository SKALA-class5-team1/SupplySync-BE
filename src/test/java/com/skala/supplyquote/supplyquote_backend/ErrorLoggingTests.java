package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 오류 응답이 로그에 사유와 함께 남는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorLoggingTests.BoomController.class})
@ActiveProfiles({"test", "demo"})
@ExtendWith(OutputCaptureExtension.class)
// 로컬 .env의 FRONTEND_ORIGINS와 무관하게 http://localhost:5173이 허용 목록에 없는 상황을 만든다
@TestPropertySource(properties = "app.cors.allowed-origins=http://allowed.example")
class ErrorLoggingTests {
    @RestController
    static class BoomController {
        @GetMapping("/internal/test-boom")
        String boom() {
            throw new IllegalStateException("의도한 테스트 오류");
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void wrongPasswordIsLoggedAsUnauthorizedWithTheReason(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_id\":\"hb_buyer01\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(output.getAll()).contains("HTTP 401 POST /api/auth/login")
                .contains("UNAUTHORIZED: 아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void invalidTokenIsLoggedAndAnswersInTheCommonErrorFormat(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(output.getAll()).contains("HTTP 401 GET /api/me");
    }

    @Test
    void corsRejectionPointsAtFrontendOrigins(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_id\":\"hb_buyer01\",\"password\":\"demo1234\"}"))
                .andExpect(status().isForbidden());
        assertThat(output.getAll()).contains("HTTP 403 POST /api/auth/login")
                .contains("origin=http://localhost:5173").contains("FRONTEND_ORIGINS");
    }

    @Test
    void unexpectedExceptionIsLoggedWithStackTraceAndAnsweredAs500Json(CapturedOutput output) throws Exception {
        mvc.perform(get("/internal/test-boom").header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(output.getAll()).contains("예상하지 못한 오류 GET /internal/test-boom")
                .contains("IllegalStateException").contains("의도한 테스트 오류")
                .contains("HTTP 500 GET /internal/test-boom");
    }

    @Test
    void notFoundAndMethodNotAllowedKeepTheirFrameworkStatus() throws Exception {
        mvc.perform(get("/api/ai/tasks/not-a-uuid")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/models").header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isMethodNotAllowed());
    }
}
