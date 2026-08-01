package com.company.changeassurance.application.port.out;

import java.util.List;

import com.company.changeassurance.domain.sql.SqlParseResult;

/**
 * Port for SQL/PLSQL static analysis. Must never execute SQL.
 */
public interface SqlAnalysisPort {

    SqlAnalysisResult analyze(String sqlScriptContent, String filename);

    SqlParseResult parseDetailed(String sqlScriptContent, String filename);

    record SqlAnalysisResult(
            boolean fullySupported,
            String summary,
            List<String> affectedObjectNames,
            List<String> unsupportedConstructs,
            List<String> detectedOperations
    ) {
    }
}
