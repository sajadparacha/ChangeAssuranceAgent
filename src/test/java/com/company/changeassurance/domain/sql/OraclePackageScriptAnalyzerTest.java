package com.company.changeassurance.domain.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OraclePackageScriptAnalyzerTest {

    private final SqlScriptParser parser = new SqlScriptParser();

    @Test
    void detectsSpecAndBodyWithSchema() {
        String sql = """
                CREATE OR REPLACE PACKAGE app.billing_pkg AS
                  PROCEDURE p;
                END billing_pkg;
                /
                CREATE OR REPLACE PACKAGE BODY app.billing_pkg AS
                  PROCEDURE p IS BEGIN NULL; END;
                END billing_pkg;
                /
                """;
        PackageDeployDelta delta = OraclePackageScriptAnalyzer.analyze(parser.parse(sql, "pkg.sql"));
        assertThat(delta.specChanged()).isTrue();
        assertThat(delta.bodyChanged()).isTrue();
        assertThat(delta.changeKind()).isEqualTo("SPEC_AND_BODY");
        assertThat(delta.primaryPackageName()).isEqualTo("BILLING_PKG");
        assertThat(delta.primarySchemaOwner()).isEqualTo("APP");
        assertThat(delta.inferredFromScript()).isTrue();
    }

    @Test
    void detectsBodyOnly() {
        String sql = """
                CREATE OR REPLACE PACKAGE BODY demo_pkg AS
                  PROCEDURE p IS BEGIN NULL; END;
                END demo_pkg;
                /
                """;
        PackageDeployDelta delta = OraclePackageScriptAnalyzer.analyze(parser.parse(sql, "body.sql"));
        assertThat(delta.specChanged()).isFalse();
        assertThat(delta.bodyChanged()).isTrue();
        assertThat(delta.changeKind()).isEqualTo("BODY_ONLY");
        assertThat(delta.primaryPackageName()).isEqualTo("DEMO_PKG");
    }

    @Test
    void focusedOnFiltersToSubmittedPackage() {
        String sql = """
                CREATE OR REPLACE PACKAGE other_pkg AS
                  PROCEDURE p;
                END other_pkg;
                /
                """;
        PackageDeployDelta delta = OraclePackageScriptAnalyzer.analyze(parser.parse(sql, "o.sql"))
                .focusedOn("BILLING_PKG");
        assertThat(delta.specChanged()).isFalse();
        assertThat(delta.bodyChanged()).isFalse();
        assertThat(delta.primaryPackageName()).isEqualTo("BILLING_PKG");
    }

    @Test
    void dropPackageCountsAsSpecChange() {
        PackageDeployDelta delta = OraclePackageScriptAnalyzer.analyze(
                parser.parse("DROP PACKAGE app.old_pkg;", "drop.sql"));
        assertThat(delta.specChanged()).isTrue();
        assertThat(delta.primaryPackageName()).isEqualTo("OLD_PKG");
    }
}
