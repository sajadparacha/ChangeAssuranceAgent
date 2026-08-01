# Sample test case — AI Change Assurance Agent

Use these artifacts to run a **complete end-to-end review** (submit → tools → clarification if needed → report).

## Package: management demo (expected NO_GO / clarification)

This matches the product demo scenario: description says “minor package body,” but SQL changes package **spec + body**, **alters a table**, and **updates all rows**.

| UI / API field | File or value |
|----------------|---------------|
| Application name | `billing-service` |
| Change title | `Package body performance tune` |
| Change type | `PLSQL` |
| Target environment | `PROD` |
| Implementation window | `2026-08-02 02:00–04:00 UTC` |
| Change description | contents of `01-change-description.txt` |
| Deployment plan | contents of `02-deployment-plan.txt` |
| Rollback plan | contents of `03-rollback-plan.txt` |
| Test evidence | contents of `04-test-evidence.txt` |
| SQL file | `05-deploy.sql` |

### Expected agent behavior

1. Classifies as **MIXED** (PL/SQL + schema + data)
2. Runs completeness, SQL safety, rollback, test, consistency tools
3. Finds understated scope, incomplete rollback, UPDATE without WHERE, etc.
4. Often pauses for **one clarification** question
5. Ends with **`NO_GO_RECOMMENDED`** or **`INSUFFICIENT_INFORMATION`** (not `GO`)
6. Report marks risk scenarios as hypotheses; human approval required

---

## How to submit in the UI

1. Open http://localhost:4200  
2. Paste each text file into the matching form field  
3. Upload `05-deploy.sql`  
4. Click **Submit for review**  
5. Follow progress → answer clarification if prompted → open the report  

---

## How to submit via API (curl)

From this directory:

```bash
./submit.sh
```

Or manually:

```bash
curl -s -X POST "http://localhost:8080/api/v1/change-reviews" \
  -F "applicationName=billing-service" \
  -F "changeTitle=Package body performance tune" \
  -F "changeType=PLSQL" \
  -F "targetEnvironment=PROD" \
  -F "implementationWindow=2026-08-02 02:00-04:00 UTC" \
  -F "changeDescription=$(cat 01-change-description.txt)" \
  -F "deploymentPlan=$(cat 02-deployment-plan.txt)" \
  -F "rollbackPlan=$(cat 03-rollback-plan.txt)" \
  -F "testEvidence=$(cat 04-test-evidence.txt)" \
  -F "sqlFile=@05-deploy.sql;type=text/plain"
```

Then open:

- Progress: `http://localhost:4200/reviews/{reviewId}`  
- Report: `http://localhost:4200/reviews/{reviewId}/report`  
- API report: `http://localhost:8080/api/v1/change-reviews/{reviewId}/report`

If status is `WAITING_FOR_INFORMATION`:

```bash
# list gaps
curl -s "http://localhost:8080/api/v1/change-reviews/{reviewId}/information-gaps"

# answer (replace GAP-xxx)
curl -s -X POST "http://localhost:8080/api/v1/change-reviews/{reviewId}/answers" \
  -H "Content-Type: application/json" \
  -d '{"gapId":"GAP-001","answer":"Table and package specification changes were intentional. Rollback will restore prior package spec and body, and drop column last_refresh_ts after verifying no consumers."}'
```

---

## Second package (safer baseline)

See folder `safer-index-change/` for a milder package that still exercises the full pipeline with fewer critical findings.
