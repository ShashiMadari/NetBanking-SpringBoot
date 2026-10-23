-- Structure only: O_BANK_BANKING / PACKAGE_SPEC
ALTER SESSION SET CURRENT_SCHEMA = O_BANK_BANKING;

  CREATE OR REPLACE EDITIONABLE PACKAGE "O_BANK_BANKING"."PKG_PAYMENT" AS

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
    );

    PROCEDURE approve_payment_order (
        p_user_id          IN NUMBER,
        p_payment_order_id IN NUMBER,
        p_otp_hash         IN VARCHAR2
    );

    PROCEDURE reverse_payment_order (
        p_admin_user_id     IN NUMBER,
        p_payment_order_id  IN NUMBER,
        p_reason            IN VARCHAR2
    );

END pkg_payment;
/

