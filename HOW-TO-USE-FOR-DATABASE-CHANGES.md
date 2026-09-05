# How to Use Change Assurance Agent for Database Changes

## Quick Start

Your Change Assurance Agent is **running on http://localhost:8080**

## What It Does for Database Changes

✅ **Analyzes SQL statements** for safety issues  
✅ **Checks completeness** of deployment/rollback plans  
✅ **Validates test coverage** for affected database objects  
✅ **Identifies consistency issues** between SQL, deployment, and rollback plans  
✅ **Calculates risk scores** and provides go/no-go recommendations  
✅ **Generates failure scenarios** to help you think through what could go wrong

## Your Use Case: Adding NOT NULL Column

### The Problem You're Solving

You want to add a NOT NULL column to the `user` table in your OutfitSuggestor app, and you need to know:

1. **Database Impact:** What happens to existing rows?
2. **Application Impact:** Which code will break?
3. **Migration Strategy:** How to safely add the constraint?
4. **Risk Assessment:** Is this safe for production?

### Example Request

Create a SQL file (`user-email-migration.sql`):

```sql
-- Phase 1: Add column with default
ALTER TABLE user ADD COLUMN email VARCHAR(255) DEFAULT 'pending@example.com';

-- Phase 2: Application populates emails for new users (code deployment)

-- Phase 3: After backfill, add NOT NULL constraint
ALTER TABLE user MODIFY COLUMN email VARCHAR(255) NOT NULL;
```

Submit the analysis request:

```bash
curl -X POST http://localhost:8080/api/v1/change-reviews \
  -F "applicationName=OutfitSuggestor" \
  -F "changeTitle=Add NOT NULL email column - 3 phase migration" \
  -F "changeDescription=Adding email column for notifications. 10,000 existing users need backfill." \
  -F "changeType=DATABASE_SCHEMA" \
  -F "targetEnvironment=Production" \
  -F "deploymentPlan=Phase 1: Add column with default value
Phase 2: Deploy app code to populate emails for new registrations
Phase 3: Run backfill script for existing 10K users (2 weeks)
Phase 4: Add NOT NULL constraint after backfill verification
Objects: USER table (add column)" \
  -F "rollbackPlan=Phase 1-3: DROP COLUMN email, redeploy old app version
Phase 4: Remove NOT NULL constraint if issues found
Verification: Check user registration still works
Objects: USER table" \
  -F "testEvidence=✅ Unit Tests: UserService create/update methods - 15 tests PASS
✅ Integration: User registration with email validation - PASS
✅ Performance: Load tested 50K records, <50ms insert time - PASS  
✅ Negative Tests: Attempted NULL insertion after constraint - correctly rejected
✅ Rollback: Successfully dropped column in QA - verified app still works
Objects tested: USER table, UserService, AuthController
Known Risk: 10K existing records need backfill before Phase 4" \
  -F "sqlFile=@user-email-migration.sql"
```

### What the Agent Will Tell You

The agent will analyze and report:

#### 1. **SQL Safety Issues**
- "ALTER TABLE with NOT NULL on existing data will FAIL"
- "Need default value or two-phase approach"
- Detected objects: `USER` table

#### 2. **Completeness Checks**
- ✅ Deployment plan provided
- ✅ Rollback plan provided
- ✅ Test evidence provided
- ⚠️  Missing: explicit verification steps

#### 3. **Consistency Analysis**
- ✅ SQL mentions USER table
- ✅ Deployment plan mentions USER table
- ✅ Rollback plan mentions USER table
- ⚠️  Test evidence should explicitly reference USER table

#### 4. **Risk Assessment**
- Overall Risk: HIGH (schema change on table with data)
- Risk Score: Based on multiple factors
- Recommendation: GO/NO_GO with specific actions needed

#### 5. **Required Actions**
The agent will list specific items like:
- "Add post-deployment verification: Query sample users to confirm email populated"
- "Add rollback verification: Test user registration after rollback"
- "Describe negative test: What happens if API receives user without email?"

## Checking Results

### Get the full analysis:

```bash
# Replace REV-2026-00002 with the reviewId from the POST response
curl -s http://localhost:8080/api/v1/change-reviews/REV-2026-00002 | python3 -m json.tool | less
```

