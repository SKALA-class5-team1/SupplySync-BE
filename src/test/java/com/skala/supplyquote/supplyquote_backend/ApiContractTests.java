package com.skala.supplyquote.supplyquote_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skala.supplyquote.supplyquote_backend.service.AgentGateway;
import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisDispatch;
import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisRequestConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.skala.supplyquote.supplyquote_backend.auth.JwtTokenService;
import com.skala.supplyquote.supplyquote_backend.auth.UserAccountService;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 스텁 API가 공개한 계약(경로, JSON 필드명, 상태 코드, 역할별 응답)을 확인한다.
 * 스텁 응답은 데이터셋 정답지에서 만들어지므로 정답지 값이 그대로 나와야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "app.internal-api-key=test-internal-key")
class ApiContractTests {

    @TestConfiguration
    static class InternalKeyTestConfig {
        @Bean
        MockMvcBuilderCustomizer internalKeyByDefault(JwtTokenService tokens) {
            var manager = new UserAccountService.Account(2L, "ds_sales01", "", "김영업", UserType.MANAGER, 2L, "대성오토텍", true);
            return builder -> builder.defaultRequest(get("/")
                    .header("X-Internal-Api-Key", "test-internal-key")
                    .header("Authorization", "Bearer " + tokens.issue(manager)));
        }
    }

    private static final String TASK = "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenService tokens;

    private String customerToken() {
        return "Bearer " + tokens.issue(new UserAccountService.Account(1L, "hb_buyer01", "", "박구매",
                UserType.CUSTOMER, 1L, "한별모터스", true));
    }

