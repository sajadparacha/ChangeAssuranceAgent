package com.company.changeassurance.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "changeassurance.storage.root=${java.io.tmpdir}/caa-web-test",
        "changeassurance.ai.mode=fake"
})
class ChangeReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitAndRetrieveReview() throws Exception {
        MockMultipartFile sql = new MockMultipartFile(
                "sqlFile", "change.sql", "text/plain", "UPDATE t SET a=1 WHERE id=1;".getBytes()
        );

        MvcResult submitted = mockMvc.perform(multipart("/api/v1/change-reviews")
                        .file(sql)
                        .param("applicationName", "app")
                        .param("changeTitle", "Safe update")
                        .param("changeDescription", "Update one row")
                        .param("targetEnvironment", "UAT")
                        .param("implementationWindow", "now")
                        .param("deploymentPlan", "Run script. Owner: DBA. Verify count.")
                        .param("rollbackPlan", "Restore prior value for id=1. Verify. Environment UAT.")
                        .param("testEvidence", "Covered t object. Negative path tested. Rollback tested.")
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reviewId").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(submitted.getResponse().getContentAsString());
        String reviewId = body.get("reviewId").asText();

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("app"));

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId + "/findings"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.humanApprovalRequired").value(true));
    }

    @Test
    void rejectsMissingApplicationName() throws Exception {
        mockMvc.perform(multipart("/api/v1/change-reviews")
                        .param("changeTitle", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundReturnsErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/change-reviews/REV-DOES-NOT-EXIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
