# Test scenario: APP.USERS breaking change

End-to-end change-assurance demo for a **table** change (not package catalog impact).

It shows how uploading destructive SQL against `"APP"."USERS"` produces an easy-to-read report with critical findings (DROP, unbounded UPDATE, weak rollback/docs).

**SQL is analyzed only — the agent does not execute this script against Oracle.**

---

## Prerequisites

1. Backend running on http://localhost:8080  
2. Frontend running on http://localhost:4200  

```bash
# from repo root
./scripts/run-dev.sh
```

Or start each side separately (`mvn spring-boot:run` and `cd frontend && npm start`).

Oracle is **not required** for this scenario. Findings come from static SQL + evidence checks.  
(If you already created `APP.USERS` in Oracle, that is fine — do **not** run the breaking script there unless you intend to.)

---

## Files in this folder

| File | Purpose |
|------|---------|
| `01-baseline-create-users.sql` | Matches your table DDL (optional; for Oracle setup only) |
| `02-breaking-change.sql` | **Upload this** as the change script |
| `02-breaking-change-mild.sql` | Safer alternate (TRUNCATE instead of DROP TABLE) |
| `change-description.txt` | Intentionally understates risk |
| `deployment-plan.txt` | Weak deployment plan |
| `rollback-plan.txt` | Effectively no rollback |
| `test-evidence.txt` | Insufficient test evidence |
| `submit-via-api.sh` | Optional curl submit using these files |
| `README.md` | This guide |

---

## How to run (UI — recommended)

1. Open http://localhost:4200/
2. Choose review type: **Change package / SQL**
3. Fill the form:

| Field | Value |
|--------|--------|
| Application name | `APP` |
| Change title | `Minor USERS table cleanup` |
| Change description | paste from `change-description.txt` |
| SQL / PL/SQL change script | attach **`02-breaking-change.sql`** |

> Change type / target environment / implementation window, and deployment / rollback / test evidence,
> are currently hidden in the UI (defaults / empty are used).
> Use `submit-via-api.sh` if you want the full evidence package included.

4. Click **Run change assurance review**
5. Open the report when the review completes (or use **Download HTML**)

---

## How to run (API)

From this folder:

```bash
chmod +x submit-via-api.sh
./submit-via-api.sh
```

Or manually:

```bash
curl -s -X POST http://localhost:8080/api/v1/change-reviews \
  -F "applicationName=APP" \
  -F "changeTitle=Minor USERS table cleanup" \
  -F "changeDescription=$(cat change-description.txt)" \
  -F "changeType=MIXED" \
  -F "targetEnvironment=UAT" \
  -F "implementationWindow=tonight" \
  -F "deploymentPlan=$(cat deployment-plan.txt)" \
  -F "rollbackPlan=$(cat rollback-plan.txt)" \
  -F "testEvidence=$(cat test-evidence.txt)" \
  -F "sqlFile=@02-breaking-change.sql;type=text/plain"
```

Then:

- JSON report: `GET /api/v1/change-reviews/{reviewId}/report`
- HTML report: `GET /api/v1/change-reviews/{reviewId}/report.html`
- UI: http://localhost:4200/reviews/{reviewId}

---

## What you should see

Expect the report to highlight roughly:

| Signal | Why |
|--------|-----|
| `SQL-001` CRITICAL | `DROP TABLE APP.USERS` |
| `SQL-004` CRITICAL | `UPDATE` without `WHERE` |
| `SQL-006` / `SQL-007` | `COMMIT` and/or hard-coded password-like literal |
| `SQL-010` | Multiple objects / statements affected |
| Completeness / rollback / consistency findings | Weak plans vs destructive SQL |
| Recommendation | Typically **NO_GO** or insufficient information |
| Human approval | Still **required** |

Report UI sections to check first:

1. **Executive assessment** (recommendation + risk)
2. **Findings** (ordered by severity)
3. **Objects touched by the change script** (`USERS`, etc.)

Package catalog “Why / Oracle evidence” sections should **not** dominate this scenario (no package target).

---

## Optional: create baseline table in Oracle

Only if you want the real table to exist (not needed for the agent demo):

```bash
# in SQL*Plus / SQLcl / SQL Developer, as a privileged user
@01-baseline-create-users.sql
```

Do **not** execute `02-breaking-change.sql` against a shared database.

---

## Milder variant

If you prefer not to include `DROP TABLE` in the uploaded script, use `02-breaking-change-mild.sql` instead.  
You should still get critical findings from `TRUNCATE` and unbounded `UPDATE`.
