-- APP.USERS CRUD package + reporting view
-- Connect as APP (e.g. APP/AppDemoPass1@FREEPDB1), then run this whole file in SQL*Plus /
-- SQLcl / SQL Developer. Tools that ignore "/" may need "Execute as script".

--------------------------------------------------------------------------------
-- 1) Table (no-op if already present)
--------------------------------------------------------------------------------
BEGIN
  EXECUTE IMMEDIATE q'[
    CREATE TABLE APP.USERS (
      USER_NAME VARCHAR2(128 BYTE),
      NETWORKID VARCHAR2(20 BYTE),
      PASSWORD  VARCHAR2(20 BYTE)
    )
  ]';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN -- name is already used
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE
    'ALTER TABLE APP.USERS ADD CONSTRAINT USERS_NETWORKID_UK UNIQUE (NETWORKID)';
EXCEPTION
  WHEN OTHERS THEN
    -- already exists, or cannot validate due to duplicate NETWORKID values
    -- ORA-02260/02261/01408/02299
    IF SQLCODE NOT IN (-2260, -2261, -1408, -2299) THEN
      RAISE;
    END IF;
END;
/

--------------------------------------------------------------------------------
-- 2) Reporting view (no plaintext password)
--------------------------------------------------------------------------------
CREATE OR REPLACE VIEW APP.V_USERS_REPORT AS
SELECT
  u.USER_NAME,
  u.NETWORKID,
  CASE
    WHEN u.PASSWORD IS NULL OR LENGTH(TRIM(u.PASSWORD)) = 0 THEN 'N'
    ELSE 'Y'
  END AS PASSWORD_SET,
  NVL(LENGTH(u.PASSWORD), 0) AS PASSWORD_LENGTH,
  UPPER(u.USER_NAME) AS USER_NAME_UPPER,
  UPPER(u.NETWORKID) AS NETWORKID_UPPER
FROM APP.USERS u;

COMMENT ON TABLE APP.V_USERS_REPORT IS
  'Reporting view over APP.USERS - identity fields and password presence only (no secrets).';

--------------------------------------------------------------------------------
-- 3) Package specification
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE APP.USERS_PKG AS

  PROCEDURE CREATE_USER(
    p_user_name IN VARCHAR2,
    p_networkid IN VARCHAR2,
    p_password  IN VARCHAR2
  );

  PROCEDURE GET_USER(
    p_networkid IN  VARCHAR2,
    p_user_name OUT VARCHAR2,
    p_password  OUT VARCHAR2
  );

  -- Returns 1 if a row exists for NETWORKID, otherwise 0.
  FUNCTION USER_EXISTS(
    p_networkid IN VARCHAR2
  ) RETURN NUMBER;

  -- Partial update: only non-NULL arguments are applied. NETWORKID is the key.
  PROCEDURE UPDATE_USER(
    p_networkid IN VARCHAR2,
    p_user_name IN VARCHAR2 DEFAULT NULL,
    p_password  IN VARCHAR2 DEFAULT NULL
  );

  PROCEDURE UPDATE_PASSWORD(
    p_networkid IN VARCHAR2,
    p_password  IN VARCHAR2
  );

  PROCEDURE DELETE_USER(
    p_networkid IN VARCHAR2
  );

  /**
   * Cross-object impact procedure for change-assurance demos.
   * Touches USER_ORDER_DETAILS, ORDERS, ORDER_LINES and calls ORDERS_PKG + AUDIT_PKG.
   * Changing this routine widens blast radius beyond APP.USERS.
   */
  PROCEDURE CLOSE_USER_ACCOUNT(
    p_networkid IN VARCHAR2,
    p_reason    IN VARCHAR2 DEFAULT 'account closed'
  );

END USERS_PKG;
/