### View in Swagger UI:

Open http://localhost:8080/swagger-ui.html in your browser for interactive API testing.

### View in Frontend (if Angular app is running):

```bash
cd frontend
npm install
npm start
# Open http://localhost:4200
```

## Common Database Change Patterns

### Pattern 1: Adding NOT NULL Column (Your Case)

**Problem:** Existing rows don't have values  
**Solution:** Three-phase deployment
1. Add column with DEFAULT
2. Backfill existing data
3. Add NOT NULL constraint

**Agent Will Check:**
- Migration strategy included?
- Backfill plan described?
- Rollback covers each phase?

### Pattern 2: Renaming Column

**Problem:** Application code references old name  
**Solution:** Two-phase with alias
1. Add new column, copy data
2. Update application to use new column
3. Drop old column after verification

**Agent Will Check:**
- Both columns handled in deployment?
- Application deployment timing clear?
- Data copy strategy safe?

### Pattern 3: Changing Data Type

**Problem:** Existing data may not convert  
**Solution:** Safe migration with validation
1. Add new column with new type
2. Migrate data with validation
3. Switch application to new column
4. Drop old column

**Agent Will Check:**
- Data conversion logic provided?
- Validation of converted data?
- Fallback if conversion fails?

## What Makes a Good Submission

### ✅ Complete Submission Includes:

1. **SQL File** (required)
   - All SQL statements
   - Comments explaining purpose
   - Phase markers for multi-step changes

2. **Deployment Plan** (required)
   - Step-by-step execution order
   - Timing windows
   - List of objects modified
   - Dependencies between steps
   - Post-deployment verification

3. **Rollback Plan** (required)
   - How to undo each step
   - Data recovery strategy
   - Rollback verification steps
   - All modified objects listed

4. **Test Evidence** (required)
   - Unit test results
   - Integration test results
   - Load/performance test results
   - Negative test scenarios
   - Rollback testing confirmation
   - Explicitly mention database objects tested

5. **Additional Context** (optional but helpful)
   - Current production data volume
   - Known risks and mitigations
   - Business impact if change fails
   - Dependencies on other systems

## Understanding the Results

### Severity Levels:

- **CRITICAL:** Must fix before deployment (e.g., missing rollback)
- **HIGH:** Strong recommendation to address (e.g., missing tests)
- **MEDIUM:** Should address (e.g., missing verification steps)
- **LOW:** Nice to have (e.g., additional documentation)

### Recommendations:

- **GO_RECOMMENDED:** All critical items addressed, low risk
- **CONDITIONAL_GO:** Some issues, but manageable with noted precautions
- **NO_GO_RECOMMENDED:** Critical issues found, needs more work before approval

## Current Limitations

⚠️ **OpenAI API Not Connected:** 
- Currently using deterministic analysis only
- AI-powered features unavailable:
  - Change classification
  - Contextual risk scenarios
  - Natural language recommendations
  - Gap question generation

To enable full AI features:
1. Make repository private (done when you indicated)
2. Add OPENAI_API_KEY secret in Cursor Dashboard
3. Restart cloud agent

**However:** Deterministic analysis (SQL parsing, completeness checks, consistency validation) **still provides significant value** and catches most common issues!

## Tips for Your OutfitSuggestor Case

### Before Submission:

1. **Document your current state:**
   - How many users in production?
   - What's the table structure now?
   - Which APIs/services use the user table?

2. **Plan the migration:**
   - Can you add column with DEFAULT?
   - How long will backfill take?
   - Can you do it in maintenance window?

3. **Think about failures:**
   - What if backfill script crashes halfway?
   - What if new code deploys but column not added?
   - What if rollback needed after users added via new code?

### When You Get Results:

1. Address all CRITICAL and HIGH findings
2. Update your deployment/rollback plans
3. Add the specific verifications requested
4. Resubmit for a new analysis

### The agent is iterative!
You can submit multiple times as you refine your approach.

---

## Questions?

- **API Docs:** http://localhost:8080/swagger-ui.html
- **Health Check:** http://localhost:8080/actuator/health  
- **Review the code:** Check `/workspace` directory for implementation details

**The Change Assurance Agent helps you catch issues BEFORE they hit production!** 🛡️
