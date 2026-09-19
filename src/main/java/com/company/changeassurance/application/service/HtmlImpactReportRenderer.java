package com.company.changeassurance.application.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Renders a shareable HTML artifact from the assembled assurance report map.
 */
@Service
public class HtmlImpactReportRenderer {

    @SuppressWarnings("unchecked")
    public String render(Map<String, Object> report) {
        Map<String, Object> identification = castMap(report.get("identification"));
        Map<String, Object> databaseImpact = castMap(report.get("databaseImpact"));
        Map<String, Object> recommendation = castMap(report.get("recommendation"));
        Map<String, Object> riskAssessment = castMap(report.get("riskAssessment"));

        String reviewId = str(identification.get("reviewId"));
        String packageName = str(identification.get("packageName"));
        String schemaOwner = str(identification.get("schemaOwner"));
        String title = str(identification.get("changeTitle"));
        String overall = str(databaseImpact.get("overallImpact"));
        if (overall.isBlank()) {
            overall = str(riskAssessment.get("riskLevel"));
        }
        List<String> why = castStringList(databaseImpact.get("why"));
        List<String> testing = castStringList(databaseImpact.get("recommendedTesting"));
        if (testing.isEmpty()) {
            testing = castStringList(report.get("suggestedTests"));
        }
        List<String> limitations = castStringList(report.get("limitations"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        html.append("<title>Change Assurance Report ").append(esc(reviewId)).append("</title>");
        html.append("<style>");
        html.append(":root{--ink:#102833;--soft:#314650;--muted:#5a6b75;--accent:#0f5f75;--line:#d5e0e6;}");
        html.append("*{box-sizing:border-box;}");
        html.append("body{margin:0;font-family:Georgia,'Times New Roman',serif;color:var(--soft);");
        html.append("line-height:1.55;background:linear-gradient(180deg,#eef4f6,#f7fafb);}");
        html.append(".wrap{max-width:920px;margin:0 auto;padding:1.25rem 1rem 2.5rem;}");
        html.append("h1{font-size:clamp(1.5rem,3vw,2rem);color:var(--ink);margin:0 0 .35rem;}");
        html.append("h2{margin:1.5rem 0 .6rem;color:var(--ink);font-size:1.25rem;");
        html.append("border-bottom:1px solid var(--line);padding-bottom:.35rem;}");
        html.append("h3{margin:1rem 0 .4rem;color:var(--ink);font-size:1.05rem;}");
        html.append(".hero{background:#fff;border:1px solid rgba(15,95,117,.14);border-radius:12px;");
        html.append("padding:1.1rem 1.2rem;box-shadow:0 8px 24px rgba(16,40,51,.05);}");
        html.append(".panel{background:#fff;border:1px solid rgba(15,95,117,.12);border-radius:12px;");
        html.append("padding:1rem 1.1rem;margin-top:1rem;}");
        html.append(".meta{color:var(--muted);font-size:.92rem;}");
        html.append(".eyebrow{text-transform:uppercase;letter-spacing:.08em;font-size:.72rem;");
        html.append("font-weight:700;color:var(--accent);margin:0 0 .35rem;font-family:system-ui,sans-serif;}");
        html.append(".pill{display:inline-block;padding:.25rem .65rem;font-weight:700;border-radius:6px;");
        html.append("border:1px solid currentColor;font-family:system-ui,sans-serif;font-size:.9rem;}");
        html.append(".HIGH,.CRITICAL{background:#fdeceb;color:#8b1e1e;}");
        html.append(".MEDIUM{background:#fff4d9;color:#8a5a00;}");
        html.append(".LOW{background:#e8f6ec;color:#1f6b3a;}");
        html.append("ul{padding-left:1.15rem;margin:.4rem 0;} li{margin:.25rem 0;}");
        html.append("table{border-collapse:collapse;width:100%;margin-top:.5rem;font-family:system-ui,sans-serif;}");
        html.append("th,td{border:1px solid var(--line);padding:.45rem .55rem;text-align:left;font-size:.88rem;}");
        html.append("th{background:#f3f7f8;color:var(--ink);} ");
        html.append("@media (max-width:640px){body{font-size:15px;}.wrap{padding:.85rem .75rem 2rem;}");
        html.append("table{display:block;overflow-x:auto;}}");
        html.append("</style></head><body><div class=\"wrap\">");
        html.append("<div class=\"hero\">");
        html.append("<p class=\"eyebrow\">AI Change Assurance Agent</p>");
        html.append("<h1>").append(esc(title.isBlank() ? "Change assurance report" : title)).append("</h1>");
        html.append("<p class=\"meta\">Review ").append(esc(reviewId));
        if (!packageName.isBlank()) {
            html.append(" · Target ");
            if (!schemaOwner.isBlank()) {
                html.append(esc(schemaOwner)).append('.');
            }
            html.append(esc(packageName));
        }
        html.append("</p></div>");

        html.append("<div class=\"panel\"><h2>Executive assessment</h2>");
        html.append("<p>Overall assessment: <span class=\"pill ").append(esc(overall)).append("\">")
                .append(esc(overall.isBlank() ? "UNKNOWN" : overall)).append("</span></p>");
        Map<String, Object> deployChange = castMap(databaseImpact.get("deployChange"));
        String changeKind = str(deployChange.get("changeKind"));
        if (!changeKind.isBlank() && !"UNKNOWN".equals(changeKind)) {
            html.append("<p class=\"meta\">Deploy script change: ").append(esc(changeKind));
            if (Boolean.TRUE.equals(deployChange.get("specChanged"))) {
                html.append(" (specification / API)");
            } else if (Boolean.TRUE.equals(deployChange.get("bodyChanged"))) {
                html.append(" (body only)");
            }
            html.append("</p>");
        }
        html.append("<p>").append(esc(str(report.get("executiveSummary")))).append("</p>");
        html.append("<p class=\"meta\">Recommendation: ")
                .append(esc(str(recommendation.get("value"))))
                .append(" · Human approval required</p></div>");

        html.append("<div class=\"panel\"><h2>Why?</h2><ul>");
        if (why.isEmpty()) {
            html.append("<li>No deterministic impact reasons were recorded.</li>");
        } else {
            for (String item : why) {
                html.append("<li>").append(esc(item)).append("</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"panel\"><h2>Recommended testing</h2><ul>");
        if (testing.isEmpty()) {
            html.append("<li>No regression scope listed.</li>");
        } else {
            for (String item : testing) {
                html.append("<li>").append(esc(item)).append("</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"panel\"><h2>Evidence (catalog)</h2>");
        appendObjectTable(html, "Dependencies (used by package)", castMapList(databaseImpact.get("dependencies")));
        appendObjectTable(html, "Direct dependents", castMapList(databaseImpact.get("dependents")));
        appendObjectTable(html, "Transitive dependents", castMapList(databaseImpact.get("transitiveDependents")));
        appendJobsTable(html, castMapList(databaseImpact.get("schedulerJobs")));
        appendObjectTable(html, "Invalid related objects", castMapList(databaseImpact.get("invalidRelatedObjects")));
        appendSourceTable(html, castMapList(databaseImpact.get("sourceReferences")));
        html.append("</div>");

        html.append("<div class=\"panel\"><h2>AI contribution</h2>");
        Map<String, Object> aiContribution = castMap(report.get("aiContribution"));
        html.append("<p>").append(esc(str(aiContribution.get("summary")))).append("</p>");
        html.append("<p class=\"meta\">Provider ")
                .append(esc(str(aiContribution.get("selectedProvider"))))
                .append(" · Model ")
                .append(esc(str(aiContribution.get("selectedModel"))))
                .append(" · Live model tasks ")
                .append(esc(str(aiContribution.get("liveModelTasks"))))
                .append("</p>");
        List<Map<String, Object>> aiTasks = castMapList(aiContribution.get("tasks"));
        if (aiTasks.isEmpty()) {
            html.append("<p class=\"meta\">No AI task details recorded.</p>");
        } else {
            html.append("<table><thead><tr><th>Task</th><th>How</th><th>Purpose / outcome</th></tr></thead><tbody>");
            for (Map<String, Object> task : aiTasks) {
                html.append("<tr><td>").append(esc(str(task.get("taskName")))).append("</td><td>")
                        .append(esc(str(task.get("howExecuted")))).append("</td><td>")
                        .append(esc(str(task.get("purpose")))).append(" — ")
                        .append(esc(str(task.get("outcome")))).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        List<String> cannotOverride = castStringList(aiContribution.get("cannotOverride"));
        if (!cannotOverride.isEmpty()) {
            html.append("<h3>AI cannot override</h3><ul>");
            for (String item : cannotOverride) {
                html.append("<li>").append(esc(item)).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div>");

        html.append("<div class=\"panel\"><h2>Limitations</h2><ul>");
        for (String item : limitations) {
            html.append("<li>").append(esc(item)).append("</li>");
        }
        html.append("</ul>");
        html.append("<p class=\"meta\">Generated for human decision support. No DML/DDL was executed.</p>");
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static void appendObjectTable(StringBuilder html, String heading, List<Map<String, Object>> rows) {
        html.append("<h3>").append(esc(heading)).append("</h3>");
        if (rows.isEmpty()) {
            html.append("<p class=\"meta\">None</p>");
            return;
        }
        html.append("<table><thead><tr><th>Object</th><th>Type</th><th>Status</th><th>Depth</th></tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr><td>").append(esc(str(row.get("qualifiedName")))).append("</td><td>")
                    .append(esc(str(row.get("objectType")))).append("</td><td>")
                    .append(esc(str(row.get("status")))).append("</td><td>")
                    .append(esc(str(row.get("depth")))).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private static void appendJobsTable(StringBuilder html, List<Map<String, Object>> rows) {
        html.append("<h3>Scheduler jobs</h3>");
        if (rows.isEmpty()) {
            html.append("<p class=\"meta\">None</p>");
            return;
        }
        html.append("<table><thead><tr><th>Job</th><th>Type</th><th>Enabled</th><th>State</th><th>Action</th></tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr><td>").append(esc(str(row.get("qualifiedName")))).append("</td><td>")
                    .append(esc(str(row.get("jobType")))).append("</td><td>")
                    .append(esc(str(row.get("enabled")))).append("</td><td>")
                    .append(esc(str(row.get("state")))).append("</td><td>")
                    .append(esc(str(row.get("actionExcerpt")))).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private static void appendSourceTable(StringBuilder html, List<Map<String, Object>> rows) {
        html.append("<h3>Source references</h3>");
        if (rows.isEmpty()) {
            html.append("<p class=\"meta\">None</p>");
            return;
        }
        html.append("<table><thead><tr><th>Object</th><th>Line</th><th>Excerpt</th></tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            html.append("<tr><td>").append(esc(str(row.get("qualifiedName")))).append("</td><td>")
                    .append(esc(str(row.get("line")))).append("</td><td>")
                    .append(esc(str(row.get("excerpt")))).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMapList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
