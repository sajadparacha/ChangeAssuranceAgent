# Test scenario: USERS_PKG.CLOSE_USER_ACCOUNT package impact

End-to-end change-assurance demo for a **PL/SQL package body** change that already has
cross-object blast radius in Oracle (`USERS`, `USER_ORDER_DETAILS`, `ORDER_LINES`,
`ORDERS_PKG`, `AUDIT_PKG`, plus dependent `CLOSE_USER_ACCOUNT_JOB`).

The proposed change understates risk in the description, but the deploy script:
- hard-deletes order detail / order line rows (main variant)
- adds a **new** call to `BILLING_PKG.REFRESH_ACCOUNTS`

**SQL is analyzed only — the agent does not execute the change script against Oracle.**
Catalog facts (dependents / dependencies) come from read-only Oracle metadata when
`CHANGEASSURANCE_DB_METADATA_MODE=oracle`.

---

## Prerequisites

1. Backend on http://localhost:8080 and frontend on http://localhost:4200  
   (`./scripts/run-dev.sh` from repo root)
2. Docker Oracle with the current APP baseline already deployed (for **oracle** catalog mode):
   - `test-scenarios/app-users-breaking-change/03-users-pkg-and-view.sql`
   - `test-scenarios/app-users-breaking-change/04-user-order-details.sql`
   - demo packages from `scripts/oracle-seed-demo-packages.sql` (`ORDERS_PKG`, `AUDIT_PKG`, `BILLING_PKG`)
3. App catalog mode:
   - **fake** (default): `USERS_PKG` is seeded in `FakeDatabaseMetadataAdapter` — works without Oracle
   - **oracle** (live catalog): set env vars and restart backend (`.env` is loaded by `./scripts/run-dev.sh`):

```bash
CHANGEASSURANCE_DB_METADATA_MODE=oracle
CHANGEASSURANCE_DB_JDBC_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
CHANGEASSURANCE_DB_USERNAME=APP
CHANGEASSURANCE_DB_PASSWORD=AppDemoPass1
CHANGEASSURANCE_DB_DEFAULT_OWNER=APP
```

On startup the backend log should show either:
- `Configuring FakeDatabaseMetadataAdapter` or
- `Configuring Oracle JDBC DatabaseMetadataPort`

If the report says “fake catalog” / `DBI-001`, the process is not in oracle mode (or the package name/owner is wrong).

Verify baseline (optional, read-only):

```bash
docker exec -i codex-oracle-impact-demo bash -lc \
  'sqlplus -s APP/AppDemoPass1@//localhost:1521/FREEPDB1' \
  < test-scenarios/users-pkg-close-account-impact/01-verify-baseline.sql
```

Do **not** run `02-change-*.sql` against a shared database unless you intend to apply it.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `01-verify-baseline.sql` | Read-only Oracle checks for package / table / FK baseline |
| `02-change-close-user-account.sql` | **Upload this** — body change with deletes + `BILLING_PKG` |
| `02-change-close-user-account-mild.sql` | Safer alternate (soft updates + `BILLING_PKG` only) |
| `change-description.txt` | Intentionally understates risk |
| `deployment-plan.txt` | Lightweight deploy notes |
| `rollback-plan.txt` | Weak rollback (no data restore) |
| `test-evidence.txt` | Insufficient regression evidence |
| `submit-via-api.sh` | curl submit with `packageName=USERS_PKG` |
| `README.md` | This guide |

---

## How to run (UI — recommended)

1. Open http://localhost:4200/
2. Choose **Package catalog impact** (or Change package / SQL and also set package fields)
3. Fill:

| Field | Value |
|--------|--------|
| Package name | `USERS_PKG` |
| Schema owner | `APP` |
| Application name | `APP` |
| Change title | `USERS_PKG close-account body tweak` |
| Change description | paste from `change-description.txt` |
| SQL / PL/SQL change script | attach **`02-change-close-user-account.sql`** |

4. Run the review and open the report / Download HTML

If the UI path you use does not send `packageName`, prefer `./submit-via-api.sh`.

---

## How to run (API)

```bash
cd test-scenarios/users-pkg-close-account-impact
chmod +x submit-via-api.sh
./submit-via-api.sh
```

Milder upload:

```bash
./submit-via-api.sh ./02-change-close-user-account-mild.sql
```

---

## What you should see

| Signal | Why |
|--------|-----|
| Package target `APP.USERS_PKG` | Explicit `packageName` / derived from script |
| Deploy change kind **body** (not spec) | `CREATE OR REPLACE PACKAGE BODY` only |
| Catalog dependencies | `ORDERS_PKG`, `AUDIT_PKG`, `USERS`, `USER_ORDER_DETAILS`, `ORDER_LINES`, `V_USERS_REPORT`, … |
| New dependency risk | Script introduces `BILLING_PKG` usage |
| Dependent | `CLOSE_USER_ACCOUNT_JOB` |
| Weak test / rollback evidence | Gaps vs destructive deletes (main variant) |
| Human approval | Still **required** |

Report sections to check:

1. **Executive assessment** (recommendation + overall package impact)
2. **Oracle catalog evidence** (dependencies / dependents / jobs / health)
3. **Findings** and **AI role** (honest contribution limits)
4. **Objects touched by the change script**

---

## Related baseline scripts

Already used to build this Oracle state:

- [`../app-users-breaking-change/03-users-pkg-and-view.sql`](../app-users-breaking-change/03-users-pkg-and-view.sql)
- [`../app-users-breaking-change/04-user-order-details.sql`](../app-users-breaking-change/04-user-order-details.sql)
- [`../../scripts/oracle-seed-demo-packages.sql`](../../scripts/oracle-seed-demo-packages.sql)
