-- Structure only: O_BANK_CREDIT / PACKAGE_BODY
ALTER SESSION SET CURRENT_SCHEMA = O_BANK_CREDIT;

  CREATE OR REPLACE EDITIONABLE PACKAGE BODY "O_BANK_CREDIT"."PKG_CREDIT_CARD_ADMIN" AS

    PROCEDURE reverse_merchant_purchase (
        p_admin_user_id        IN NUMBER,
        p_merchant_purchase_id IN NUMBER,
        p_reason               IN VARCHAR2
    )
    AS
        v_user_id            NUMBER;
        v_card_id            NUMBER;
        v_card_charge        NUMBER(15, 2);
        v_points_redeemed    NUMBER(12);
        v_points_earned      NUMBER(12);
        v_reference_number   VARCHAR2(80);
        v_purchase_status    VARCHAR2(15);

        v_card_outstanding   NUMBER(15, 2);
        v_available_points   NUMBER(12);
        v_reason             VARCHAR2(500);
        v_reversal_reference VARCHAR2(80);
        v_new_outstanding    NUMBER(15, 2);
    BEGIN
        IF p_admin_user_id IS NULL THEN
            RAISE_APPLICATION_ERROR(
                -20412,
                'Administrator identity is required.'
            );
        END IF;

        v_reason := TRIM(p_reason);

        IF v_reason IS NULL
           OR LENGTH(v_reason) < 5
           OR LENGTH(v_reason) > 500 THEN

            RAISE_APPLICATION_ERROR(
                -20411,
                'Reversal reason must contain 5 to 500 characters.'
            );
        END IF;

        SELECT user_id,
               card_id,
               card_charge_amount,
               points_redeemed,
               points_earned,
               reference_number,
               status
          INTO v_user_id,
               v_card_id,
               v_card_charge,
               v_points_redeemed,
               v_points_earned,
               v_reference_number,
               v_purchase_status
          FROM credit_card_merchant_purchases
         WHERE merchant_purchase_id = p_merchant_purchase_id
         FOR UPDATE;

        IF v_purchase_status <> 'SUCCESS' THEN
            RAISE_APPLICATION_ERROR(
                -20413,
                'Only successful merchant purchases can be reversed.'
            );
        END IF;

        SELECT outstanding_amount
          INTO v_card_outstanding
          FROM cards
         WHERE card_id = v_card_id
         FOR UPDATE;

        IF v_card_outstanding < v_card_charge THEN
            RAISE_APPLICATION_ERROR(
                -20414,
                'Purchase cannot be reversed because the card balance changed.'
            );
        END IF;

        SELECT NVL(SUM(points), 0)
          INTO v_available_points
          FROM credit_card_reward_point_ledger
         WHERE user_id = v_user_id
           AND card_id = v_card_id;

        IF v_available_points < v_points_earned THEN
            RAISE_APPLICATION_ERROR(
                -20415,
                'Purchase cannot be reversed because earned reward points were already used.'
            );
        END IF;

        v_reversal_reference :=
                SUBSTR(v_reference_number, 1, 76) || '-R';

        v_new_outstanding := GREATEST(
                v_card_outstanding - v_card_charge,
                0
        );

        UPDATE cards
           SET outstanding_amount = v_new_outstanding,
               minimum_due_amount = LEAST(
                       v_new_outstanding,
                       ROUND(v_new_outstanding * 0.05, 2)
               )
         WHERE card_id = v_card_id;

        IF v_points_earned > 0 THEN
            INSERT INTO credit_card_reward_point_ledger (
                user_id,
                card_id,
                merchant_purchase_id,
                event_type,
                points,
                reference_number,
                description,
                created_at
            )
            VALUES (
                v_user_id,
                v_card_id,
                p_merchant_purchase_id,
                'EARN_REVERSAL',
                -v_points_earned,
                v_reversal_reference || '-EARN',
                'Reward points removed because merchant purchase was reversed.',
                SYSTIMESTAMP
            );
        END IF;

        IF v_points_redeemed > 0 THEN
            INSERT INTO credit_card_reward_point_ledger (
                user_id,
                card_id,
                merchant_purchase_id,
                event_type,
                points,
                reference_number,
                description,
                created_at
            )
            VALUES (
                v_user_id,
                v_card_id,
                p_merchant_purchase_id,
                'REDEEM_RESTORE',
                v_points_redeemed,
                v_reversal_reference || '-RESTORE',
                'Reward points restored because merchant purchase was reversed.',
                SYSTIMESTAMP
            );
        END IF;

        UPDATE credit_card_merchant_purchases
           SET status = 'REVERSED',
               reversal_reference_number = v_reversal_reference,
               reversal_reason = v_reason,
               reversed_at = SYSTIMESTAMP
         WHERE merchant_purchase_id = p_merchant_purchase_id;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20410,
                'Merchant purchase or credit card was not found.'
            );
    END reverse_merchant_purchase;

