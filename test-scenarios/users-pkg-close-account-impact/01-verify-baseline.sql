-- Read-only checks that the Oracle baseline for this scenario exists.
-- Connect as APP (APP/AppDemoPass1@FREEPDB1). Does not modify data.

SET LINESIZE 200
SET PAGESIZE 100
COL object_name FORMAT A28
COL object_type FORMAT A15
COL status FORMAT A10
COL procedure_name FORMAT A28

PROMPT === Core objects ===
SELECT object_name, object_type, status
  FROM user_objects
 WHERE object_name IN (
       'USERS', 'ORDERS', 'ORDER_LINES', 'USER_ORDER_DETAILS',
       'V_USERS_REPORT', 'USERS_PKG', 'ORDERS_PKG', 'AUDIT_PKG',
       'BILLING_PKG', 'CLOSE_USER_ACCOUNT_JOB'
     )
 ORDER BY object_type, object_name;

PROMPT === USERS_PKG routines ===
SELECT procedure_name
  FROM user_procedures
 WHERE object_name = 'USERS_PKG'
 ORDER BY subprogram_id;

PROMPT === USERS_PKG dependencies (APP) ===
SELECT referenced_name, referenced_type
  FROM user_dependencies
 WHERE name = 'USERS_PKG'
   AND referenced_owner = 'APP'
 ORDER BY referenced_type, referenced_name;

PROMPT === Dependents of USERS_PKG ===
SELECT name, type
  FROM user_dependencies
 WHERE referenced_name = 'USERS_PKG'
 ORDER BY type, name;

PROMPT === Sample linked rows ===
SELECT u.user_id, u.networkid, d.order_id, SUBSTR(d.order_detail, 1, 60) AS order_detail
  FROM user_order_details d
  JOIN users u ON u.user_id = d.user_id
 ORDER BY u.networkid, d.order_id;