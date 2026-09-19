-- Seed PL/SQL packages for Change Assurance Agent Oracle impact demos.
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET CONTAINER = FREEPDB1;

BEGIN
  EXECUTE IMMEDIATE q'[CREATE USER APP IDENTIFIED BY "AppDemoPass1" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS]';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1920 THEN RAISE; END IF; -- user exists
END;
/

BEGIN
  EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE, CREATE VIEW, CREATE TRIGGER TO APP';
END;
/

ALTER SESSION SET CURRENT_SCHEMA = APP;

BEGIN EXECUTE IMMEDIATE 'CREATE SEQUENCE APP.audit_events_seq START WITH 1 INCREMENT BY 1'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'CREATE TABLE APP.billing_accounts (id NUMBER PRIMARY KEY, account_name VARCHAR2(100), last_refresh_ts TIMESTAMP)'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'CREATE TABLE APP.audit_events (id NUMBER PRIMARY KEY, event_msg VARCHAR2(4000), created_at TIMESTAMP DEFAULT SYSTIMESTAMP)'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'CREATE TABLE APP.orders (id NUMBER PRIMARY KEY, status VARCHAR2(30))'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'CREATE TABLE APP.order_lines (id NUMBER PRIMARY KEY, order_id NUMBER, sku VARCHAR2(50))'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

CREATE OR REPLACE VIEW APP.billing_account_status AS
SELECT id, account_name, last_refresh_ts FROM APP.billing_accounts;

CREATE OR REPLACE PACKAGE APP.billing_pkg AS
  PROCEDURE refresh_accounts;
  FUNCTION get_status(p_id IN NUMBER) RETURN VARCHAR2;
END billing_pkg;
/

CREATE OR REPLACE PACKAGE BODY APP.billing_pkg AS
  PROCEDURE refresh_accounts IS
  BEGIN
    UPDATE APP.billing_accounts SET last_refresh_ts = SYSTIMESTAMP;
  END refresh_accounts;

  FUNCTION get_status(p_id IN NUMBER) RETURN VARCHAR2 IS
    v_name VARCHAR2(100);
  BEGIN
    SELECT account_name INTO v_name FROM APP.billing_accounts WHERE id = p_id;
    RETURN v_name;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN 'MISSING';
  END get_status;
END billing_pkg;
/

CREATE OR REPLACE PACKAGE APP.orders_pkg AS
  PROCEDURE place_order(p_id IN NUMBER);
  PROCEDURE cancel_order(p_id IN NUMBER);
END orders_pkg;
/

CREATE OR REPLACE PACKAGE BODY APP.orders_pkg AS
  PROCEDURE place_order(p_id IN NUMBER) IS
  BEGIN
    INSERT INTO APP.orders(id, status) VALUES (p_id, 'OPEN');
  END place_order;

  PROCEDURE cancel_order(p_id IN NUMBER) IS
  BEGIN
    UPDATE APP.orders SET status = 'CANCELLED' WHERE id = p_id;
  END cancel_order;
END orders_pkg;
/

CREATE OR REPLACE PACKAGE APP.audit_pkg AS
  PROCEDURE write_event(p_msg IN VARCHAR2);
END audit_pkg;
/

CREATE OR REPLACE PACKAGE BODY APP.audit_pkg AS
  PROCEDURE write_event(p_msg IN VARCHAR2) IS
  BEGIN
    INSERT INTO APP.audit_events(id, event_msg) VALUES (APP.audit_events_seq.NEXTVAL, p_msg);
  END write_event;
END audit_pkg;
/

CREATE OR REPLACE PACKAGE APP.api_billing_wrapper AS
  PROCEDURE refresh;
END api_billing_wrapper;
/

CREATE OR REPLACE PACKAGE BODY APP.api_billing_wrapper AS
  PROCEDURE refresh IS
  BEGIN
    APP.billing_pkg.refresh_accounts;
  END refresh;
END api_billing_wrapper;
/

CREATE OR REPLACE VIEW APP.v_billing_status AS
SELECT id, account_name, last_refresh_ts FROM APP.billing_accounts;

CREATE OR REPLACE PROCEDURE APP.batch_billing_caller AS
BEGIN
  APP.billing_pkg.refresh_accounts;
END;
/

-- Intentionally leave batch_billing_caller INVALID for health demos when desired:
-- ALTER PROCEDURE APP.batch_billing_caller COMPILE;
-- Or break it:
BEGIN
  EXECUTE IMMEDIATE 'CREATE OR REPLACE PROCEDURE APP.batch_billing_caller AS BEGIN APP.missing_pkg.x; END;';
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

CREATE OR REPLACE TRIGGER APP.trg_billing_accounts_ai
AFTER INSERT ON APP.billing_accounts
FOR EACH ROW
BEGIN
  APP.audit_pkg.write_event('account created ' || :NEW.id);
  IF :NEW.id IS NOT NULL THEN
    NULL; -- keeps BILLING_PKG reference surface for source search via wrapper/caller
  END IF;
END;
/

BEGIN
  DBMS_SCHEDULER.CREATE_JOB(
    job_name        => 'APP.BILLING_NIGHTLY_REFRESH',
    job_type        => 'PLSQL_BLOCK',
    job_action      => 'BEGIN APP.billing_pkg.refresh_accounts; END;',
    start_date      => SYSTIMESTAMP,
    repeat_interval => 'FREQ=DAILY;BYHOUR=2',
    enabled         => FALSE,
    comments        => 'Demo job for Change Assurance Agent'
  );
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -27477 THEN RAISE; END IF; -- job exists
END;
/

BEGIN
  DBMS_SCHEDULER.CREATE_JOB(
    job_name        => 'APP.BILLING_STATUS_PROBE',
    job_type        => 'PLSQL_BLOCK',
    job_action      => 'DECLARE v VARCHAR2(100); BEGIN v := APP.billing_pkg.get_status(1); END;',
    start_date      => SYSTIMESTAMP,
    repeat_interval => 'FREQ=HOURLY',
    enabled         => FALSE
  );
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -27477 THEN RAISE; END IF;
END;
/

COLUMN owner FORMAT A10
COLUMN object_name FORMAT A30
COLUMN object_type FORMAT A15
COLUMN status FORMAT A10
SELECT owner, object_name, object_type, status
FROM all_objects
WHERE owner = 'APP' AND object_type IN ('PACKAGE', 'PACKAGE BODY', 'PROCEDURE', 'VIEW', 'TRIGGER')
ORDER BY object_name, object_type;

SELECT name AS package_name, referenced_name, referenced_type
FROM all_dependencies
WHERE owner = 'APP' AND name = 'BILLING_PKG' AND type IN ('PACKAGE', 'PACKAGE BODY')
ORDER BY referenced_type, referenced_name;

SELECT owner, name, type
FROM all_dependencies
WHERE referenced_owner = 'APP' AND referenced_name = 'BILLING_PKG'
ORDER BY type, name;

SELECT owner, job_name, enabled, state
FROM all_scheduler_jobs
WHERE owner = 'APP' AND UPPER(job_action) LIKE '%BILLING_PKG%'
ORDER BY job_name;

EXIT;
