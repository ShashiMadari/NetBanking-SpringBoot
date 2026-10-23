-- Structure only: O_BANK_BANKING / PACKAGE_BODY
ALTER SESSION SET CURRENT_SCHEMA = O_BANK_BANKING;

  CREATE OR REPLACE EDITIONABLE PACKAGE BODY "O_BANK_BANKING"."PKG_PAYMENT" AS

    PROCEDURE create_payment_order (
        p_user_id            IN NUMBER,
        p_source_account_id  IN NUMBER,
        p_beneficiary_id     IN NUMBER,
        p_biller_id          IN NUMBER,
        p_consumer_reference IN VARCHAR2,
        p_payment_type       IN VARCHAR2,
        p_amount             IN NUMBER,
        p_otp_hash           IN VARCHAR2,
        p_otp_expiry         IN TIMESTAMP,
        p_reference_number   IN VARCHAR2,
        p_idempotency_key    IN VARCHAR2,
        p_payment_order_id   OUT NUMBER
    )
    AS
        v_account_status     accounts.status%TYPE;
        v_available_balance  accounts.available_balance%TYPE;
        v_beneficiary_status beneficiaries.status%TYPE;
        v_biller_status      billers.status%TYPE;
    BEGIN
        BEGIN
            SELECT payment_order_id
            INTO p_payment_order_id
            FROM payment_orders
            WHERE idempotency_key = p_idempotency_key;

            RETURN;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                NULL;
        END;

        IF p_amount <= 0 THEN
            RAISE_APPLICATION_ERROR(
                -20301,
                'Payment amount must be greater than zero.'
            );
        END IF;

        IF p_payment_type NOT IN ('TRANSFER', 'BILL_PAYMENT') THEN
            RAISE_APPLICATION_ERROR(
                -20302,
                'Invalid payment type.'
            );
        END IF;

        IF p_otp_hash IS NULL OR p_otp_expiry <= SYSTIMESTAMP THEN
            RAISE_APPLICATION_ERROR(
                -20303,
                'Valid transaction OTP details are required.'
            );
        END IF;

        SELECT status, available_balance
        INTO v_account_status, v_available_balance
        FROM accounts
        WHERE account_id = p_source_account_id
          AND user_id = p_user_id;

        IF v_account_status <> 'ACTIVE' THEN
            RAISE_APPLICATION_ERROR(
                -20304,
                'Source account is not active.'
            );
        END IF;

        IF v_available_balance < p_amount THEN
            RAISE_APPLICATION_ERROR(
                -20305,
                'Insufficient available balance.'
            );
        END IF;

        IF p_payment_type = 'TRANSFER' THEN

            IF p_beneficiary_id IS NULL
               OR p_biller_id IS NOT NULL
               OR p_consumer_reference IS NOT NULL THEN

                RAISE_APPLICATION_ERROR(
                    -20306,
                    'Transfer requires one beneficiary only.'
                );
            END IF;

            SELECT status
            INTO v_beneficiary_status
            FROM beneficiaries
            WHERE beneficiary_id = p_beneficiary_id
              AND user_id = p_user_id;

            IF v_beneficiary_status <> 'ACTIVE' THEN
                RAISE_APPLICATION_ERROR(
                    -20307,
                    'Beneficiary is not active.'
                );
            END IF;

        ELSE

            IF p_biller_id IS NULL
               OR p_beneficiary_id IS NOT NULL
               OR p_consumer_reference IS NULL THEN

                RAISE_APPLICATION_ERROR(
                    -20308,
                    'Bill payment requires a biller and consumer reference.'
                );
            END IF;

            SELECT status
            INTO v_biller_status
            FROM billers
            WHERE biller_id = p_biller_id;

            IF v_biller_status <> 'ACTIVE' THEN
                RAISE_APPLICATION_ERROR(
                    -20309,
                    'Biller is inactive.'
                );
            END IF;
        END IF;

        INSERT INTO payment_orders (
            source_account_id,
            beneficiary_id,
            biller_id,
            consumer_reference,
            payment_type,
            amount,
            reference_number,
            idempotency_key,
            status,
            otp_hash,
            otp_expiry
        )
        VALUES (
            p_source_account_id,
            p_beneficiary_id,
            p_biller_id,
            p_consumer_reference,
            p_payment_type,
            p_amount,
            p_reference_number,
            p_idempotency_key,
            'OTP_PENDING',
            p_otp_hash,
            p_otp_expiry
        )
        RETURNING payment_order_id INTO p_payment_order_id;

    END create_payment_order;


    PROCEDURE approve_payment_order (
        p_user_id          IN NUMBER,
        p_payment_order_id IN NUMBER,
        p_otp_hash         IN VARCHAR2
    )
    AS
        v_source_account_id      NUMBER(19);
        v_beneficiary_id         NUMBER(19);
        v_payment_type           VARCHAR2(20);
        v_amount                 NUMBER(15,2);
        v_reference_number       VARCHAR2(40);
        v_db_otp_hash            VARCHAR2(255);
        v_otp_expiry             TIMESTAMP;
        v_payment_status         VARCHAR2(20);

        v_source_balance         NUMBER(15,2);
        v_source_status          VARCHAR2(15);

        v_beneficiary_account_no VARCHAR2(34);
        v_destination_account_id NUMBER(19);
        v_debit_transaction_id   NUMBER(19);
        v_is_internal_transfer   CHAR(1) := 'N';
    BEGIN
        SELECT
            po.source_account_id,
            po.beneficiary_id,
            po.payment_type,
            po.amount,
            po.reference_number,
            po.otp_hash,
            po.otp_expiry,
            po.status
        INTO
            v_source_account_id,
            v_beneficiary_id,
            v_payment_type,
            v_amount,
            v_reference_number,
            v_db_otp_hash,
            v_otp_expiry,
            v_payment_status
        FROM payment_orders po
        JOIN accounts a
          ON a.account_id = po.source_account_id
        WHERE po.payment_order_id = p_payment_order_id
          AND a.user_id = p_user_id
        FOR UPDATE;

        IF v_payment_status <> 'OTP_PENDING' THEN
            RAISE_APPLICATION_ERROR(
                -20310,
                'Payment is not waiting for OTP approval.'
            );
        END IF;

        IF v_otp_expiry IS NULL OR v_otp_expiry < SYSTIMESTAMP THEN
            UPDATE payment_orders
            SET status = 'FAILED'
            WHERE payment_order_id = p_payment_order_id;

            RAISE_APPLICATION_ERROR(
                -20311,
                'Transaction OTP has expired.'
            );
        END IF;

        IF v_db_otp_hash IS NULL OR v_db_otp_hash <> p_otp_hash THEN
            RAISE_APPLICATION_ERROR(
                -20312,
                'Invalid transaction OTP.'
            );
        END IF;

        SELECT available_balance, status
        INTO v_source_balance, v_source_status
        FROM accounts
        WHERE account_id = v_source_account_id
        FOR UPDATE;

        IF v_source_status <> 'ACTIVE' THEN
            RAISE_APPLICATION_ERROR(
                -20313,
                'Source account is not active.'
            );
        END IF;

        IF v_source_balance < v_amount THEN
            RAISE_APPLICATION_ERROR(
                -20314,
                'Insufficient available balance.'
            );
        END IF;

        UPDATE accounts
        SET available_balance = available_balance - v_amount,
            book_balance = book_balance - v_amount
        WHERE account_id = v_source_account_id;

        INSERT INTO transactions (
            payment_order_id,
            account_id,
            transaction_type,
            entry_side,
            amount,
            reference_number,
            status
        )
        VALUES (
            p_payment_order_id,
            v_source_account_id,
            v_payment_type,
            'DEBIT',
            v_amount,
            SUBSTR(v_reference_number, 1, 35) || '-D',
            'SUCCESS'
        )
        RETURNING transaction_id INTO v_debit_transaction_id;

        IF v_payment_type = 'TRANSFER' THEN

            SELECT beneficiary_account_number
            INTO v_beneficiary_account_no
            FROM beneficiaries
            WHERE beneficiary_id = v_beneficiary_id;

            BEGIN
                SELECT account_id
                INTO v_destination_account_id
                FROM accounts
                WHERE account_number = v_beneficiary_account_no
                  AND status = 'ACTIVE'
                  AND account_id <> v_source_account_id
                  AND ROWNUM = 1;

                v_is_internal_transfer := 'Y';

            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    v_is_internal_transfer := 'N';
            END;

            IF v_is_internal_transfer = 'Y' THEN

                UPDATE accounts
                SET available_balance = available_balance + v_amount,
                    book_balance = book_balance + v_amount
                WHERE account_id = v_destination_account_id;

                INSERT INTO transactions (
                    payment_order_id,
                    account_id,
                    transaction_type,
                    entry_side,
                    amount,
                    reference_number,
                    status
                )
                VALUES (
                    p_payment_order_id,
                    v_destination_account_id,
                    'TRANSFER',
                    'CREDIT',
                    v_amount,
                    SUBSTR(v_reference_number, 1, 35) || '-C',
                    'SUCCESS'
                );
            END IF;
        END IF;

        UPDATE payment_orders
        SET status = 'SUCCESS',
            otp_hash = NULL,
            otp_expiry = NULL,
            completed_at = SYSTIMESTAMP
        WHERE payment_order_id = p_payment_order_id;

    END approve_payment_order;


    PROCEDURE reverse_payment_order (
        p_admin_user_id     IN NUMBER,
        p_payment_order_id  IN NUMBER,
        p_reason            IN VARCHAR2
    )
    AS
        v_source_account_id      NUMBER(19);
        v_customer_user_id       NUMBER(19);
        v_amount                 NUMBER(15,2);
        v_reference_number       VARCHAR2(40);
        v_payment_type           VARCHAR2(20);
        v_payment_status         VARCHAR2(20);

        v_debit_transaction_id   NUMBER(19);
        v_credit_transaction_id  NUMBER(19);
        v_destination_account_id NUMBER(19);
        v_destination_balance    NUMBER(15,2);

        v_reason                 VARCHAR2(500);
        v_reversal_reference     VARCHAR2(80);
    BEGIN
        v_reason := TRIM(p_reason);

        IF v_reason IS NULL
           OR LENGTH(v_reason) < 5
           OR LENGTH(v_reason) > 500 THEN

            RAISE_APPLICATION_ERROR(
                -20316,
                'Reversal reason must contain between 5 and 500 characters.'
            );
        END IF;

        /*
          The Banking Java controller already validates that the caller
          has ADMIN role from the JWT before calling this procedure.
          Therefore, do not query the Identity service USERS table here.
        */
        SELECT
            po.source_account_id,
            a.user_id,
            po.amount,
            po.reference_number,
            po.payment_type,
            po.status
        INTO
            v_source_account_id,
            v_customer_user_id,
            v_amount,
            v_reference_number,
            v_payment_type,
            v_payment_status
        FROM payment_orders po
        JOIN accounts a
          ON a.account_id = po.source_account_id
        WHERE po.payment_order_id = p_payment_order_id
        FOR UPDATE;

        IF v_payment_status <> 'SUCCESS' THEN
            RAISE_APPLICATION_ERROR(
                -20315,
                'Only successful payments can be reversed.'
            );
        END IF;

        SELECT transaction_id
        INTO v_debit_transaction_id
        FROM transactions
        WHERE payment_order_id = p_payment_order_id
          AND entry_side = 'DEBIT'
          AND status = 'SUCCESS';

        v_reversal_reference :=
            SUBSTR(NVL(v_reference_number, 'PAYMENT'), 1, 76) || '-REV';

        /*
          For an internal transfer, first remove money from the receiver.
          A reversal is rejected if that account no longer has enough funds.
        */
        IF v_payment_type = 'TRANSFER' THEN
            BEGIN
                SELECT transaction_id, account_id
                INTO v_credit_transaction_id, v_destination_account_id
                FROM transactions
                WHERE payment_order_id = p_payment_order_id
                  AND transaction_type = 'TRANSFER'
                  AND entry_side = 'CREDIT'
                  AND status = 'SUCCESS'
                  AND account_id <> v_source_account_id
                FOR UPDATE;

                SELECT available_balance
                INTO v_destination_balance
                FROM accounts
                WHERE account_id = v_destination_account_id
                FOR UPDATE;

                IF v_destination_balance < v_amount THEN
                    RAISE_APPLICATION_ERROR(
                        -20318,
                        'Internal recipient account does not have enough balance for reversal.'
                    );
                END IF;

                UPDATE accounts
                SET available_balance = available_balance - v_amount,
                    book_balance      = book_balance - v_amount
                WHERE account_id = v_destination_account_id;

                INSERT INTO transactions (
                    payment_order_id,
                    account_id,
                    transaction_type,
                    entry_side,
                    amount,
                    reference_number,
                    status,
                    reversal_of_transaction_id
                )
                VALUES (
                    p_payment_order_id,
                    v_destination_account_id,
                    'REVERSAL',
                    'DEBIT',
                    v_amount,
                    SUBSTR(NVL(v_reference_number, 'PAYMENT'), 1, 34) || '-REV-C',
                    'SUCCESS',
                    v_credit_transaction_id
                );

            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    /*
                      No internal credit transaction means this was an
                      external transfer. Only refund the source account.
                    */
                    NULL;
            END;
        END IF;

        /* Refund the original customer. */
        UPDATE accounts
        SET available_balance = available_balance + v_amount,
            book_balance      = book_balance + v_amount
        WHERE account_id = v_source_account_id;

        INSERT INTO transactions (
            payment_order_id,
            account_id,
            transaction_type,
            entry_side,
            amount,
            reference_number,
            status,
            reversal_of_transaction_id
        )
        VALUES (
            p_payment_order_id,
            v_source_account_id,
            'REVERSAL',
            'CREDIT',
            v_amount,
            SUBSTR(NVL(v_reference_number, 'PAYMENT'), 1, 34) || '-REV-D',
            'SUCCESS',
            v_debit_transaction_id
        );

        UPDATE payment_orders
        SET status                    = 'REVERSED',
            reversal_reference_number = v_reversal_reference,
            reversal_reason           = v_reason,
            reversed_at               = SYSTIMESTAMP,
            reversed_by_user_id       = p_admin_user_id
        WHERE payment_order_id = p_payment_order_id;

    END reverse_payment_order;

END pkg_payment;
/

