-- Change Assurance demo — intentionally destructive script for APP.USERS
-- Upload this file to the agent. Do NOT run against shared/prod Oracle unless intentional.
-- The agent analyzes this SQL statically; it does not execute it.

-- 1) Unbounded DML: changes every row
UPDATE APP.USERS SET PASSWORD = 'Reset123';

-- 2) Structural break: drops a column callers may depend on
ALTER TABLE APP.USERS DROP COLUMN NETWORKID;

-- 3) Hard break: drops the table
DROP TABLE APP.USERS CASCADE CONSTRAINTS;

-- 4) Explicit COMMIT in deploy script
COMMIT;
