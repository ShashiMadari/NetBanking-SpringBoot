whenever sqlerror exit failure rollback
set define off echo off feedback off serveroutput on
alter session set container=FREEPDB1;
DECLARE
  v_admin NUMBER;
  v_branch NUMBER;
  v_user NUMBER;
  v_savings NUMBER;
  v_current NUMBER;
  v_card NUMBER;
  v_wallet NUMBER;
  v_loan NUMBER;
  v_product NUMBER;
  v_count NUMBER;
  v_open NUMBER;
  v_interest NUMBER;
  v_principal NUMBER;
  v_emi NUMBER;
  v_payment NUMBER;
  v_close NUMBER;
  v_due DATE := ADD_MONTHS(TRUNC(CURRENT_DATE,'MM'),2)+9;
  FUNCTION ensure_user(p_name VARCHAR2,p_email VARCHAR2,p_phone VARCHAR2,p_hash VARCHAR2,p_role VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    BEGIN
      SELECT user_id INTO v_id FROM o_bank_identity.users WHERE LOWER(email)=LOWER(p_email);
    EXCEPTION WHEN NO_DATA_FOUND THEN
      INSERT INTO o_bank_identity.users(full_name,email,phone,password_hash,role,login_status,net_banking_status,status)
      VALUES(p_name,p_email,p_phone,p_hash,p_role,'LOGGED_OUT','ACTIVE','ACTIVE') RETURNING user_id INTO v_id;
      INSERT INTO o_bank_identity.audit_events(user_id,action,entity_type,entity_id,reason)
      VALUES(v_id,'DEMO_USER_PROVISIONED','USER',TO_CHAR(v_id),'Local demo fixture, no production data');
      INSERT INTO o_bank_identity.notifications(user_id,event_type,message)
      VALUES(v_id,'SECURITY','Your local O-Bank demo profile is ready.');
    END;
    RETURN v_id;
  END;
  FUNCTION ensure_account(p_user NUMBER,p_type VARCHAR2,p_number VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    BEGIN
      SELECT account_id INTO v_id FROM o_bank_banking.accounts WHERE user_id=p_user AND account_type=p_type AND status='ACTIVE' FETCH FIRST 1 ROWS ONLY;
    EXCEPTION WHEN NO_DATA_FOUND THEN
      INSERT INTO o_bank_banking.accounts(user_id,branch_id,account_number,account_type,available_balance,book_balance,lien_amount,currency,status)
      VALUES(p_user,v_branch,p_number,p_type,1000000,1000000,0,'INR','ACTIVE') RETURNING account_id INTO v_id;
      INSERT INTO o_bank_banking.transactions(account_id,transaction_type,entry_side,amount,reference_number,status)
      VALUES(v_id,'DEPOSIT','CREDIT',1000000,'DEMO-OPEN-'||v_id,'SUCCESS');
    END;
    RETURN v_id;
  END;
  PROCEDURE product(p_code VARCHAR2,p_name VARCHAR2,p_type VARCHAR2,p_demat CHAR,p_risk VARCHAR2,p_lock NUMBER,p_tenure NUMBER,p_min NUMBER,p_price NUMBER,p_rate NUMBER,p_exchange VARCHAR2) IS
  BEGIN
    INSERT INTO o_bank_investment.investment_products(product_code,product_name,product_type,requires_demat,risk_level,lock_in_months,tenure_months,minimum_amount,current_price,annual_rate,exchange_name,active)
    SELECT p_code,p_name,p_type,p_demat,p_risk,p_lock,p_tenure,p_min,p_price,p_rate,p_exchange,'Y' FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM o_bank_investment.investment_products WHERE product_code=p_code);
  END;
BEGIN
  SELECT COUNT(*) INTO v_count FROM o_bank_identity.users;
  IF v_count > 0 THEN
    RAISE_APPLICATION_ERROR(-20001,'Demo seeding requires a fresh database with no users. Existing records were preserved.');
  END IF;
  SELECT COUNT(*) INTO v_count FROM o_bank_banking.branches WHERE ifsc_code='OBNK0000001';
  IF v_count=0 THEN
    INSERT INTO o_bank_banking.branches(branch_name,ifsc_code,status) VALUES('Bengaluru Demo Branch','OBNK0000001','ACTIVE');
  END IF;
  SELECT branch_id INTO v_branch FROM o_bank_banking.branches WHERE ifsc_code='OBNK0000001';
  v_admin := ensure_user('__ADMIN_NAME__','__ADMIN_EMAIL__','__ADMIN_PHONE__','PASSWORD_HASH_2','ADMIN');
  FOR r IN (
    SELECT 1 n,'__CUSTOMER1_NAME__' full_name,'__CUSTOMER1_EMAIL__' email,'__CUSTOMER1_PHONE__' phone,'PASSWORD_HASH_0' pass_hash FROM dual
    UNION ALL SELECT 2,'__CUSTOMER2_NAME__','__CUSTOMER2_EMAIL__','__CUSTOMER2_PHONE__','PASSWORD_HASH_1' FROM dual
  ) LOOP
    v_user := ensure_user(r.full_name,r.email,r.phone,r.pass_hash,'CUSTOMER');
    v_savings := ensure_account(v_user,'SAVINGS','26102310000'||r.n);
    v_current := ensure_account(v_user,'CURRENT','26102320000'||r.n);
    FOR c IN (SELECT 'CREDIT' card_type,'VISA' network,100000 credit_limit,50000 daily_limit,'10'||LPAD(r.n,2,'0') last_four,v_current account_id FROM dual
              UNION ALL SELECT 'DEBIT','RUPAY',0,25000,'20'||LPAD(r.n,2,'0'),v_savings FROM dual) LOOP
      SELECT COUNT(*) INTO v_count FROM o_bank_credit.cards WHERE user_id=v_user AND card_type=c.card_type;
      IF v_count=0 THEN
        INSERT INTO o_bank_credit.cards(user_id,account_id,card_token,last_four,card_type,network,daily_limit,credit_limit,outstanding_amount,minimum_due_amount,pin_hash,status)
        VALUES(v_user,c.account_id,'DEMO-CARD-'||v_user||'-'||c.card_type,c.last_four,c.card_type,c.network,c.daily_limit,c.credit_limit,0,0,'CARD_PIN_HASH','ACTIVE');
      END IF;
    END LOOP;
    INSERT INTO o_bank_forex.user_kyc(user_id,kyc_status,updated_by)
    SELECT v_user,'APPROVED',v_admin FROM dual WHERE NOT EXISTS(SELECT 1 FROM o_bank_forex.user_kyc WHERE user_id=v_user);
    FOR fx IN (SELECT 'AED' currency FROM dual UNION ALL SELECT 'USD' FROM dual UNION ALL SELECT 'EUR' FROM dual UNION ALL SELECT 'GBP' FROM dual) LOOP
      INSERT INTO o_bank_forex.forex_wallets(user_id,currency_code,available_balance)
      SELECT v_user,fx.currency,0 FROM dual WHERE NOT EXISTS(SELECT 1 FROM o_bank_forex.forex_wallets WHERE user_id=v_user AND currency_code=fx.currency);
    END LOOP;
    SELECT wallet_id INTO v_wallet FROM o_bank_forex.forex_wallets WHERE user_id=v_user AND currency_code='USD';
    INSERT INTO o_bank_forex.forex_cards(user_id,wallet_id,card_number_masked,card_token,status,spend_limit,available_balance)
    SELECT v_user,v_wallet,'FOREX **** '||('300'||r.n),'DEMO-FX-CARD-'||v_user,'ACTIVE',1000,0 FROM dual
    WHERE NOT EXISTS(SELECT 1 FROM o_bank_forex.forex_cards WHERE card_token='DEMO-FX-CARD-'||v_user);
    INSERT INTO o_bank_investment.investment_profiles(user_id,pan_number,kyc_status,linked_account_id,demat_number,demat_status,wallet_balance)
    SELECT v_user,'DEMOX000'||r.n||'A','DEMO_VERIFIED',v_savings,'DEMO'||LPAD(v_user,12,'0'),'ACTIVE',0 FROM dual
    WHERE NOT EXISTS(SELECT 1 FROM o_bank_investment.investment_profiles WHERE user_id=v_user);
    SELECT COUNT(*) INTO v_count FROM o_bank_credit.loans WHERE loan_account_number='LNDEMO20261023'||r.n;
    IF v_count=0 THEN
      v_emi := ROUND(100000*0.01*POWER(1.01,12)/(POWER(1.01,12)-1),2);
      INSERT INTO o_bank_credit.loans(user_id,loan_account_number,loan_type,loan_amount,outstanding_amount,interest_rate,tenure_months,installments_paid,emi_amount,next_due_date,status)
      VALUES(v_user,'LNDEMO20261023'||r.n,'PERSONAL',100000,100000,12,12,0,v_emi,v_due,'ACTIVE') RETURNING loan_id INTO v_loan;
      v_open := 100000;
      FOR i IN 1..12 LOOP
        v_interest := ROUND(v_open*0.01,2);
        v_principal := CASE WHEN i=12 THEN v_open ELSE LEAST(v_open,v_emi-v_interest) END;
        v_payment := v_principal+v_interest;
        v_close := v_open-v_principal;
        INSERT INTO o_bank_credit.loan_emi_schedules(loan_id,installment_number,due_date,opening_principal,emi_amount,principal_component,interest_component,closing_principal,status)
        VALUES(v_loan,i,ADD_MONTHS(v_due,i-1),v_open,v_payment,v_principal,v_interest,v_close,'DUE');
        v_open := v_close;
      END LOOP;
    END IF;
    DBMS_OUTPUT.PUT_LINE('Provisioned customer '||r.email||' userId='||v_user||' savings='||v_savings||' current='||v_current);
  END LOOP;
  -- Link the two customers as internal beneficiaries for transfer demonstrations.
  INSERT INTO o_bank_banking.beneficiaries(user_id,beneficiary_name,beneficiary_account_number,ifsc_code,bank_name,status)
  SELECT u.user_id,other.full_name,a.account_number,'OBNK0000001','O-Bank','ACTIVE'
  FROM o_bank_identity.users u CROSS JOIN o_bank_identity.users other
  JOIN o_bank_banking.accounts a ON a.user_id=other.user_id AND a.account_type='SAVINGS'
  WHERE u.email IN ('__CUSTOMER1_EMAIL__','__CUSTOMER2_EMAIL__')
    AND other.email IN ('__CUSTOMER1_EMAIL__','__CUSTOMER2_EMAIL__') AND u.user_id<>other.user_id
    AND NOT EXISTS(SELECT 1 FROM o_bank_banking.beneficiaries b WHERE b.user_id=u.user_id AND b.beneficiary_account_number=a.account_number AND b.ifsc_code='OBNK0000001');
  FOR b IN (SELECT 'Demo Electricity' name,'ELECTRICITY' category,'Consumer number' label FROM dual
    UNION ALL SELECT 'Demo Mobile','MOBILE','Mobile number' FROM dual
    UNION ALL SELECT 'Demo Gas','GAS','Customer number' FROM dual
    UNION ALL SELECT 'Demo Broadband','BROADBAND','Account number' FROM dual) LOOP
    INSERT INTO o_bank_banking.billers(biller_name,biller_category,consumer_number_label,status)
    SELECT b.name,b.category,b.label,'ACTIVE' FROM dual WHERE NOT EXISTS(SELECT 1 FROM o_bank_banking.billers WHERE biller_name=b.name);
  END LOOP;
  FOR m IN (SELECT 'DEMO_RETAIL' code,'Demo Retail Store' name,'SHOPPING' category FROM dual
    UNION ALL SELECT 'DEMO_TRAVEL','Demo Travel','TRAVEL' FROM dual
    UNION ALL SELECT 'DEMO_GROCERY','Demo Grocery','GROCERY' FROM dual) LOOP
    INSERT INTO o_bank_credit.merchants(merchant_code,merchant_name,merchant_category,reward_eligible,status)
    SELECT m.code,m.name,m.category,'Y','ACTIVE' FROM dual WHERE NOT EXISTS(SELECT 1 FROM o_bank_credit.merchants WHERE merchant_code=m.code);
  END LOOP;
  -- Illustrative demo reference rates; these are not current market quotes.
  FOR fx IN (SELECT 'AED' currency,22.75 rate,125 markup FROM dual UNION ALL SELECT 'USD',84.25,150 FROM dual
    UNION ALL SELECT 'EUR',90.5,175 FROM dual UNION ALL SELECT 'GBP',106,200 FROM dual) LOOP
    INSERT INTO o_bank_forex.forex_rates(currency_code,base_rate_inr,current_rate_inr,markup_bps,status,reference_source,reference_as_of)
    SELECT fx.currency,fx.rate,fx.rate,fx.markup,'ACTIVE','LOCAL_DEMO',CURRENT_DATE FROM dual
    WHERE NOT EXISTS(SELECT 1 FROM o_bank_forex.forex_rates WHERE currency_code=fx.currency);
  END LOOP;
  product('DEMO_EQUITY','Demo Equity','EQUITY','Y','HIGH',0,NULL,100,100,NULL,'NSE');
  product('DEMO_ETF','Demo Gold ETF','ETF','Y','MODERATE',0,NULL,100,100,NULL,'NSE');
  product('DEMO_MF','Demo Index Mutual Fund','MUTUAL_FUND','N','MODERATE',0,NULL,500,100,NULL,NULL);
  product('DEMO_FD','Demo Fixed Deposit','FD','N','LOW',0,12,1000,1,7,NULL);
  product('DEMO_RD','Demo Recurring Deposit','RD','N','LOW',0,12,500,1,6.5,NULL);
  product('DEMO_PPF','Demo PPF','PPF','N','LOW',180,180,500,1,7.1,NULL);
  product('DEMO_BOND','Demo Bond','BOND','Y','MODERATE',0,36,1000,1000,7.5,NULL);
  product('DEMO_GSEC','Demo Government Security','GOVT_SECURITY','Y','LOW',0,60,1000,1000,7,NULL);
  -- Paused SIP templates can be reviewed and resumed deliberately from the application.
  SELECT product_id INTO v_product FROM o_bank_investment.investment_products WHERE product_code='DEMO_MF';
  FOR u IN (SELECT u.user_id,a.account_id FROM o_bank_identity.users u JOIN o_bank_banking.accounts a ON a.user_id=u.user_id
    WHERE u.email IN ('__CUSTOMER1_EMAIL__','__CUSTOMER2_EMAIL__') AND a.account_type='SAVINGS') LOOP
    INSERT INTO o_bank_investment.investment_schedules(user_id,source_account_id,product_id,schedule_type,amount,debit_day,next_debit_date,tenure_months,status)
    SELECT u.user_id,u.account_id,v_product,'SIP',1000,10,v_due,12,'PAUSED' FROM dual
    WHERE NOT EXISTS(SELECT 1 FROM o_bank_investment.investment_schedules WHERE user_id=u.user_id AND product_id=v_product AND schedule_type='SIP');
  END LOOP;
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Local fixture committed. Admin userId='||v_admin);
EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/
exit
