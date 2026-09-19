-- Milder breaking-change variant for APP.USERS (no DROP TABLE).
-- Upload instead of 02-breaking-change.sql if preferred.
-- Still expect CRITICAL findings (unbounded UPDATE + TRUNCATE).

UPDATE APP.USERS SET PASSWORD = 'Reset123';

ALTER TABLE APP.USERS MODIFY (USER_NAME VARCHAR2(10 BYTE));

TRUNCATE TABLE APP.USERS;

COMMIT;
