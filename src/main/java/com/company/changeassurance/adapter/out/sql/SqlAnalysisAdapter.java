package com.company.changeassurance.adapter.out.sql;

import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.SqlAnalysisPort;
import com.company.changeassurance.domain.sql.SqlParseResult;
import com.company.changeassurance.domain.sql.SqlScriptParser;

@Component
public class SqlAnalysisAdapter implements SqlAnalysisPort {

    private final SqlScriptParser parser = new SqlScriptParser();

    @Override
    public SqlAnalysisResult analyze(String sqlScriptContent, String filename) {
        SqlParseResult parsed = parser.parse(sqlScriptContent, filename);
        return new SqlAnalysisResult(
                parsed.fullySupported(),
                "Parsed " + parsed.statements().size() + " statement(s); unsupported="
                        + parsed.unsupportedConstructs().size(),
                parsed.affectedObjectNames().stream().toList(),
                parsed.unsupportedConstructs(),
                parsed.detectedOperations().stream().map(Enum::name).toList()
        );
    }

    @Override
    public SqlParseResult parseDetailed(String sqlScriptContent, String filename) {
        return parser.parse(sqlScriptContent, filename);
    }
}
