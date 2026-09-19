-- Milder body-only variant: keeps soft updates, only adds BILLING_PKG call.
-- Use this if you want package-impact findings without hard DELETE semantics.

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
      RAISE_APPLICATION_ERROR(-20004, 'User already exists for NETWORKID=' || TRIM(p_networkid));
  END CREATE_USER;

  PROCEDURE GET_USER(
    p_networkid IN  VARCHAR2,
    p_user_name OUT VARCHAR2,
    p_password  OUT VARCHAR2
  ) IS
  BEGIN
    ASSERT_NETWORKID(p_networkid);
    SELECT USER_NAME, PASSWORD INTO p_user_name, p_password
      FROM APP.USERS WHERE NETWORKID = TRIM(p_networkid);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20005, 'User not found for NETWORKID=' || TRIM(p_networkid));
  END GET_USER;

  FUNCTION USER_EXISTS(p_networkid IN VARCHAR2) RETURN NUMBER IS
    v_count NUMBER;
  BEGIN
    IF p_networkid IS NULL OR LENGTH(TRIM(p_networkid)) = 0 THEN
      RETURN 0;
    END IF;
    SELECT COUNT(*) INTO v_count FROM APP.USERS WHERE NETWORKID = TRIM(p_networkid);
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
      RAISE_APPLICATION_ERROR(-20005, 'User not found for NETWORKID=' || TRIM(p_networkid));
    END IF;
  END UPDATE_USER;

  PROCEDURE UPDATE_PASSWORD(p_networkid IN VARCHAR2, p_password IN VARCHAR2) IS
  BEGIN
    IF p_password IS NULL OR LENGTH(TRIM(p_password)) = 0 THEN
      RAISE_APPLICATION_ERROR(-20003, 'PASSWORD is required');
    END IF;
    UPDATE_USER(p_networkid => p_networkid, p_password => p_password);
  END UPDATE_PASSWORD;

  PROCEDURE DELETE_USER(p_networkid IN VARCHAR2) IS
    v_rows NUMBER;
  BEGIN
    ASSERT_NETWORKID(p_networkid);
    DELETE FROM APP.USERS WHERE NETWORKID = TRIM(p_networkid);
    v_rows := SQL%ROWCOUNT;
    IF v_rows = 0 THEN
      RAISE_APPLICATION_ERROR(-20005, 'User not found for NETWORKID=' || TRIM(p_networkid));
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

    SELECT USER_ID, USER_NAME INTO v_user_id, v_user_name
      FROM APP.USERS WHERE NETWORKID = TRIM(p_networkid);

    BEGIN
      SELECT USER_NAME INTO v_report_nm
        FROM APP.V_USERS_REPORT WHERE NETWORKID = TRIM(p_networkid);
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        v_report_nm := v_user_name;
    END;

    FOR r IN (
      SELECT d.ORDER_ID, d.ID AS DETAIL_ID
        FROM APP.USER_ORDER_DETAILS d
       WHERE d.USER_ID = v_user_id
    ) LOOP
      v_order_cnt := v_order_cnt + 1;

      UPDATE APP.USER_ORDER_DETAILS
         SET ORDER_DETAIL = SUBSTR(
               NVL(ORDER_DETAIL, '') || ' | CLOSED: ' || NVL(p_reason, 'account closed'),
               1, 4000)
       WHERE ID = r.DETAIL_ID;

      UPDATE APP.ORDER_LINES
         SET SKU = SUBSTR(NVL(SKU, 'N/A') || '-CLOSED', 1, 50)
       WHERE ORDER_ID = r.ORDER_ID;

      APP.ORDERS_PKG.CANCEL_ORDER(r.ORDER_ID);
    END LOOP;

    -- NEW: billing side-effect for every account close
    APP.BILLING_PKG.REFRESH_ACCOUNTS;

    APP.AUDIT_PKG.WRITE_EVENT(
      'CLOSE_USER_ACCOUNT(mild) networkid=' || TRIM(p_networkid)
      || ' user=' || v_report_nm
      || ' orders_touched=' || v_order_cnt
      || ' reason=' || NVL(p_reason, 'account closed')
    );

    UPDATE APP.USERS
       SET PASSWORD = SUBSTR('LOCKED:' || NVL(PASSWORD, 'x'), 1, 20)
     WHERE USER_ID = v_user_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20005, 'User not found for NETWORKID=' || TRIM(p_networkid));
  END CLOSE_USER_ACCOUNT;

END USERS_PKG;
/
