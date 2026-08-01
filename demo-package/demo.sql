CREATE OR REPLACE PACKAGE billing_pkg AS
  PROCEDURE refresh;
END billing_pkg;
/
CREATE OR REPLACE PACKAGE BODY billing_pkg AS
  PROCEDURE refresh IS BEGIN NULL; END;
END billing_pkg;
/
ALTER TABLE billing_accounts ADD (last_refresh_ts TIMESTAMP);
UPDATE billing_accounts SET last_refresh_ts = SYSTIMESTAMP;
