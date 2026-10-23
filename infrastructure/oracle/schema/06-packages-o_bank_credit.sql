-- Structure only: O_BANK_CREDIT / PACKAGE_SPEC
ALTER SESSION SET CURRENT_SCHEMA = O_BANK_CREDIT;

  CREATE OR REPLACE EDITIONABLE PACKAGE "O_BANK_CREDIT"."PKG_CREDIT_CARD_ADMIN" AS

    PROCEDURE reverse_merchant_purchase (
        p_admin_user_id        IN NUMBER,
        p_merchant_purchase_id IN NUMBER,
        p_reason               IN VARCHAR2
    );

END pkg_credit_card_admin;
/


  CREATE OR REPLACE EDITIONABLE PACKAGE "O_BANK_CREDIT"."PKG_LOAN_SCHEDULE" AS

    PROCEDURE generate_schedule(
        p_loan_id        IN NUMBER,
        p_first_due_date IN DATE
    );

    PROCEDURE recalculate_remaining_schedule(
        p_loan_id               IN NUMBER,
        p_remaining_principal   IN NUMBER
    );

END pkg_loan_schedule;
/

