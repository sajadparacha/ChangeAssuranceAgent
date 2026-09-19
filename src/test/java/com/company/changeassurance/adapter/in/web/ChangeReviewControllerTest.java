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
        "changeassurance.ai.mode=fake",
        "changeassurance.db-metadata.mode=fake"
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
    void submitPackageNameOnlyReturnsDatabaseImpact() throws Exception {
        MvcResult submitted = mockMvc.perform(multipart("/api/v1/change-reviews")
                        .param("packageName", "BILLING_PKG"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reviewId").exists())
                .andReturn();

        String reviewId = objectMapper.readTree(submitted.getResponse().getContentAsString())
                .get("reviewId").asText();

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageName").value("BILLING_PKG"))
                .andExpect(jsonPath("$.changeTitle").value("DB impact: BILLING_PKG"));

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseImpact.available").value(true))
                .andExpect(jsonPath("$.databaseImpact.packageFound").value(true))
                .andExpect(jsonPath("$.databaseImpact.catalogMode").value("fake"))
                .andExpect(jsonPath("$.databaseImpact.blastRadius.dependentCount").value(4))
                .andExpect(jsonPath("$.databaseImpact.overallImpact").exists())
                .andExpect(jsonPath("$.databaseImpact.why").isArray())
                .andExpect(jsonPath("$.databaseImpact.recommendedTesting").isArray())
                .andExpect(jsonPath("$.limitations").isArray());

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId + "/report.html"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(html).contains("Overall assessment");
                    org.assertj.core.api.Assertions.assertThat(html).contains("BILLING_PKG");
                });
    }

    @Test
    void submitPackageSqlDerivesTargetAndDeployChange() throws Exception {
        String sql = """
                CREATE OR REPLACE PACKAGE BODY billing_pkg AS
                  PROCEDURE refresh_accounts IS BEGIN NULL; END;
                END billing_pkg;
                /
                """;
        MockMultipartFile sqlFile = new MockMultipartFile(
                "sqlFile", "billing_body.sql", "text/plain", sql.getBytes()
        );

        MvcResult submitted = mockMvc.perform(multipart("/api/v1/change-reviews")
                        .file(sqlFile))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reviewId").exists())
                .andReturn();

        String reviewId = objectMapper.readTree(submitted.getResponse().getContentAsString())
                .get("reviewId").asText();

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageName").value("BILLING_PKG"));

        mockMvc.perform(get("/api/v1/change-reviews/" + reviewId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseImpact.available").value(true))
                .andExpect(jsonPath("$.databaseImpact.packageFound").value(true))
                .andExpect(jsonPath("$.databaseImpact.deployChange.bodyChanged").value(true))
                .andExpect(jsonPath("$.databaseImpact.deployChange.specChanged").value(false))
                .andExpect(jsonPath("$.databaseImpact.deployChange.changeKind").value("BODY_ONLY"))
                .andExpect(jsonPath("$.databaseImpact.deployChange.inferredFromScript").value(true));
    }

    @Test
    void notFoundReturnsErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/change-reviews/REV-DOES-NOT-EXIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
