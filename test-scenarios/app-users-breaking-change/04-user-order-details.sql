-- Link table: APP.USER_ORDER_DETAILS
-- FKs: USER_ID -> APP.USERS(USER_ID), ORDER_ID -> APP.ORDERS(ID)
-- Connect as APP (APP/AppDemoPass1@FREEPDB1), run as script.

--------------------------------------------------------------------------------
-- 1) Ensure ORDERS exists (demo catalog table)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE APP.ORDERS (
      ID     NUMBER PRIMARY KEY,
      STATUS VARCHAR2(30)
    )
  ]';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- 2) Add USER_ID to USERS (surrogate PK) if missing
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE APP.USERS ADD (USER_ID NUMBER)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN RAISE; END IF; -- column already exists
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE APP.USERS_USER_ID_SEQ START WITH 1 INCREMENT BY 1 NOCACHE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- Backfill USER_ID for existing rows
BEGIN
  FOR r IN (
    SELECT ROWID AS rid
      FROM APP.USERS
     WHERE USER_ID IS NULL
     ORDER BY NETWORKID
  ) LOOP
    UPDATE APP.USERS
       SET USER_ID = APP.USERS_USER_ID_SEQ.NEXTVAL
     WHERE ROWID = r.rid;
  END LOOP;
  COMMIT;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE APP.USERS MODIFY (USER_ID NUMBER NOT NULL)';
EXCEPTION
  WHEN OTHERS THEN
    -- already NOT NULL, or empty table edge cases
    IF SQLCODE NOT IN (-1442, -1451) THEN
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE APP.USERS ADD CONSTRAINT USERS_PK PRIMARY KEY (USER_ID)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-2260, -2261) THEN RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- 3) USER_ORDER_DETAILS (user + order FKs + detail text)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE APP.USER_ORDER_DETAILS_SEQ START WITH 1 INCREMENT BY 1 NOCACHE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE APP.USER_ORDER_DETAILS (
      ID            NUMBER        NOT NULL,
      USER_ID       NUMBER        NOT NULL,
      ORDER_ID      NUMBER        NOT NULL,
      ORDER_DETAIL  VARCHAR2(4000),
      CONSTRAINT USER_ORDER_DETAILS_PK PRIMARY KEY (ID),
      CONSTRAINT UOD_USER_FK  FOREIGN KEY (USER_ID)  REFERENCES APP.USERS (USER_ID),
      CONSTRAINT UOD_ORDER_FK FOREIGN KEY (ORDER_ID) REFERENCES APP.ORDERS (ID)
    )
  ]';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

--------------------------------------------------------------------------------
-- 4) Sample data (idempotent-ish)
--------------------------------------------------------------------------------
MERGE INTO APP.ORDERS o
USING (
  SELECT 1001 AS id, 'OPEN' AS status FROM dual UNION ALL
  SELECT 1002, 'OPEN' FROM dual UNION ALL
  SELECT 1003, 'CLOSED' FROM dual
) s
ON (o.id = s.id)
WHEN NOT MATCHED THEN
  INSERT (id, status) VALUES (s.id, s.status);

MERGE INTO APP.USER_ORDER_DETAILS d
USING (
  SELECT u.user_id,
         1001 AS order_id,
         'Alice initial order - standard shipping' AS order_detail
    FROM APP.USERS u
   WHERE u.networkid = 'NET001'
  UNION ALL
  SELECT u.user_id,
         1002,
         'Bob bulk order - priority handling'
    FROM APP.USERS u
   WHERE u.networkid = 'NET002'
) s
ON (d.user_id = s.user_id AND d.order_id = s.order_id)
WHEN NOT MATCHED THEN
  INSERT (id, user_id, order_id, order_detail)
  VALUES (APP.USER_ORDER_DETAILS_SEQ.NEXTVAL, s.user_id, s.order_id, s.order_detail);

COMMIT;

-- Keep USERS_PKG valid after USERS DDL changes
ALTER PACKAGE APP.USERS_PKG COMPILE;
ALTER PACKAGE APP.USERS_PKG COMPILE BODY;

-- Optional reporting join example:
-- SELECT u.user_name, u.networkid, o.id AS order_id, o.status, d.order_detail
--   FROM APP.USER_ORDER_DETAILS d
--   JOIN APP.USERS u ON u.user_id = d.user_id
--   JOIN APP.ORDERS o ON o.id = d.order_id
--  ORDER BY u.user_name, o.id;