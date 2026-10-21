package com.skala.supplyquote.supplyquote_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skala.supplyquote.supplyquote_backend.auth.JwtTokenService;
import com.skala.supplyquote.supplyquote_backend.auth.UserAccountService;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "app.internal-api-key=test-internal-key")
class JwtSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired JsonMapper json;

    private String token(long id, UserType role, long companyId) {
        return "Bearer " + tokens.issue(new UserAccountService.Account(id, "", "", "", role, companyId, "", true));
    }

    @Test
    void loginRejectsBadPasswordAndIssuedTokenIdentifiesUser() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_id\":\"hb_buyer01\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_id\":\"hb_buyer01\",\"password\":\"demo1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expires_in").value(3600)).andReturn();
        String accessToken = json.readTree(response.getResponse().getContentAsString()).get("access_token").asString();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user_type").value("CUSTOMER"))
                .andExpect(jsonPath("$.company_id").value(1));
    }

    @Test
    void authenticationRolesAndCompanyScopeAreEnforced() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rfqs/1/analysis").header("Authorization", token(1, UserType.CUSTOMER, 1)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/rfqs").param("mine", "true")
                        .header("Authorization", token(2, UserType.MANAGER, 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/rfqs").header("Authorization", token(1, UserType.CUSTOMER, 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(multipart("/api/rfqs").header("Authorization", token(2, UserType.MANAGER, 2))
                        .file(new MockMultipartFile("file", "x.md", "text/markdown", new byte[]{1})))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void revokedAccountAndDifferentCompanyCannotReadRfq() throws Exception {
        jdbc.update("insert into companies(company_id,name,company_type) values (99,'테스트 고객사','CUSTOMER')");
        jdbc.update("insert into users(user_id,login_id,password_hash,company_id,name,user_type) "
                + "values (99,'other_buyer','unused',99,'다른 구매자','CUSTOMER')");
        mvc.perform(get("/api/rfqs/1").header("Authorization", token(99, UserType.CUSTOMER, 99)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/rfqs/1/quotes").header("Authorization", token(99, UserType.CUSTOMER, 99)))
                .andExpect(status().isNotFound());
        jdbc.update("update users set is_active=false where user_id=1");
        mvc.perform(get("/api/me").header("Authorization", token(1, UserType.CUSTOMER, 1)))
                .andExpect(status().isUnauthorized());
    }
}