--------------------------------------------------------------------------------
-- 4) Package body
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE BODY APP.USERS_PKG AS

  PROCEDURE ASSERT_NETWORKID(p_networkid IN VARCHAR2) IS
  BEGIN
    IF p_networkid IS NULL OR LENGTH(TRIM(p_networkid)) = 0 THEN
      RAISE_APPLICATION_ERROR(-20001, 'NETWORKID is required');
    END IF;
  END ASSERT_NETWORKID;

  PROCEDURE CREATE_USER(
    p_user_name IN VARCHAR2,
    p_networkid IN VARCHAR2,
    p_password  IN VARCHAR2
  ) IS
  BEGIN
    ASSERT_NETWORKID(p_networkid);

    IF p_user_name IS NULL OR LENGTH(TRIM(p_user_name)) = 0 THEN
      RAISE_APPLICATION_ERROR(-20002, 'USER_NAME is required');
    END IF;

    IF p_password IS NULL OR LENGTH(TRIM(p_password)) = 0 THEN
      RAISE_APPLICATION_ERROR(-20003, 'PASSWORD is required');
    END IF;

    INSERT INTO APP.USERS (USER_ID, USER_NAME, NETWORKID, PASSWORD)
    VALUES (APP.USERS_USER_ID_SEQ.NEXTVAL, TRIM(p_user_name), TRIM(p_networkid), p_password);
  EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN
      RAISE_APPLICATION_ERROR(
        -20004,
        'User already exists for NETWORKID=' || TRIM(p_networkid)
      );
  END CREATE_USER;

  PROCEDURE GET_USER(
    p_networkid IN  VARCHAR2,
    p_user_name OUT VARCHAR2,
    p_password  OUT VARCHAR2
  ) IS
  BEGIN
    ASSERT_NETWORKID(p_networkid);

    SELECT USER_NAME, PASSWORD
      INTO p_user_name, p_password
      FROM APP.USERS
     WHERE NETWORKID = TRIM(p_networkid);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(
        -20005,
        'User not found for NETWORKID=' || TRIM(p_networkid)
      );
  END GET_USER;

  FUNCTION USER_EXISTS(
    p_networkid IN VARCHAR2
  ) RETURN NUMBER IS
    v_count NUMBER;
  BEGIN
    IF p_networkid IS NULL OR LENGTH(TRIM(p_networkid)) = 0 THEN
      RETURN 0;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM APP.USERS
     WHERE NETWORKID = TRIM(p_networkid);

    RETURN CASE WHEN v_count > 0 THEN 1 ELSE 0 END;
  END USER_EXISTS;

  PROCEDURE UPDATE_USER(
    p_networkid IN VARCHAR2,
    p_user_name IN VARCHAR2 DEFAULT NULL,
    p_password  IN VARCHAR2 DEFAULT NULL
  ) IS
    v_rows NUMBER;
  BEGIN
    ASSERT_NETWORKID(p_networkid);

    IF p_user_name IS NULL AND p_password IS NULL THEN
      RAISE_APPLICATION_ERROR(-20006, 'Provide p_user_name and/or p_password to update');
    END IF;

    UPDATE APP.USERS
       SET USER_NAME = NVL(TRIM(p_user_name), USER_NAME),
           PASSWORD  = NVL(p_password, PASSWORD)
     WHERE NETWORKID = TRIM(p_networkid);

    v_rows := SQL%ROWCOUNT;
    IF v_rows = 0 THEN
      RAISE_APPLICATION_ERROR(
        -20005,
        'User not found for NETWORKID=' || TRIM(p_networkid)
      );
    END IF;
  END UPDATE_USER;

  PROCEDURE UPDATE_PASSWORD(
    p_networkid IN VARCHAR2,
    p_password  IN VARCHAR2
  ) IS
  BEGIN
    IF p_password IS NULL OR LENGTH(TRIM(p_password)) = 0 THEN
      RAISE_APPLICATION_ERROR(-20003, 'PASSWORD is required');
    END IF;

    UPDATE_USER(
      p_networkid => p_networkid,
      p_password  => p_password
    );
  END UPDATE_PASSWORD;

  PROCEDURE DELETE_USER(
    p_networkid IN VARCHAR2
  ) IS
    v_rows NUMBER;
  BEGIN
    ASSERT_NETWORKID(p_networkid);

    DELETE FROM APP.USERS
     WHERE NETWORKID = TRIM(p_networkid);

    v_rows := SQL%ROWCOUNT;
    IF v_rows = 0 THEN
      RAISE_APPLICATION_ERROR(
        -20005,
        'User not found for NETWORKID=' || TRIM(p_networkid)
      );
    END IF;
  END DELETE_USER;

  PROCEDURE CLOSE_USER_ACCOUNT(
    p_networkid IN VARCHAR2,
    p_reason    IN VARCHAR2 DEFAULT 'account closed'
  ) IS
    v_user_id   NUMBER;
    v_user_name VARCHAR2(128);
    v_report_nm VARCHAR2(128);
    v_order_cnt NUMBER := 0;
  BEGIN
    ASSERT_NETWORKID(p_networkid);

    SELECT USER_ID, USER_NAME
      INTO v_user_id, v_user_name
      FROM APP.USERS
     WHERE NETWORKID = TRIM(p_networkid);

    -- Reporting dependency: V_USERS_REPORT
    BEGIN
      SELECT USER_NAME
        INTO v_report_nm
        FROM APP.V_USERS_REPORT
       WHERE NETWORKID = TRIM(p_networkid);
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        v_report_nm := v_user_name;
    END;

    -- Cascade across order tables + ORDERS_PKG
    FOR r IN (
      SELECT d.ORDER_ID, d.ID AS DETAIL_ID
        FROM APP.USER_ORDER_DETAILS d
       WHERE d.USER_ID = v_user_id
    ) LOOP
      v_order_cnt := v_order_cnt + 1;

      UPDATE APP.USER_ORDER_DETAILS
         SET ORDER_DETAIL = SUBSTR(
               NVL(ORDER_DETAIL, '') || ' | CLOSED: ' || NVL(p_reason, 'account closed'),
               1,
               4000
             )
       WHERE ID = r.DETAIL_ID;

      UPDATE APP.ORDER_LINES
         SET SKU = SUBSTR(NVL(SKU, 'N/A') || '-CLOSED', 1, 50)
       WHERE ORDER_ID = r.ORDER_ID;

      APP.ORDERS_PKG.CANCEL_ORDER(r.ORDER_ID);
    END LOOP;

    -- Package-to-package dependency: AUDIT_PKG (+ AUDIT_EVENTS table)
    APP.AUDIT_PKG.WRITE_EVENT(
      'CLOSE_USER_ACCOUNT networkid=' || TRIM(p_networkid)
      || ' user=' || v_report_nm
      || ' orders_touched=' || v_order_cnt
      || ' reason=' || NVL(p_reason, 'account closed')
    );

    -- Soft signal on user row (keeps USERS DML in this routine)
    UPDATE APP.USERS
       SET PASSWORD = SUBSTR('LOCKED:' || NVL(PASSWORD, 'x'), 1, 20)
     WHERE USER_ID = v_user_id;

  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(
        -20005,
        'User not found for NETWORKID=' || TRIM(p_networkid)
      );
  END CLOSE_USER_ACCOUNT;

END USERS_PKG;
/

--------------------------------------------------------------------------------
-- 5) Dependent caller (so USERS_PKG shows dependents in impact analysis)
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE APP.CLOSE_USER_ACCOUNT_JOB(
  p_networkid IN VARCHAR2
) AS
BEGIN
  APP.USERS_PKG.CLOSE_USER_ACCOUNT(p_networkid => p_networkid);
END CLOSE_USER_ACCOUNT_JOB;
/
