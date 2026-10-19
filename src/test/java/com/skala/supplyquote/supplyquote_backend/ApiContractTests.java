package com.skala.supplyquote.supplyquote_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiContractTests {

    private static final String TASK = "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11";

    @Autowired
    MockMvc mvc;

    @Test
    void webGroupExposesRfqConfirmationAndApprovalContract() throws Exception {
        mvc.perform(get("/v3/api-docs/1-web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/rfqs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/rfqs/{rfqId}/analysis'].get").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/confirmation'].post").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/approval'].post").exists())
                .andExpect(jsonPath("$.paths['/api/ai/tasks/{taskId}/stream'].get").exists());
    }

    @Test
    void internalGroupExposesCatalogInventoryAndRegistrationContract() throws Exception {
        mvc.perform(get("/v3/api-docs/2-internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/models'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/models/{modelId}/bom'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}/inventory'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/parts/{partId}/substitutes'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/rfq-reviews'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/analysis-results'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/quotes'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/purchase-requests'].post").exists());
    }

    @Test
    void rfqCreateReturns202WithTaskId() throws Exception {
        mvc.perform(post("/api/rfqs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"HB-EV7 워터펌프","content":"# 견적요청서 (RFQ)"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists());
    }

    @Test
    void invalidRfqReturnsCommonErrorFormat() throws Exception {
        mvc.perform(post("/api/rfqs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"","content":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void analysisStubFollowsDatasetScenario() throws Exception {
        mvc.perform(get("/api/rfqs/101/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productMatch.selectedModel").value("EWP-12-B"))
                .andExpect(jsonPath("$.productMatch.quotedPriceKrw").value(37300))
                .andExpect(jsonPath("$.shortages[0].partId").value("P-MCU-G1"))
                .andExpect(jsonPath("$.shortages[0].shortageQty").value(500))
                .andExpect(jsonPath("$.review.issues.length()").value(7))
                .andExpect(jsonPath("$.review.customerQuestions.length()").value(5));
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
    void sseStreamUsesEventStreamContentType() throws Exception {
        mvc.perform(get("/api/ai/tasks/" + TASK + "/stream"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
