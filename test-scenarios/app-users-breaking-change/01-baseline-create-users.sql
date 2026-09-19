-- Baseline for Change Assurance demo: APP.USERS
-- Optional: run once in Oracle if you want the table to exist.
-- The Change Assurance Agent does NOT need this for the review demo.

CREATE TABLE "APP".USERS
(
     USER_NAME    VARCHAR2(128 BYTE),
     NETWORKID    VARCHAR2(20 BYTE),
     PASSWORD     VARCHAR2(20 BYTE)
)
TABLESPACE "USERS"
LOGGING;

-- Optional sample rows
INSERT INTO APP.USERS (USER_NAME, NETWORKID, PASSWORD)
VALUES ('alice', 'NET001', 'tempPass1');
INSERT INTO APP.USERS (USER_NAME, NETWORKID, PASSWORD)
VALUES ('bob', 'NET002', 'tempPass2');
COMMIT;
