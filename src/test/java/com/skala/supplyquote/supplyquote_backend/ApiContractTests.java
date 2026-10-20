package com.skala.supplyquote.supplyquote_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
@ActiveProfiles("test")
class ApiContractTests {

    private static final String TASK = "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

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
                // 데이터셋에 없는 V1 시절 경로는 사라졌다
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
    void rfqUploadAcceptsMarkdownFileAndRfqNumber() throws Exception {
        var file = new MockMultipartFile("file", "RFQ_HB-EV7-EWP-001.md", "text/markdown", "# 견적요청서".getBytes(StandardCharsets.UTF_8));
        var number = new MockMultipartFile("rfq_number", "", "text/plain", "HB-EV7-EWP-001".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/rfqs").file(file).file(number))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rfq_number").value("HB-EV7-EWP-001"))
                .andExpect(jsonPath("$.task_id").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void rfqUploadWithoutRfqNumberFailsWithCommonErrorFormat() throws Exception {
        var file = new MockMultipartFile("file", "a.md", "text/markdown", "x".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/rfqs").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void analysisViewFollowsDatasetExpectedValues() throws Exception {
        mvc.perform(get("/api/rfqs/1/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.selected_model").value("EWP-12-B"))
                .andExpect(jsonPath("$.review.fit_type").value("파생 사양"))
                .andExpect(jsonPath("$.review.issues.length()").value(7))
                .andExpect(jsonPath("$.review.customer_questions.length()").value(5))
                .andExpect(jsonPath("$.review.contract_risks.length()").value(2))
                .andExpect(jsonPath("$.review.shortages[0].part_id").value("P-MCU-G1"))
                .andExpect(jsonPath("$.review.shortages[0].short_qty").value(500))
                .andExpect(jsonPath("$.review.shortages[0].is_critical").value(true))
                .andExpect(jsonPath("$.review.alternatives.length()").value(4))
                .andExpect(jsonPath("$.review.recommended_alt").value("SUBSTITUTE_PART"))
                .andExpect(jsonPath("$.review.alternatives[2].extra_cost_krw").value(150000))
                .andExpect(jsonPath("$.review.alternatives[0].substitute_part_id").doesNotExist())
                .andExpect(jsonPath("$.quotes[0].quote_id").value("Q-2026-1019-001"))
                .andExpect(jsonPath("$.quotes[0].unit_price_krw").value(37300))
                .andExpect(jsonPath("$.quotes[0].parts_cost_krw").value(21650))
                .andExpect(jsonPath("$.quotes[0].margin_pct").value(42.0))
                .andExpect(jsonPath("$.quotes[0].price_by_year.length()").value(6))
                .andExpect(jsonPath("$.quotes[0].price_by_year[5].unit_price_krw").value(32031))
                .andExpect(jsonPath("$.quotes[0].price_by_year[5].margin_pct").value(32.4))
                .andExpect(jsonPath("$.quotes[0].checks.length()").value(5));
    }

    @Test
    void customerQuoteHidesEveryInternalField() throws Exception {
        // 데이터셋 규칙: 고객에게는 단가만 공개하고 원가·마진·검토 결과는 내부용이다
        mvc.perform(get("/api/rfqs/1/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$[0].unit_price_krw").value(37300))
                .andExpect(jsonPath("$[0].price_by_year.length()").value(6))
                .andExpect(jsonPath("$[0].price_by_year[0].unit_price_krw").value(37300))
                .andExpect(jsonPath("$[0].parts_cost_krw").doesNotExist())
                .andExpect(jsonPath("$[0].margin_pct").doesNotExist())
                .andExpect(jsonPath("$[0].checks").doesNotExist())
                .andExpect(jsonPath("$[0].approved_by").doesNotExist())
                .andExpect(jsonPath("$[0].price_by_year[0].margin_pct").doesNotExist())
                .andExpect(jsonPath("$[0].price_by_year[0].cr_applied").doesNotExist());
    }

    @Test
    void rfqListAndDetailUseSnakeCase() throws Exception {
        mvc.perform(get("/api/rfqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rfq_number").value("HB-EV7-EWP-001"))
                .andExpect(jsonPath("$[0].customer_company").value("한별모터스"))
                .andExpect(jsonPath("$[0].status").value("IN_REVIEW"));
        mvc.perform(get("/api/rfqs/1"))
                .andExpect(jsonPath("$.sample_qty").value(2000))
                .andExpect(jsonPath("$.sample_due_date").value("2026-12-15"))
                .andExpect(jsonPath("$.target_price_krw").value(38000));
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
    void confirmationRequiresAssumptions() throws Exception {
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
    void approvalIsPerQuoteAndRejectionNeedsAReason() throws Exception {
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"APPROVED"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quote_id").value("Q-2026-1019-001"))
                .andExpect(jsonPath("$.status").value("REVALIDATING"));
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"REJECTED","reason":"마진 재검토","return_to_step":"QUOTE"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quote_id":"Q-2026-1019-001","decision":"REJECTED"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 승인 단위는 quote_id이므로 없으면 400
        mvc.perform(post("/api/ai/tasks/" + TASK + "/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sseStreamUsesEventStreamContentType() throws Exception {
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
                .andExpect(jsonPath("$.items[1].part_id").value("P-MCU-G1"))
                .andExpect(jsonPath("$.items[1].source").value("OPTION:OPT-HT"))
                .andExpect(jsonPath("$.items[1].replaced_part_id").value("P-MCU-STD"))
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
    void reviewUpsertAcceptsPartialSections() throws Exception {
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"rfq_number":"HB-EV7-EWP-001","thread_id":"task-1","status":"WAITING",
                         "issues":[{"id":"ISS-01","type":"이상값","field":"spec.flow","severity":"HIGH"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.thread_id").value("task-1"));
        mvc.perform(put("/internal/rfq-reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"rfq_number":"HB-EV7-EWP-001"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quoteDraftUsesDatasetQuoteDraftAndServerGeneratesQuoteId() throws Exception {
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        draft.remove("quote_id");
        draft.remove("status");
        draft.put("thread_id", "task-1");
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quote_id").value("Q-2026-1019-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        draft.remove("price_by_year");
        mvc.perform(post("/internal/quotes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/internal/quotes/Q-2026-1019-001").contentType(MediaType.APPLICATION_JSON).content("""
                        {"checks":[{"rule":"MARGIN","result":"PASS"}]}"""))
                .andExpect(status().isOk());
    }

    @Test
    void registerIsIdempotentContract() throws Exception {
        mvc.perform(post("/internal/quotes/Q-2026-1019-001/register"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.rfq_status").value("QUOTED"))
                .andExpect(jsonPath("$.outcome").value("CREATED"));
    }

    @Test
    void agentEventsMapToTaskStatus() throws Exception {
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CLARIFICATION_REQUIRED\",\"step\":\"RFQ_ANALYSIS\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"STEP_START\",\"step\":\"SUPPLY_CHECK\"}"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/internal/tasks/" + TASK + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOT_AN_EVENT\"}"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode fixture(String file) throws IOException {
        try (InputStream in = new ClassPathResource("stub/" + file).getInputStream()) {
            return json.readTree(in);
        }
    }
}
