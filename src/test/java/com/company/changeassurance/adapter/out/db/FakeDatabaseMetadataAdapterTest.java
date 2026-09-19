package com.company.changeassurance.adapter.out.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.domain.db.PackageDbImpact;

class FakeDatabaseMetadataAdapterTest {

    private final FakeDatabaseMetadataAdapter adapter = new FakeDatabaseMetadataAdapter("APP");

    @Test
    void analyzesBillingPkgImpact() {
        PackageDbImpact impact = adapter.analyzeImpact(null, "billing_pkg");
        assertThat(impact.packageFound()).isTrue();
        assertThat(impact.catalogMode()).isEqualTo("fake");
        assertThat(impact.packageInfo().packageName()).isEqualTo("BILLING_PKG");
        assertThat(impact.packageInfo().owner()).isEqualTo("APP");
        assertThat(impact.procedures()).extracting(p -> p.name())
                .contains("REFRESH_ACCOUNTS", "GET_STATUS");
        assertThat(impact.dependencies()).extracting(d -> d.objectName())
                .contains("BILLING_ACCOUNTS");
        assertThat(impact.dependents()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(impact.dependentCountsByType()).containsKey("VIEW");
        assertThat(impact.schedulerJobs()).isNotEmpty();
        assertThat(impact.transitiveDependents()).isNotEmpty();
        assertThat(impact.sourceReferences()).isNotEmpty();
    }

    @Test
    void analyzesUsersPkgImpact() {
        PackageDbImpact impact = adapter.analyzeImpact("APP", "USERS_PKG");
        assertThat(impact.packageFound()).isTrue();
        assertThat(impact.packageInfo().packageName()).isEqualTo("USERS_PKG");
        assertThat(impact.procedures()).extracting(p -> p.name())
                .contains("CLOSE_USER_ACCOUNT", "CREATE_USER");
        assertThat(impact.dependencies()).extracting(d -> d.objectName())
                .contains("USERS", "USER_ORDER_DETAILS", "ORDERS_PKG", "AUDIT_PKG");
        assertThat(impact.dependents()).extracting(d -> d.objectName())
                .contains("CLOSE_USER_ACCOUNT_JOB");
        assertThat(adapter.listSchedulerJobsReferencing("APP", "USERS_PKG"))
                .extracting(j -> j.jobName())
                .contains("USERS_ACCOUNT_CLOSE_SWEEP");
        assertThat(adapter.searchSourceReferences("APP", "USERS_PKG", 20))
                .isNotEmpty();
    }

    @Test
    void returnsNotFoundForUnknownPackage() {
        PackageDbImpact impact = adapter.analyzeImpact("APP", "MISSING_PKG");
        assertThat(impact.packageFound()).isFalse();
        assertThat(impact.summary()).contains("MISSING_PKG");
    }

    @Test
    void findsSchedulerJobsAndInvalidHealthForBilling() {
        assertThat(adapter.listSchedulerJobsReferencing("APP", "BILLING_PKG"))
                .extracting(j -> j.jobName())
                .contains("BILLING_NIGHTLY_REFRESH", "BILLING_STATUS_PROBE");
        assertThat(adapter.listInvalidRelatedObjects("APP", "BILLING_PKG"))
                .extracting(o -> o.objectName())
                .contains("BATCH_BILLING_CALLER");
        assertThat(adapter.listInvalidRelatedObjects("APP", "AUDIT_PKG"))
                .extracting(o -> o.objectName())
                .contains("AUDIT_PKG");
    }

    @Test
    void searchesSourceReferences() {
        assertThat(adapter.searchSourceReferences("APP", "BILLING_PKG", 20))
                .isNotEmpty();
    }
}
