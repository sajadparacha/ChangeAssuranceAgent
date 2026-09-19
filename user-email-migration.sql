-- OutfitSuggestor: add NOT NULL email column on USER table
-- Safe 3-phase migration (do not run all phases in one deploy)

-- =============================================================================
-- PHASE 1 — Add nullable column with temporary default
-- Deploy with app that can still run without requiring email.
-- =============================================================================
ALTER TABLE user
  ADD COLUMN email VARCHAR(255) DEFAULT 'pending@example.com';

-- Optional: make default apply only for new inserts during transition
-- (existing rows already get the DEFAULT from ADD COLUMN above)

-- =============================================================================
-- PHASE 2 — Backfill existing rows (run after app can collect real emails)
-- Replace placeholder emails with real values before enforcing NOT NULL.
-- =============================================================================
-- Example backfill (adjust source of truth for your environment):
-- UPDATE user
--    SET email = <real_email_from_source>
--  WHERE email IS NULL
--     OR email = 'pending@example.com';

-- Verification before Phase 3:
-- SELECT COUNT(*) AS remaining_placeholders
--   FROM user
--  WHERE email IS NULL
--     OR email = 'pending@example.com';
-- Expect: 0

-- =============================================================================
-- PHASE 3 — Enforce NOT NULL (only after backfill verification)
-- =============================================================================
ALTER TABLE user
  MODIFY COLUMN email VARCHAR(255) NOT NULL;

-- Optional: drop temporary default once application always supplies email
-- ALTER TABLE user ALTER COLUMN email DROP DEFAULT;

-- =============================================================================
-- ROLLBACK notes (not executed as part of forward migration)
-- Phase 1–2: ALTER TABLE user DROP COLUMN email;
-- Phase 3:   ALTER TABLE user MODIFY COLUMN email VARCHAR(255) NULL;
--            (or DROP COLUMN if fully reverting)
-- =============================================================================
