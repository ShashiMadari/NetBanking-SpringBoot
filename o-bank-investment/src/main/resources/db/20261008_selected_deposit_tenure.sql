-- Run once on the existing investment schema before restarting the service.
-- New databases get these columns from investment-schema.sql.
DECLARE
  column_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO column_count FROM user_tab_columns
   WHERE table_name = 'INVESTMENT_ORDERS' AND column_name = 'SELECTED_TENURE_MONTHS';
  IF column_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE investment_orders ADD (selected_tenure_months NUMBER(4))';
  END IF;

  SELECT COUNT(*) INTO column_count FROM user_tab_columns
   WHERE table_name = 'INVESTMENT_HOLDINGS' AND column_name = 'TENURE_MONTHS';
  IF column_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE investment_holdings ADD (tenure_months NUMBER(4))';
  END IF;
END;
/