    // ------------------------------------------------------------------ OpenAPI 문서
    @Test
    void webGroupExposesAuthRfqTaskAndApprovalContract() throws Exception {
        mvc.perform(get("/v3/api-docs/1-web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs/{rfqId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs/{rfqId}/analysis'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs/{rfqId}/quotes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/stream'].get").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/confirmation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/approval'].post").exists());
    }

    @Test
    void internalGroupExposesDatasetAlignedContract() throws Exception {
        mvc.perform(get("/v3/api-docs/2-internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/rfqs/{rfqNumber}'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/rfqs/{rfqNumber}'].patch").exists())
                .andExpect(jsonPath("$.paths['/internal/rfqs/{rfqNumber}/content'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/models'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/models/{modelId}/bom'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}/inventory'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}/substitutes'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/rfq-reviews'].put").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes/{quoteId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes/{quoteId}/register'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/tasks/{taskId}/events'].post").exists())
                // 확정 데이터셋에 없는 이전 경로는 사용하지 않는다
                .andExpect(jsonPath("$.paths['/internal/purchase-requests']").doesNotExist())
                .andExpect(jsonPath("$.paths['/internal/analysis-results']").doesNotExist());
    }

    @Test
    void openApiSchemaUsesSnakeCaseLikeTheRuntime() throws Exception {
        // 런타임 JSON(스프링)과 OpenAPI 문서(springdoc)가 같은 필드명을 쓰는지 확인한다
        mvc.perform(get("/v3/api-docs/2-internal"))
                .andExpect(jsonPath("$.components.schemas.ReviewUpsertRequest.properties.rfq_number").exists())
                .andExpect(jsonPath("$.components.schemas.ReviewUpsertRequest.properties.selected_model").exists())
                .andExpect(jsonPath("$.components.schemas.ReviewUpsertRequest.properties.recommended_alt").exists())
                .andExpect(jsonPath("$.components.schemas.QuoteDraftRequest.properties.unit_price_krw").exists())
                .andExpect(jsonPath("$.components.schemas.QuoteDraftRequest.properties.price_by_year").exists())
                .andExpect(jsonPath("$.components.schemas.Shortage.properties.is_critical").exists())
                .andExpect(jsonPath("$.components.schemas.ReviewUpsertRequest.properties.rfqNumber").doesNotExist())
                // 스키마 이름 규칙을 바꿔도 OpenAPI 문서 자체의 키(requestBody, operationId 등)는 그대로여야 한다
                .andExpect(jsonPath("$.paths['/internal/quotes'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes'].post.operationId").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes'].post.request_body").doesNotExist());
    }

    // ------------------------------------------------------------------ 화면용 API
    @Test
    @Transactional
    void rfqUploadAcceptsMarkdownFileAndRfqNumber() throws Exception {
        var file = new MockMultipartFile("file", "RFQ_HB-EV7-EWP-001.md", "text/markdown", "# 견적요청서".getBytes(StandardCharsets.UTF_8));
        var number = new MockMultipartFile("rfq_number", "", "text/plain", "TEST-RFQ-001".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/rfqs").file(file).file(number).header("Authorization", customerToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rfq_number").value("TEST-RFQ-001"))
                .andExpect(jsonPath("$.task_id").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(get("/internal/rfqs/TEST-RFQ-001/content"))
                .andExpect(status().isOk())
                .andExpect(content().string("# 견적요청서"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from ai_tasks where rfq_number='TEST-RFQ-001'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rfqUploadWithoutRfqNumberFailsWithCommonErrorFormat() throws Exception {
        var file = new MockMultipartFile("file", "a.md", "text/markdown", "x".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/rfqs").file(file).header("Authorization", customerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void analysisViewFollowsDatasetExpectedValues() throws Exception {
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerQuoteHidesEveryInternalField() throws Exception {
        // 데이터셋 규칙: 고객에게는 단가만 공개하고 원가·마진·검토 결과는 내부용이다
        mvc.perform(get("/api/rfqs/1/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rfqListAndDetailUseSnakeCase() throws Exception {
        mvc.perform(get("/api/rfqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rfq_number").value("HB-EV7-EWP-001"))
                .andExpect(jsonPath("$[0].customer_company").value("한별모터스"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"));
        mvc.perform(get("/api/rfqs/1"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.sample_qty").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.latest_task_id").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void loginAndMeReturnUserInfo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login_id":"ds_sales01","password":"demo1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.user.user_type").value("MANAGER"))
                .andExpect(jsonPath("$.user.company_name").value("대성오토텍"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login_id":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Transactional
    void confirmationRequiresAssumptions() throws Exception {
        seedTask("NEEDS_CLARIFICATION");
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001',?)", "task-" + TASK);
        mvc.perform(post("/api/ai/tasks/" + TASK + "/confirmation").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assumptions":[{"field":"spec.flow","value":"22 L/min"}]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/ai/tasks/" + TASK + "/confirmation").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assumptions":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void approvalIsPerQuoteAndRejectionNeedsAReason() throws Exception {
        seedTask("WAITING_APPROVAL");
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001',?)", "task-" + TASK);
        jdbc.update("""
                insert into quotes(quote_id,review_id,rfq_number,model_id,unit_price_krw,price_by_year,status)
                values ('Q-2026-1019-001',(select review_id from rfq_reviews where thread_id=?),
                        'HB-EV7-EWP-001','EWP-12-B',37300,'[]'::jsonb,'DRAFT')
                """, "task-" + TASK);
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"APPROVED"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quote_id").value("Q-2026-1019-001"))
                .andExpect(jsonPath("$.status").value("REVALIDATING"));
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"REJECTED","reason":"마진 재검토","return_to_step":"QUOTE"}"""))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"REJECTED"}"""))
                .andExpect(status().isConflict());
        // 승인 단위는 quote_id이므로 없으면 400
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void sseStreamUsesEventStreamContentType() throws Exception {
        seedTask("QUEUED");
        mvc.perform(get("/api/ai/tasks/" + TASK + "/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    // ------------------------------------------------------------------ 내부 API
    @Test
    void rfqContentIsServedAsMarkdown() throws Exception {
        mvc.perform(get("/internal/rfqs/HB-EV7-EWP-001/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("22 L/h")));
    }

    @Test
    void bomAndInventoryMatchSg2Expected() throws Exception {
        mvc.perform(get("/internal/models/EWP-12-B/bom").param("options", "OPT-HT,OPT-IP9K"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[?(@.part_id == 'P-MCU-G1')].source").value(org.hamcrest.Matchers.hasItem("OPTION:OPT-HT")))
                .andExpect(jsonPath("$.items[?(@.part_id == 'P-MCU-G1')].replaced_part_id").value(org.hamcrest.Matchers.hasItem("P-MCU-STD")))
                .andExpect(jsonPath("$.items[0].replaced_part_id").doesNotExist());
        mvc.perform(get("/internal/parts/P-MCU-G1/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on_hand").value(9000))
                .andExpect(jsonPath("$.allocated").value(7500))
                .andExpect(jsonPath("$.available").value(1500))
                .andExpect(jsonPath("$.incoming_date").value("2027-01-20"));
        mvc.perform(get("/internal/parts/P-NOPE/inventory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/internal/parts/P-MCU-G1/substitutes"))
                .andExpect(jsonPath("$[0].substitute_part_id").value("P-MCU-G1-ALT"))
                .andExpect(jsonPath("$[0].pin_compatible").value(true))
                .andExpect(jsonPath("$[0].ref_chunk_id").value("alt-002"))
                .andExpect(jsonPath("$[0].unit_cost_diff_krw").value(300));
        mvc.perform(get("/internal/parts/P-IMP-25/substitutes"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void catalogReadsBaseBomAndValidatesModelOptions() throws Exception {
        mvc.perform(get("/internal/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        // 데이터셋·DB 컬럼과 같은 이름 (size_lmm이 아니다)
        mvc.perform(get("/internal/models/EWP-12-B"))
                .andExpect(jsonPath("$.size_l_mm").isNumber())
                .andExpect(jsonPath("$.size_w_mm").isNumber())
                .andExpect(jsonPath("$.size_h_mm").isNumber())
                .andExpect(jsonPath("$.size_lmm").doesNotExist());
        mvc.perform(get("/internal/models/EWP-12-B/bom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(0))
                .andExpect(jsonPath("$.items[?(@.part_id == 'P-MCU-STD')].source")
                        .value(org.hamcrest.Matchers.hasItem("BASE")));
        mvc.perform(get("/internal/models/EWP-12-A/bom").param("options", "OPT-IP9K"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/internal/models/no-such-model"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void rfqAndInventoryReadsReflectDatabaseUpdates() throws Exception {
        jdbc.update("update rfqs set status = 'IN_REVIEW', eau = 150000 where rfq_id = 1");
        jdbc.update("update inventory set allocated = 8000 where part_id = 'P-MCU-G1'");
        mvc.perform(get("/api/rfqs"))
                .andExpect(jsonPath("$[0].status").value("IN_REVIEW"));
        mvc.perform(get("/api/rfqs/1"))
                .andExpect(jsonPath("$.eau").value(150000));
        mvc.perform(get("/internal/rfqs/HB-EV7-EWP-001"))
                .andExpect(jsonPath("$.eau").value(150000));
        mvc.perform(get("/internal/parts/P-MCU-G1/inventory"))
                .andExpect(jsonPath("$.available").value(1000));
        mvc.perform(get("/api/rfqs/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void reviewUpsertAcceptsPartialSections() throws Exception {
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"rfq_number":"HB-EV7-EWP-001","thread_id":"task-1","status":"WAITING",
                         "issues":[{"id":"ISS-01","type":"이상값","field":"spec.flow","severity":"HIGH"}],
                         "final_plan":{"P-MCU-G1":1500,"P-MCU-G1-ALT":500}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.thread_id").value("task-1"));
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(jsonPath("$.review.final_plan['P-MCU-G1']").value(1500))
                .andExpect(jsonPath("$.review.final_plan['P-MCU-G1-ALT']").value(500));
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"rfq_number":"HB-EV7-EWP-001"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void reviewEvidenceSurvivesUpsertAndManagerRead() throws Exception {
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"rfq_number":"HB-EV7-EWP-001","thread_id":"task-evidence","status":"WAITING",
                 "issues":[{"id":"ISS-01","evidence":[{"chunk_id":"dsn-002","section":"2.1","excerpt":"유량 기준"}]}],
                 "contract_risks":[{"item":"보증 기간","evidence":[{"chunk_id":"pol-003","section":"제12조","excerpt":"보증 기준"}]}],
                 "alternatives":[{"type":"SUBSTITUTE_PART","evidence":[{"chunk_id":"alt-002","section":"대체품","excerpt":"적용 가능"}]}]}
                """))
                .andExpect(status().isOk());
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"rfq_number":"HB-EV7-EWP-001","thread_id":"task-evidence","status":"DONE"}
                """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.status").value("DONE"))
                .andExpect(jsonPath("$.review.issues[0].evidence[0].chunk_id").value("dsn-002"))
                .andExpect(jsonPath("$.review.issues[0].evidence[0].section").value("2.1"))
                .andExpect(jsonPath("$.review.issues[0].evidence[0].excerpt").value("유량 기준"))
                .andExpect(jsonPath("$.review.contract_risks[0].evidence[0].chunk_id").value("pol-003"))
                .andExpect(jsonPath("$.review.alternatives[0].evidence[0].chunk_id").value("alt-002"));
    }

    @Test
    @Transactional
    void quoteDraftUsesDatasetQuoteDraftAndServerGeneratesQuoteId() throws Exception {
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001','task-1')");
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", "task-1");
        draft.put("revision", 1);
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quote_id").value("Q-2026-1019-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select issue_date from quotes where quote_id='Q-2026-1019-001'", java.time.LocalDate.class))
                .isEqualTo(java.time.LocalDate.parse("2026-10-19"));
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(jsonPath("$.quotes[0].issue_date").value("2026-10-19"))
                .andExpect(jsonPath("$.quotes[0].details.recipient").value("한별모터스 구매본부"))
                .andExpect(jsonPath("$.quotes[0].details.price_breakdown").isArray())
                .andExpect(jsonPath("$.quotes[0].details.parts_cost_breakdown").isArray());
        draft.remove("price_by_year");
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/internal/quotes/Q-2026-1019-001").contentType(MediaType.APPLICATION_JSON).content("""
                        {"checks":[{"rule":"MARGIN","result":"PASS"}]}"""))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void reviseCreatesNewQuoteUpToThreeTimesAndRetriesReturnSameId() throws Exception {
        seedTask("WAITING_APPROVAL");
        String thread = "task-" + TASK;
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001',?)", thread);
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", thread);
        String lastId = null;
        for (int revision = 1; revision <= 4; revision++) {
            draft.put("revision", revision);
            String body = json.writeValueAsString(draft);
            var created = mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andReturn();
            String quoteId = json.readTree(created.getResponse().getContentAsString()).get("quote_id").asString();
            if (lastId != null) org.assertj.core.api.Assertions.assertThat(quoteId).isNotEqualTo(lastId);
            mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.quote_id").value(quoteId));
            if (revision > 1) {
                mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"APPROVAL_REQUIRED\",\"step\":\"APPROVAL\"}"))
                        .andExpect(status().isAccepted());
            }
            if (revision < 4) {
                mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quote_id\":\"" + quoteId + "\",\"decision\":\"REVISE\",\"reason\":\"조건 수정\"}"))
                        .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RUNNING"));
                org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                        "select revision_requested from quotes where quote_id=?", Boolean.class, quoteId)).isTrue();
                mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"RUN_END\",\"data\":{\"outcome\":\"REJECTED\"}}"))
                        .andExpect(status().isConflict());
            }
            lastId = quoteId;
        }
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quote_id\":\"" + lastId + "\",\"decision\":\"REVISE\",\"reason\":\"또 수정\"}"))
                .andExpect(status().isConflict());
        draft.put("revision", 5);
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft))).andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from quotes", Integer.class)).isEqualTo(4);
    }

    @Test
    @Transactional
    void rejectedRunEndCompletesTaskWithoutAllowingRevision() throws Exception {
        seedTask("WAITING_APPROVAL");
        String thread = "task-" + TASK;
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001',?)", thread);
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", thread);
        draft.put("revision", 1);
        String quoteId = json.readTree(mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft))).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("quote_id").asString();
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quote_id\":\"" + quoteId + "\",\"decision\":\"REJECTED\",\"reason\":\"중단\"}"))
                .andExpect(status().isAccepted());
        String end = "{\"type\":\"RUN_END\",\"payload\":{\"outcome\":\"REJECTED\"}}";
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON).content(end))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON).content(end))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("REJECTED"));
        draft.put("revision", 2);
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft))).andExpect(status().isConflict());
    }

    @Test
    @Transactional
    void registerIsIdempotentContract() throws Exception {
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001','task-register')");
        jdbc.update("""
                insert into quotes(quote_id,review_id,rfq_number,model_id,unit_price_krw,price_by_year,status)
                values ('Q-2026-1019-001',(select review_id from rfq_reviews where thread_id='task-register'),
                        'HB-EV7-EWP-001','EWP-12-B',37300,'[]'::jsonb,'APPROVED')
                """);
        mvc.perform(post("/internal/quotes/Q-2026-1019-001/register"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.rfq_status").value("QUOTED"))
                .andExpect(jsonPath("$.outcome").value("CREATED"));
        mvc.perform(post("/internal/quotes/Q-2026-1019-001/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_PROCESSED"));
    }

    @Test
    @Transactional
    void agentEventsMapToTaskStatus() throws Exception {
        seedTask("QUEUED");
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CLARIFICATION_REQUIRED\",\"step\":\"RFQ_ANALYSIS\","
                                + "\"trace_url\":\"https://trace.example/runs/123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"));
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.current_step").value("RFQ_ANALYSIS"))
                .andExpect(jsonPath("$.trace_url").value("https://trace.example/runs/123"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"STEP_START\",\"step\":\"SUPPLY_CHECK\"}"))
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOT_AN_EVENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalApiRejectsWrongSharedKey() throws Exception {
        mvc.perform(get("/internal/models").header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @Transactional
    void reviewQuoteApprovalAndCustomerViewFollowStoredState() throws Exception {
        seedTask("RUNNING");
        String thread = "task-" + TASK;
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"rfq_number":"HB-EV7-EWP-001","thread_id":"%s","selected_model":"EWP-12-B",
                 "issues":[{"id":"ISS-01","type":"이상값","field":"spec.flow","severity":"HIGH"}]}
                """.formatted(thread))).andExpect(status().isOk());
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"rfq_number":"HB-EV7-EWP-001","thread_id":"%s","status":"DONE",
                 "recommended_alt":"SUBSTITUTE_PART"}
                """.formatted(thread))).andExpect(status().isOk());
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", thread);
        draft.put("revision", 1);
        var created = mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(draft))).andExpect(status().isCreated()).andReturn();
        String quoteId = json.readTree(created.getResponse().getContentAsString()).get("quote_id").asString();
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.selected_model").value("EWP-12-B"))
                .andExpect(jsonPath("$.review.issues.length()").value(1))
                .andExpect(jsonPath("$.review.recommended_alt").value("SUBSTITUTE_PART"))
                .andExpect(jsonPath("$.quotes[0].quote_id").value(quoteId));
        mvc.perform(get("/api/rfqs/1/quotes"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"APPROVAL_REQUIRED\",\"step\":\"APPROVAL\"}"))
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quote_id\":\"" + quoteId + "\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REVALIDATING"));
        mvc.perform(post("/internal/quotes/" + quoteId + "/register"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rfq_status").value("QUOTED"));
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.status").value("REGISTERING"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RUN_END\",\"data\":{\"outcome\":\"REGISTERED\"}}"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/rfqs/1/quotes"))
                .andExpect(jsonPath("$[0].quote_id").value(quoteId))
                .andExpect(jsonPath("$[0].issue_date").value("2026-10-19"))
                .andExpect(jsonPath("$[0].details.price_breakdown").isArray())
                .andExpect(jsonPath("$[0].details.parts_cost_breakdown").doesNotExist())
                .andExpect(jsonPath("$[0].parts_cost_krw").doesNotExist())
                .andExpect(jsonPath("$[0].margin_pct").doesNotExist())
                .andExpect(jsonPath("$[0].price_by_year[0].margin_pct").doesNotExist());
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.outcome").value("REGISTERED"));
    }

    @Test
    void agentHttpAdapterCallsRunAndResumeWithThreadId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var paths = new java.util.concurrent.CopyOnWriteArrayList<String>();
        AtomicReference<String> receivedKey = new AtomicReference<>();
        var resumeBodies = new java.util.concurrent.CopyOnWriteArrayList<String>();
        CountDownLatch calls = new CountDownLatch(3);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (exchange.getRequestURI().getPath().endsWith("/resume")) resumeBodies.add(body);
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            calls.countDown();
        });
        server.start();
        UUID taskId = UUID.randomUUID();
        String threadId = "task-" + taskId;
        jdbc.update("insert into ai_tasks(task_id,rfq_number,requested_by,status,thread_id,request_id) "
                + "values (?,'HB-EV7-EWP-001',1,'QUEUED',?,?)", taskId, threadId, UUID.randomUUID());
        try {
            AgentGateway gateway = new AgentGateway(jdbc, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-internal-key", 300);
            gateway.start(taskId, 1L, "HB-EV7-EWP-001", threadId, UUID.randomUUID());
            gateway.resume(taskId, threadId, "CLARIFICATION", 2L,
                    Map.of("assumptions", List.of(Map.of("field", "spec.flow", "value", "22 L/min"))));
            gateway.resumeApproval(taskId, threadId, 2L,
                    Map.of("result", "REVISE", "reason", "조건 수정", "changes", Map.of()));
            org.assertj.core.api.Assertions.assertThat(calls.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThat(paths).contains("/runs", "/runs/" + threadId + "/resume");
            org.assertj.core.api.Assertions.assertThat(receivedKey.get()).isEqualTo("test-internal-key");
            JsonNode approval = resumeBodies.stream().map(json::readTree)
                    .filter(node -> "SG4.request_approval".equals(node.path("step").asString())).findFirst().orElseThrow();
            org.assertj.core.api.Assertions.assertThat(approval.get("userId").asLong()).isEqualTo(2L);
            org.assertj.core.api.Assertions.assertThat(approval.get("decision").get("result").asString()).isEqualTo("REVISE");
            org.assertj.core.api.Assertions.assertThat(approval.has("quoteId")).isFalse();
            JsonNode clarification = resumeBodies.stream().map(json::readTree)
                    .filter(node -> "CLARIFICATION".equals(node.path("kind").asString())).findFirst().orElseThrow();
            org.assertj.core.api.Assertions.assertThat(clarification.get("userId").asLong()).isEqualTo(2L);
            org.assertj.core.api.Assertions.assertThat(clarification.get("task_id").asString()).isEqualTo(taskId.toString());
            org.assertj.core.api.Assertions.assertThat(clarification.get("decision").get("assumptions").get(0).get("field").asString())
                    .isEqualTo("spec.flow");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline &&
                    !"RUNNING".equals(jdbc.queryForObject("select status from ai_tasks where task_id=?", String.class, taskId))) {
                Thread.sleep(10);
            }
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status from ai_tasks where task_id=?", String.class, taskId))
                    .isEqualTo("RUNNING");
        } finally {
            jdbc.update("delete from ai_tasks where task_id=?", taskId);
            server.stop(0);
        }
    }

    @Test
    void agentCallFailsWhenResponseExceedsReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try { Thread.sleep(2_500); } catch (InterruptedException ignored) { }
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            AgentGateway gateway = new AgentGateway(jdbc, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-internal-key", 1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    gateway.startSync(UUID.randomUUID(), 1L, "HB-EV7-EWP-001", "task-x", UUID.randomUUID()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Transactional
    void analysisOutboxAndConsumerDeduplicateBrokerRedelivery() {
        seedTask("QUEUED");
        String thread = "task-" + TASK;
        UUID taskId = UUID.fromString(TASK);
        AgentGateway gateway = org.mockito.Mockito.mock(AgentGateway.class);
        AnalysisDispatch dispatch = new AnalysisDispatch(true, jdbc, json, gateway);
        UUID requestId = jdbc.queryForObject("select request_id from ai_tasks where task_id=?", UUID.class, taskId);
        dispatch.requested(taskId, 1L, 2L, "HB-EV7-EWP-001", thread, requestId);
        String body = jdbc.queryForObject("select body::text from analysis_outbox where type='analysis.requested'",
                String.class);
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("analysis.requested");
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8), properties);
        AnalysisRequestConsumer consumer = new AnalysisRequestConsumer(jdbc, json, gateway, 0);
        consumer.receive(message);
        consumer.receive(message);
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1))
                .startSync(taskId, 1L, "HB-EV7-EWP-001", thread, requestId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from ai_tasks where task_id=?", String.class, taskId)).isEqualTo("RUNNING");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from processed_messages", Integer.class)).isEqualTo(1);

        MessageProperties resumeProperties = new MessageProperties();
        resumeProperties.setReceivedRoutingKey("analysis.resume");
        Map<String, Object> decision = Map.of("result", "REVISE", "reason", "조건 수정", "changes", Map.of());
        dispatch.approval(taskId, thread, 2L, decision);
        String resumeBody = jdbc.queryForObject("select body::text from analysis_outbox "
                + "where type='analysis.resume' and body->'payload'->>'kind'='REVISE'", String.class);
        consumer.receive(new Message(resumeBody.getBytes(StandardCharsets.UTF_8), resumeProperties));
        org.mockito.Mockito.verify(gateway).resumeApprovalSync(thread, 2L, decision);

        dispatch.clarification(taskId, thread, 2L, List.of(Map.of("field", "spec.flow", "value", "22 L/min")));
        String clarificationBody = jdbc.queryForObject("select body::text from analysis_outbox "
                + "where body->'payload'->>'kind'='CLARIFICATION'", String.class);
        consumer.receive(new Message(clarificationBody.getBytes(StandardCharsets.UTF_8), resumeProperties));
        org.mockito.Mockito.verify(gateway).resumeSync(org.mockito.ArgumentMatchers.eq(taskId),
                org.mockito.ArgumentMatchers.eq(thread), org.mockito.ArgumentMatchers.eq("CLARIFICATION"),
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @Transactional
    void agentCallIsNotRetriedByDefaultAndFailureFailsTheTask() {
        seedTask("QUEUED");
        String thread = "task-" + TASK;
        UUID taskId = UUID.fromString(TASK);
        UUID requestId = jdbc.queryForObject("select request_id from ai_tasks where task_id=?", UUID.class, taskId);
        AgentGateway gateway = org.mockito.Mockito.mock(AgentGateway.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Agent 응답 없음")).when(gateway)
                .startSync(taskId, 1L, "HB-EV7-EWP-001", thread, requestId);
        new AnalysisDispatch(true, jdbc, json, gateway).requested(taskId, 1L, 2L, "HB-EV7-EWP-001", thread, requestId);
        String body = jdbc.queryForObject("select body::text from analysis_outbox where type='analysis.requested'",
                String.class);
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("analysis.requested");
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8), properties);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AnalysisRequestConsumer(jdbc, json, gateway, 0).receive(message))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1))
                .startSync(taskId, 1L, "HB-EV7-EWP-001", thread, requestId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from ai_tasks where task_id=?", String.class, taskId)).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select failure_reason from ai_tasks where task_id=?", String.class, taskId)).contains("Agent 응답 없음");
    }

    @Test
    @Transactional
    void configuredRetriesCallTheAgentAgain() {
        seedTask("QUEUED");
        String thread = "task-" + TASK;
        UUID taskId = UUID.fromString(TASK);
        UUID requestId = jdbc.queryForObject("select request_id from ai_tasks where task_id=?", UUID.class, taskId);
        AgentGateway gateway = org.mockito.Mockito.mock(AgentGateway.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("일시 오류")).doNothing().when(gateway)
                .startSync(taskId, 1L, "HB-EV7-EWP-001", thread, requestId);
        new AnalysisDispatch(true, jdbc, json, gateway).requested(taskId, 1L, 2L, "HB-EV7-EWP-001", thread, requestId);
        String body = jdbc.queryForObject("select body::text from analysis_outbox where type='analysis.requested'",
                String.class);
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("analysis.requested");
        new AnalysisRequestConsumer(jdbc, json, gateway, 1)
                .receive(new Message(body.getBytes(StandardCharsets.UTF_8), properties));
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(2))
                .startSync(taskId, 1L, "HB-EV7-EWP-001", thread, requestId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from ai_tasks where task_id=?", String.class, taskId)).isEqualTo("RUNNING");
    }

    @Test
    @Transactional
    void runOnHoldEventMarksTaskOnHoldForManagersOnly() throws Exception {
        seedTask("RUNNING");
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RUN_ON_HOLD\",\"step\":\"ALTERNATIVE_ANALYSIS\","
                                + "\"message\":\"쓸 수 있는 대안이 없습니다\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.current_step").value("ALTERNATIVE_ANALYSIS"));
        mvc.perform(get("/api/ai/tasks/" + TASK).header("Authorization", customerToken()))
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.failure_reason").doesNotExist());
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SAVE_APPROVAL_REQUIRED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void stockChangeAfterApprovalAllowsNextRevisionAndSupersedesThePreviousQuote() throws Exception {
        seedTask("WAITING_APPROVAL");
        String thread = "task-" + TASK;
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001',?)", thread);
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", thread);
        draft.put("revision", 1);
        var created = mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft)))
                .andExpect(status().isCreated()).andReturn();
        String first = json.readTree(created.getResponse().getContentAsString()).get("quote_id").asString();

        // 승인 전(DRAFT)에는 REVISE 없이 다음 revision을 만들 수 없다
        draft.put("revision", 2);
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft))).andExpect(status().isConflict());

        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quote_id\":\"" + first + "\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REVALIDATING"));

        // 재검증에서 재고가 부족해 Agent가 SG2부터 다시 써서 revision 2를 저장한다
        var second = mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft)))
                .andExpect(status().isCreated()).andReturn();
        String secondId = json.readTree(second.getResponse().getContentAsString()).get("quote_id").asString();
        org.assertj.core.api.Assertions.assertThat(secondId).isNotEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from quotes where quote_id=?", String.class, first)).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select reject_reason from quotes where quote_id=?", String.class, first)).isEqualTo("재고 변경으로 재작성");
        mvc.perform(get("/api/ai/tasks/" + TASK))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.current_step").value("QUOTE"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPROVAL_REQUIRED\",\"step\":\"APPROVAL\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quote_id\":\"" + secondId + "\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted());
        // 같은 요청의 재시도는 기존 quote_id를 돌려준다
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(draft)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quote_id").value(secondId));
    }

    @Test
    @Transactional
    void watchdogFailsOnlyStalledTasksAndSkipsHumanWaits() {
        String[][] rows = {
                {"QUEUED", "20 minutes"}, {"QUEUED", "1 minutes"}, {"RUNNING", "45 minutes"},
                {"RUNNING", "5 minutes"}, {"REVALIDATING", "45 minutes"}, {"WAITING_APPROVAL", "5 hours"},
                {"ON_HOLD", "5 hours"}, {"NEEDS_CLARIFICATION", "5 hours"}, {"DONE", "5 hours"}};
        var ids = new java.util.ArrayList<UUID>();
        for (String[] row : rows) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("insert into ai_tasks(task_id,rfq_number,requested_by,status,thread_id,request_id,updated_at) "
                    + "values (?,'HB-EV7-EWP-001',1,?,?,?, now() - ?::interval)",
                    id, row[0], "task-" + id, UUID.randomUUID(), row[1]);
        }
        new com.skala.supplyquote.supplyquote_backend.service.StalledTaskWatchdog(jdbc,
                new com.skala.supplyquote.supplyquote_backend.service.TaskEventStream(), true, 10, 30).sweep();
        var expected = java.util.List.of("FAILED", "QUEUED", "FAILED", "RUNNING", "FAILED", "WAITING_APPROVAL",
                "ON_HOLD", "NEEDS_CLARIFICATION", "DONE");
        for (int i = 0; i < ids.size(); i++) {
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "select status from ai_tasks where task_id=?", String.class, ids.get(i))).isEqualTo(expected.get(i));
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select failure_reason from ai_tasks where task_id=?", String.class, ids.get(0))).contains("시작되지 않았습니다");
    }

    private void seedTask(String status) {
        jdbc.update("""
                insert into ai_tasks(task_id,rfq_number,requested_by,status,thread_id,request_id)
                values (?,'HB-EV7-EWP-001',1,?,?,?)
                """, java.util.UUID.fromString(TASK), status, "task-" + TASK, java.util.UUID.randomUUID());
    }

    private JsonNode fixture(String file) throws IOException {
        try (InputStream in = new ClassPathResource("stub/" + file).getInputStream()) {
            return json.readTree(in);
        }
    }
}