END pkg_credit_card_admin;
/


  CREATE OR REPLACE EDITIONABLE PACKAGE BODY "O_BANK_CREDIT"."PKG_LOAN_SCHEDULE" AS

    PROCEDURE generate_schedule(
        p_loan_id        IN NUMBER,
        p_first_due_date IN DATE
    ) AS
        v_outstanding       loans.outstanding_amount%TYPE;
        v_interest_rate     loans.interest_rate%TYPE;
        v_tenure_months     loans.tenure_months%TYPE;
        v_loan_status       loans.status%TYPE;
        v_paid_count        NUMBER;

        v_monthly_rate      NUMBER(15,10);
        v_regular_emi       NUMBER(15,2);
        v_row_emi           NUMBER(15,2);
        v_opening           NUMBER(15,2);
        v_interest          NUMBER(15,2);
        v_principal         NUMBER(15,2);
        v_closing           NUMBER(15,2);
    BEGIN
        SELECT outstanding_amount,
               interest_rate,
               tenure_months,
               status
        INTO v_outstanding,
             v_interest_rate,
             v_tenure_months,
             v_loan_status
        FROM loans
        WHERE loan_id = p_loan_id
        FOR UPDATE;

        IF v_loan_status <> 'ACTIVE' THEN
            RAISE_APPLICATION_ERROR(
                -20201,
                'Only an active loan can have an EMI schedule.'
            );
        END IF;

        IF p_first_due_date IS NULL OR v_outstanding <= 0 THEN
            RAISE_APPLICATION_ERROR(
                -20202,
                'Loan amount and first due date are required.'
            );
        END IF;

        SELECT COUNT(*)
        INTO v_paid_count
        FROM loan_emi_schedules
        WHERE loan_id = p_loan_id
          AND status = 'PAID';

        IF v_paid_count > 0 THEN
            RAISE_APPLICATION_ERROR(
                -20203,
                'Cannot regenerate a schedule after EMI payment has started.'
            );
        END IF;

        DELETE FROM loan_emi_schedules
        WHERE loan_id = p_loan_id;

        v_monthly_rate := v_interest_rate / 1200;

        IF v_monthly_rate = 0 THEN
            v_regular_emi := ROUND(
                    v_outstanding / v_tenure_months,
                    2
            );
        ELSE
            v_regular_emi := ROUND(
                    v_outstanding
                    * v_monthly_rate
                    * POWER(1 + v_monthly_rate, v_tenure_months)
                    / (
                        POWER(1 + v_monthly_rate, v_tenure_months) - 1
                    ),
                    2
            );
        END IF;

        v_opening := v_outstanding;

        FOR i IN 1 .. v_tenure_months LOOP
            v_interest := ROUND(v_opening * v_monthly_rate, 2);
            v_principal := ROUND(v_regular_emi - v_interest, 2);

            IF i = v_tenure_months
               OR v_principal >= v_opening THEN

                v_principal := v_opening;
                v_row_emi := ROUND(v_principal + v_interest, 2);
            ELSE
                v_row_emi := v_regular_emi;
            END IF;

            v_closing := v_opening - v_principal;

            INSERT INTO loan_emi_schedules (
                loan_id,
                installment_number,
                due_date,
                opening_principal,
                emi_amount,
                principal_component,
                interest_component,
                closing_principal,
                status
            )
            VALUES (
                p_loan_id,
                i,
                ADD_MONTHS(p_first_due_date, i - 1),
                v_opening,
                v_row_emi,
                v_principal,
                v_interest,
                v_closing,
                'DUE'
            );

            v_opening := v_closing;
        END LOOP;

        UPDATE loans
        SET emi_amount = v_regular_emi,
            next_due_date = p_first_due_date,
            installments_paid = 0
        WHERE loan_id = p_loan_id;
    END generate_schedule;
    PROCEDURE recalculate_remaining_schedule(
        p_loan_id               IN NUMBER,
        p_remaining_principal   IN NUMBER
    ) AS
        v_interest_rate       loans.interest_rate%TYPE;
        v_tenure_months       loans.tenure_months%TYPE;
        v_installments_paid   loans.installments_paid%TYPE;
        v_loan_status         loans.status%TYPE;
        v_first_due_date      DATE;
        v_remaining_months    NUMBER;
        v_due_count           NUMBER;
        v_processed           NUMBER := 0;

        v_monthly_rate        NUMBER(15,10);
        v_regular_emi         NUMBER(15,2);
        v_row_emi             NUMBER(15,2);
        v_opening             NUMBER(15,2);
        v_interest            NUMBER(15,2);
        v_principal           NUMBER(15,2);
        v_closing             NUMBER(15,2);
    BEGIN
        IF p_remaining_principal IS NULL OR p_remaining_principal <= 0 THEN
            RAISE_APPLICATION_ERROR(-20210,
                'Remaining principal must be greater than zero.');
        END IF;

        SELECT interest_rate, tenure_months, installments_paid, status
        INTO v_interest_rate, v_tenure_months, v_installments_paid,
             v_loan_status
        FROM loans
        WHERE loan_id = p_loan_id
        FOR UPDATE;

        IF v_loan_status IS NULL OR v_loan_status <> 'ACTIVE' THEN
            RAISE_APPLICATION_ERROR(-20212,
                'Only an active loan can have its remaining schedule recalculated.');
        END IF;

        v_remaining_months := v_tenure_months - v_installments_paid;
        IF v_remaining_months IS NULL OR v_remaining_months <= 0
           OR v_remaining_months <> TRUNC(v_remaining_months) THEN
            RAISE_APPLICATION_ERROR(-20213,
                'Remaining installment count must be a positive integer.');
        END IF;

        IF v_interest_rate IS NULL OR v_interest_rate < 0 THEN
            RAISE_APPLICATION_ERROR(-20214,
                'Loan interest rate must be nonnegative.');
        END IF;

        SELECT COUNT(*), MIN(due_date)
        INTO v_due_count, v_first_due_date
        FROM loan_emi_schedules
        WHERE loan_id = p_loan_id AND status = 'DUE';

        IF v_due_count = 0 OR v_first_due_date IS NULL THEN
            RAISE_APPLICATION_ERROR(-20211,
                'No remaining EMI schedule exists.');
        END IF;

        IF v_due_count <> v_remaining_months THEN
            RAISE_APPLICATION_ERROR(-20215,
                'Unpaid schedule count does not match remaining loan tenure.');
        END IF;

        v_monthly_rate := v_interest_rate / 1200;
        IF v_monthly_rate = 0 THEN
            v_regular_emi := ROUND(
                p_remaining_principal / v_remaining_months, 2);
        ELSE
            v_regular_emi := ROUND(
                p_remaining_principal * v_monthly_rate
                * POWER(1 + v_monthly_rate, v_remaining_months)
                / (POWER(1 + v_monthly_rate, v_remaining_months) - 1), 2);
        END IF;

        v_opening := p_remaining_principal;

        FOR installment IN (
            SELECT loan_emi_schedule_id
            FROM loan_emi_schedules
            WHERE loan_id = p_loan_id AND status = 'DUE'
            ORDER BY installment_number, loan_emi_schedule_id
            FOR UPDATE
        ) LOOP
            v_processed := v_processed + 1;
            v_interest := ROUND(v_opening * v_monthly_rate, 2);
            v_principal := ROUND(v_regular_emi - v_interest, 2);

            IF v_processed = v_remaining_months
               OR v_principal >= v_opening THEN
                v_principal := v_opening;
                v_row_emi := ROUND(v_principal + v_interest, 2);
            ELSE
                v_row_emi := v_regular_emi;
            END IF;

            v_closing := ROUND(v_opening - v_principal, 2);

            -- Preserve IDs, installment numbers, dates, status and history.
            UPDATE loan_emi_schedules
            SET opening_principal = v_opening,
                emi_amount = v_row_emi,
                principal_component = v_principal,
                interest_component = v_interest,
                closing_principal = v_closing
            WHERE loan_emi_schedule_id = installment.loan_emi_schedule_id
              AND loan_id = p_loan_id AND status = 'DUE';

            IF SQL%ROWCOUNT <> 1 THEN
                RAISE_APPLICATION_ERROR(-20216,
                    'An unpaid installment changed during recalculation.');
            END IF;

            v_opening := v_closing;
        END LOOP;

        IF v_processed <> v_remaining_months OR v_opening <> 0 THEN
            RAISE_APPLICATION_ERROR(-20217,
                'Recalculated schedule does not reconcile to the remaining principal.');
        END IF;

        UPDATE loans
        SET emi_amount = v_regular_emi,
            next_due_date = v_first_due_date
        WHERE loan_id = p_loan_id;
    END recalculate_remaining_schedule;

END pkg_loan_schedule;
/

