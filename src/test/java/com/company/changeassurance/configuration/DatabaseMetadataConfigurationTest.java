package com.company.changeassurance.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.company.changeassurance.adapter.out.db.FakeDatabaseMetadataAdapter;
import com.company.changeassurance.adapter.out.db.OracleJdbcMetadataAdapter;
import com.company.changeassurance.application.port.out.DatabaseMetadataPort;

class DatabaseMetadataConfigurationTest {

    @Test
    void selectsFakeAdapterWhenModeIsFake() {
        ChangeAssuranceProperties properties = new ChangeAssuranceProperties(
                null,
                null,
                new ChangeAssuranceProperties.DbMetadata(
                        "fake", null, null, null, null, "APP"
                ),
                null
        );
        DatabaseMetadataPort port = new ChangeAssuranceConfiguration().databaseMetadataPort(properties);
        assertThat(port).isInstanceOf(FakeDatabaseMetadataAdapter.class);
        assertThat(port.catalogMode()).isEqualTo("fake");
    }

    @Test
    void buildsOracleAdapterWhenConfigured() {
        OracleJdbcMetadataAdapter adapter = new OracleJdbcMetadataAdapter(
                "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "APP",
                "AppDemoPass1",
                "oracle.jdbc.OracleDriver",
                "APP"
        );
        assertThat(adapter.catalogMode()).isEqualTo("oracle");
    }

    @Test
    void oracleModeFailsClosedWithoutJdbcUrl() {
        assertThatThrownBy(() -> new OracleJdbcMetadataAdapter(
                null, "APP", "x", "oracle.jdbc.OracleDriver", "APP"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC URL");
    }
}
