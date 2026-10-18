package com.inetbank.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.inetbank.investment.kafka.InvestmentOrderDebitQueueService;

@Service
public class InvestmentService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final IdentityClient identities;
    private final IdentityAuditClient audits;
    private final BankingAccountClient accounts;
    private final BankingDebitClient debits;
    private final BankingCreditClient credits;
    private final IdentityOtpEmailClient otpEmails;
    private final InvestmentOrderDebitQueueService debitQueue;
    private final InvestmentNotificationService investmentNotifications;

    public InvestmentService(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            IdentityClient identities,
            IdentityAuditClient audits,
            BankingAccountClient accounts,
            BankingDebitClient debits,
            BankingCreditClient credits,
            IdentityOtpEmailClient otpEmails,
            InvestmentOrderDebitQueueService debitQueue,
            InvestmentNotificationService investmentNotifications) {

        this.jdbc = jdbc;
        this.encoder = encoder;
        this.identities = identities;
        this.audits = audits;
        this.accounts = accounts;
        this.debits = debits;
        this.credits = credits;
        this.otpEmails = otpEmails;
        this.debitQueue = debitQueue;
        this.investmentNotifications = investmentNotifications;
    }

    public Map<String, Object> dashboard(String email) {
        Customer customer = customer(email);

        List<Map<String, Object>> holdings = jdbc.queryForList("""
                SELECT h.holding_id id,
                       p.product_name product_name,
                       p.product_type product_type,
                       h.quantity,
                       h.tenure_months,
                       CASE WHEN p.product_type = 'FD' AND h.tenure_months IS NOT NULL
                            THEN ADD_MONTHS(CAST(h.opened_at AS DATE), h.tenure_months)
                            ELSE NULL END maturity_date,
                       h.invested_amount invested_amount,
                       h.current_value current_value,
                       h.current_value - h.invested_amount gain_loss,
                       h.status
                FROM investment_holdings h
                JOIN investment_products p
                  ON p.product_id = h.product_id
                WHERE h.user_id = ?
                  AND h.status IN ('ACTIVE', 'MATURED')
                ORDER BY h.opened_at DESC
                """, customer.id())
                .stream()
                .map(this::holdingResponse)
                .toList();

        BigDecimal invested = holdings.stream()
                .map(row -> decimal(row.get("investedAmount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal current = holdings.stream()
                .map(row -> decimal(row.get("currentValue")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", profile(customer.id()));
        response.put("holdings", holdings);
        response.put("investedAmount", invested);
        response.put("currentValue", current);
        response.put("gainLoss", current.subtract(invested));

        return response;
    }

    public List<Map<String, Object>> products() {
        return jdbc.queryForList("""
                SELECT product_id id,
                       product_code code,
                       product_name name,
                       product_type product_type,
                       requires_demat requires_demat,
                       asset_size asset_size,
                       risk_level risk_level,
                       past_return_1y past_return_1y,
                       lock_in_months lock_in_months,
                       tenure_months tenure_months,
                       minimum_amount minimum_amount,
                       current_price current_price,
                       annual_rate annual_rate,
                       exchange_name exchange_name
                FROM investment_products
                WHERE active = 'Y'
                ORDER BY product_type, product_id
                """)
                .stream()
                .map(this::productResponse)
                .toList();
    }

    public Map<String, Object> productDetail(long productId) {
        Map<String, Object> detail = productResponse(product(productId));
        String type = String.valueOf(detail.get("productType"));

        detail.put("description",
                description(type, String.valueOf(detail.get("name"))));
        detail.put("purchaseOptions", purchaseOptions(type));
        detail.put("payoutOptions", payoutOptions(type));

        detail.put(
                "marketNote",
                Set.of("EQUITY", "ETF").contains(type)
                        ? "Illustrative delayed market trend. It is not a live quote."
                        : "Illustrative historical performance. It is not financial advice.");

        detail.put("chartLabel", "Simulated 12-month performance");
        detail.put("chartPoints", chartPoints(product(productId)));

        return detail;
    }

    public Map<String, Object> wallet(String email) {
        Customer customer = customer(email);
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> profile = profile(customer.id());

        response.put("balance", profile.get("walletBalance"));
        response.put("dematStatus", profile.get("dematStatus"));

        response.put("transactions",
                jdbc.queryForList("""
                        SELECT wallet_transaction_id id,
                               transaction_type transaction_type,
                               amount,
                               status,
                               reference_number reference_number,
                               created_at created_at
                        FROM trading_wallet_transactions
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        """, customer.id())
                        .stream()
                        .map(row -> Map.of(
                                "id", col(row, "id"),
                                "transactionType", col(row, "transaction_type"),
                                "amount", col(row, "amount"),
                                "status", col(row, "status"),
                                "referenceNumber", col(row, "reference_number"),
                                "createdAt", col(row, "created_at")))
                        .toList());

        return response;
    }

    @Transactional
    public Map<String, Object> savePan(
            String email,
            InvestmentRequests.PanRequest request) {

        Customer customer = customer(email);
        String pan = request.panNumber().toUpperCase(Locale.ROOT);

        if (profileExists(customer.id())) {
            jdbc.update("""
                    UPDATE investment_profiles
                    SET pan_number = ?,
                        kyc_status = 'DEMO_VERIFIED',
                        updated_at = SYSTIMESTAMP
                    WHERE user_id = ?
                    """, pan, customer.id());
        } else {
            jdbc.update("""
                    INSERT INTO investment_profiles(user_id, pan_number, kyc_status)
                    VALUES (?, ?, 'DEMO_VERIFIED')
                    """, customer.id(), pan);
        }

        audit(
                customer.id(),
                "INVESTMENT_PAN_FORMAT_VERIFIED",
                "INVESTMENT_PROFILE",
                String.valueOf(customer.id()));

        return profile(customer.id());
    }

    @Transactional
    public Map<String, Object> sendDematOtp(
            String email,
            InvestmentRequests.DematOtpRequest request) {

        Customer customer = customer(email);

        requireVerifiedPan(customer.id());
        requireOwnedActiveAccount(request.accountId(), customer.id());

        if (!profileExists(customer.id())) {
            throw new IllegalArgumentException(
                    "Verify PAN format before opening a Demat account.");
        }

        Map<String, Object> existing = profileRow(customer.id());

        Timestamp expiry = timestamp(col(existing, "demat_otp_expiry"));

        if ("PENDING_OTP".equals(col(existing, "demat_status"))
                && expiry != null
                && expiry.toLocalDateTime().isAfter(LocalDateTime.now())) {

            return Map.of(
                    "alreadySent", true,
                    "message",
                    "A Demat OTP has already been sent. "
                            + "Enter that OTP or wait for it to expire.");
        }

        String otp = otp();

        jdbc.update("""
                UPDATE investment_profiles
                SET linked_account_id = ?,
                    demat_status = 'PENDING_OTP',
                    demat_otp_hash = ?,
                    demat_otp_expiry = ?,
                    updated_at = SYSTIMESTAMP
                WHERE user_id = ?
                """,
                request.accountId(),
                encoder.encode(otp),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)),
                customer.id());

        sendOtp(customer, otp, "Demat account opening");

        audit(
                customer.id(),
                "DEMAT_OTP_SENT",
                "INVESTMENT_PROFILE",
                String.valueOf(customer.id()));

        return Map.of(
                "message",
                "An OTP was created. No Demat account is active until you confirm it.",
                "demoOtp",
                otp);
    }

    @Transactional
    public Map<String, Object> verifyDematOtp(
            String email,
            InvestmentRequests.OtpRequest request) {

        Customer customer = customer(email);
        Map<String, Object> profile = profileRow(customer.id());

        if (!"PENDING_OTP".equals(col(profile, "demat_status"))) {
            throw new IllegalArgumentException(
                    "Start Demat account verification first.");
        }

        Timestamp expiry = timestamp(col(profile, "demat_otp_expiry"));

        if (expiry == null
                || expiry.toLocalDateTime().isBefore(LocalDateTime.now())
                || !encoder.matches(
                        request.otp(),
                        String.valueOf(col(profile, "demat_otp_hash")))) {

            throw new IllegalArgumentException(
                    "The Demat OTP is invalid or expired.");
        }

        String number = "120"
                + String.format(
                        "%013d",
                        customer.id() * 1000 + System.currentTimeMillis() % 1000);

        jdbc.update("""
                UPDATE investment_profiles
                SET demat_number = ?,
                    demat_status = 'ACTIVE',
                    demat_otp_hash = NULL,
                    demat_otp_expiry = NULL,
                    updated_at = SYSTIMESTAMP
                WHERE user_id = ?
                """, number, customer.id());

        audit(
                customer.id(),
                "DEMAT_OPENED",
                "INVESTMENT_PROFILE",
                String.valueOf(customer.id()));

        return profile(customer.id());
    }

    @Transactional
    public Map<String, Object> startWalletTransfer(
            String email,
            InvestmentRequests.WalletRequest request,
            String type) {

        Customer customer = customer(email);
        Map<String, Object> profile = profile(customer.id());

        if (!"ACTIVE".equals(profile.get("dematStatus"))) {
            throw new IllegalArgumentException(
                    "Open and verify a Demat account before using the Trading Wallet.");
        }

        requireOwnedActiveAccount(request.accountId(), customer.id());

        if (!request.accountId().equals(number(profile.get("linkedAccountId")))) {
            throw new IllegalArgumentException(
                    "Use the account linked to your Demat profile.");
        }

        String otp = otp();
        String reference = ("FUND".equals(type) ? "WALF" : "WALW") + token();

        jdbc.update("""
                INSERT INTO trading_wallet_transactions(
                    user_id,
                    profile_id,
                    linked_account_id,
                    transaction_type,
                    amount,
                    otp_hash,
                    otp_expiry,
                    status,
                    reference_number
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING_OTP', ?)
                """,
                customer.id(),
                col(profileRow(customer.id()), "profile_id"),
                request.accountId(),
                type,
                request.amount(),
                encoder.encode(otp),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)),
                reference);

        Long id = jdbc.queryForObject("""
                SELECT wallet_transaction_id
                FROM trading_wallet_transactions
                WHERE reference_number = ?
                """, Long.class, reference);

        sendOtp(customer, otp, "Trading Wallet " + type.toLowerCase(Locale.ROOT));

        audit(
                customer.id(),
                "TRADING_WALLET_OTP_SENT",
                "TRADING_WALLET",
                String.valueOf(id));

        return Map.of(
                "walletTransactionId",
                id,
                "message",
                "No money has moved. Enter the OTP to authorise it.",
                "demoOtp",
                otp);
    }

    @Transactional
    public Map<String, Object> authoriseWalletTransfer(
            String email,
            long id,
            InvestmentRequests.OtpRequest request) {

        Customer customer = customer(email);

        Map<String, Object> transaction = jdbc.queryForMap("""
                SELECT *
                FROM trading_wallet_transactions
                WHERE wallet_transaction_id = ?
                  AND user_id = ?
                FOR UPDATE
                """, id, customer.id());

        verifyPendingOtp(transaction, request.otp());

        BigDecimal amount = decimal(col(transaction, "amount"));
        String type = String.valueOf(col(transaction, "transaction_type"));
        String reference = String.valueOf(col(transaction, "reference_number"));

        if ("FUND".equals(type)) {
            debit(
                    customer,
                    number(col(transaction, "linked_account_id")),
                    amount,
                    "TRADING_WALLET_FUND",
                    reference);

            jdbc.update("""
                    UPDATE investment_profiles
                    SET wallet_balance = wallet_balance + ?,
                        updated_at = SYSTIMESTAMP
                    WHERE user_id = ?
                    """, amount, customer.id());

        } else {
            // Lock the wallet before checking its balance and crediting Banking.
                Map<String, Object> profile =
                        profileRowForUpdate(customer.id());

            if (decimal(col(profile, "wallet_balance")).compareTo(amount) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient Trading Wallet balance.");
            }

            credit(
                    customer,
                    number(col(transaction, "linked_account_id")),
                    amount,
                    "TRADING_WALLET_WITHDRAWAL",
                    reference);

            jdbc.update("""
                    UPDATE investment_profiles
                    SET wallet_balance = wallet_balance - ?,
                        updated_at = SYSTIMESTAMP
                    WHERE user_id = ?
                    """, amount, customer.id());
        }

        jdbc.update("""
                UPDATE trading_wallet_transactions
                SET status = 'COMPLETED',
                    completed_at = SYSTIMESTAMP,
                    otp_hash = NULL,
                    otp_expiry = NULL,
                    updated_at = SYSTIMESTAMP
                WHERE wallet_transaction_id = ?
                """, id);

        audit(
                customer.id(),
                "TRADING_WALLET_" + type + "_COMPLETED",
                "TRADING_WALLET",
                String.valueOf(id));

        return Map.of(
                "message",
                "FUND".equals(type)
                        ? "Trading Wallet funded successfully."
                        : "Wallet funds transferred to your savings account.",
                "referenceNumber",
                reference,
                "walletBalance",
                profile(customer.id()).get("walletBalance"));
    }

    @Transactional
    public Map<String, Object> startPurchase(
            String email,
            InvestmentRequests.PurchaseRequest request) {

        Customer customer = customer(email);

        Map<String, Object> product = product(request.productId());

        requireOwnedActiveAccount(request.accountId(), customer.id());

        String productType = String.valueOf(col(product, "product_type"));
        Integer selectedTenureMonths = request.tenureMonths();
        if ("FD".equals(productType)) {
            if (selectedTenureMonths == null || selectedTenureMonths < 12
                    || selectedTenureMonths > 600) {
                throw new IllegalArgumentException("Choose a Fixed Deposit tenure from 12 to 600 months.");
            }
        } else if ("RD".equals(productType)) {
            if (selectedTenureMonths == null || selectedTenureMonths < 1
                    || selectedTenureMonths > 120) {
                throw new IllegalArgumentException("Choose a Recurring Deposit tenure from 1 to 120 months.");
            }
        } else if (selectedTenureMonths != null) {
            throw new IllegalArgumentException("Tenure can be selected only for Fixed or Recurring Deposits.");
        }

        boolean unitBased = Set.of("EQUITY", "ETF").contains(productType);

        BigDecimal price = col(product, "current_price") == null
                ? BigDecimal.ONE
                : decimal(col(product, "current_price"));

        if ("Y".equals(col(product, "requires_demat"))
                && !"ACTIVE".equals(profile(customer.id()).get("dematStatus"))) {

            throw new IllegalArgumentException(
                    "Open and verify your Demat account before buying Stocks or ETFs.");
        }

        BigDecimal amount = request.amount();
        BigDecimal quantity = request.quantity();

        if (unitBased) {
            if (quantity == null) {
                throw new IllegalArgumentException("Enter the quantity to buy.");
            }

            amount = price.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        } else {
            if (amount == null) {
                throw new IllegalArgumentException(
                        "Enter the investment amount.");
            }

            quantity = amount.divide(price, 4, RoundingMode.DOWN);
        }

        if (amount.compareTo(decimal(col(product, "minimum_amount"))) < 0) {
            throw new IllegalArgumentException(
                    "The amount is below this product's minimum investment.");
        }

        String mode = request.mode() == null
                ? "LUMPSUM"
                : request.mode().toUpperCase(Locale.ROOT);

        if (!Set.of("LUMPSUM", "SIP", "AUTO_DEBIT").contains(mode)) {
            throw new IllegalArgumentException(
                    "Choose Lump sum, SIP, or Auto-debit.");
        }

        String otp = otp();
        String reference = "INV" + token();

        jdbc.update("""
                INSERT INTO investment_orders(
                    user_id,
                    source_account_id,
                    product_id,
                    order_kind,
                    investment_mode,
                    amount,
                    quantity,
                    selected_tenure_months,
                    exchange_name,
                    otp_hash,
                    otp_expiry,
                    status,
                    reference_number
                )
                VALUES (?, ?, ?, 'BUY', ?, ?, ?, ?, ?, ?, ?, 'PENDING_OTP', ?)
                """,
                customer.id(),
                request.accountId(),
                request.productId(),
                mode,
                amount,
                quantity,
                selectedTenureMonths,
                request.exchange() == null
                        ? col(product, "exchange_name")
                        : request.exchange(),
                encoder.encode(otp),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)),
                reference);

        Long id = jdbc.queryForObject("""
                SELECT order_id
                FROM investment_orders
                WHERE reference_number = ?
                """, Long.class, reference);

        sendOtp(customer, otp, "investment authorisation");

        audit(
                customer.id(),
                "INVESTMENT_OTP_SENT",
                "INVESTMENT_ORDER",
                String.valueOf(id));

        return Map.of(
                "orderId",
                id,
                "referenceNumber",
                reference,
                "message",
                "No money has been debited; enter the OTP to authorise it.",
                "demoOtp",
                otp);
    }

    @Transactional
        public Map<String, Object> startRedemption(
                String email,
                long holdingId,
                InvestmentRequests.RedemptionRequest request) {

        Customer customer = customer(email);

        requireOwnedActiveAccount(
                request.accountId(),
                customer.id()
        );

        if (request.amount() != null) {
                throw new IllegalArgumentException(
                        "Redemption amount is calculated from the holding. "
                                + "Omit amount and provide quantity only for "
                                + "a partial redemption."
                );
        }

        List<Map<String, Object>> holdings = jdbc.queryForList("""
                SELECT *
                FROM investment_holdings
                WHERE holding_id = ?
                AND user_id = ?
                AND status = 'ACTIVE'
                FOR UPDATE
                """,
                holdingId,
                customer.id()
        );

        if (holdings.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "An active holding was not found."
                );
        }

        Map<String, Object> holding = holdings.get(0);

        BigDecimal holdingQuantity = decimal(
        col(holding, "quantity")
);

Map<String, Object> redemptionProduct = product(
        number(col(holding, "product_id"))
);

boolean marketProduct = Set.of(
        "EQUITY",
        "ETF",
        "MUTUAL_FUND"
).contains(
        String.valueOf(
                col(redemptionProduct, "product_type")
        )
);

BigDecimal holdingValue;

if (marketProduct
        && col(redemptionProduct, "current_price") != null) {

    BigDecimal latestPrice = decimal(
            col(redemptionProduct, "current_price")
    );

    holdingValue = holdingQuantity
            .multiply(latestPrice)
            .setScale(2, RoundingMode.HALF_UP);

    jdbc.update("""
            UPDATE investment_holdings
               SET current_value = ?,
                   updated_at = SYSTIMESTAMP
             WHERE holding_id = ?
            """,
            holdingValue,
            holdingId
    );

    holding.put("current_value", holdingValue);

} else {
    holdingValue = decimal(
            col(holding, "current_value")
    );
}

        if (holdingQuantity.signum() <= 0
                || holdingValue.signum() < 0) {

                throw new IllegalStateException(
                        "The holding has an invalid quantity or current value."
                );
        }

        BigDecimal quantity = request.quantity() == null
                ? holdingQuantity
                : request.quantity();

        if (quantity.signum() <= 0
                || quantity.compareTo(holdingQuantity) > 0) {

                throw new IllegalArgumentException(
                        "You cannot redeem more units than you hold."
                );
        }

        // Server-calculated payout. The customer cannot set this amount.
        BigDecimal amount = holdingValue
                .multiply(quantity)
                .divide(
                        holdingQuantity,
                        2,
                        RoundingMode.HALF_UP
                );

        if (amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        "The calculated redemption amount must be greater than zero."
                );
        }

        String otp = otp();
        String reference = "RED" + token();

        jdbc.update("""
                INSERT INTO investment_orders (
                        user_id,
                        source_account_id,
                        product_id,
                        holding_id,
                        order_kind,
                        investment_mode,
                        amount,
                        quantity,
                        otp_hash,
                        otp_expiry,
                        status,
                        reference_number
                )
                VALUES (
                        ?, ?, ?, ?, 'REDEEM', 'LUMPSUM',
                        ?, ?, ?, ?, 'PENDING_OTP', ?
                )
                """,
                customer.id(),
                request.accountId(),
                col(holding, "product_id"),
                holdingId,
                amount,
                quantity,
                encoder.encode(otp),
                Timestamp.valueOf(
                        LocalDateTime.now().plusMinutes(5)
                ),
                reference
        );

        Long orderId = jdbc.queryForObject("""
                SELECT order_id
                FROM investment_orders
                WHERE reference_number = ?
                """,
                Long.class,
                reference
        );

        sendOtp(
                customer,
                otp,
                "investment redemption"
        );

        audit(
                customer.id(),
                "INVESTMENT_REDEMPTION_OTP_SENT",
                "INVESTMENT_ORDER",
                String.valueOf(orderId)
        );

        return Map.of(
                "orderId", orderId,
                "holdingId", holdingId,
                "quantity", quantity,
                "calculatedAmount", amount,
                "referenceNumber", reference,
                "message",
                "No money has moved. Enter the OTP to authorise redemption.",
                "demoOtp", otp
        );
        }
    @Transactional
    public Map<String, Object> authorisePurchase(
            String email,
            long id,
            InvestmentRequests.OtpRequest request) {

        Customer customer = customer(email);

        Map<String, Object> order = jdbc.queryForMap("""
                SELECT *
                FROM investment_orders
                WHERE order_id = ?
                  AND user_id = ?
                FOR UPDATE
                """, id, customer.id());

        verifyPendingOtp(order, request.otp());

        BigDecimal amount = decimal(col(order, "amount"));

        if ("REDEEM".equals(col(order, "order_kind"))) {
            return completeRedemption(customer, order, amount, id);
        }

        Map<String, Object> product =
                product(number(col(order, "product_id")));

        String mode = String.valueOf(col(order, "investment_mode"));

        if (!"LUMPSUM".equals(mode)) {
            String type = String.valueOf(col(product, "product_type"));

            if (!Set.of("MUTUAL_FUND", "RD", "PPF").contains(type)) {
                throw new IllegalArgumentException(
                        "This product is available only as a lump-sum investment.");
            }

            String schedule = "MUTUAL_FUND".equals(type) ? "SIP" : type;

            jdbc.update("""
                    INSERT INTO investment_schedules(
                        user_id,
                        source_account_id,
                        product_id,
                        schedule_type,
                        amount,
                        debit_day,
                        next_debit_date,
                        tenure_months,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    customer.id(),
                    col(order, "source_account_id"),
                    col(order, "product_id"),
                    schedule,
                    amount,
                    Math.min(28, LocalDate.now().getDayOfMonth()),
                    java.sql.Date.valueOf(
                            LocalDate.now()
                                    .plusMonths(1)
                                    .withDayOfMonth(
                                            Math.min(
                                                    28,
                                                    LocalDate.now().getDayOfMonth()))),
                    col(order, "selected_tenure_months") == null
                            ? col(product, "tenure_months")
                            : col(order, "selected_tenure_months"));

            jdbc.update("""
                    UPDATE investment_orders
                    -- Instruction activation is not a funded purchase.
                    SET status = 'INSTRUCTION_ACTIVE',
                        authorised_at = SYSTIMESTAMP,
                        otp_hash = NULL,
                        otp_expiry = NULL,
                        updated_at = SYSTIMESTAMP
                    WHERE order_id = ?
                    """, id);

            audit(
                    customer.id(),
                    "INVESTMENT_SCHEDULE_AUTHORISED",
                    "INVESTMENT_ORDER",
                    String.valueOf(id));

            return Map.of(
                    "message",
                    schedule + " auto-debit instruction is active. "
                            + "No money was debited today.",
                    "referenceNumber",
                    col(order, "reference_number"),
                    "status",
                    "INSTRUCTION_ACTIVE");
        }

        boolean wallet = "Y".equals(col(product, "requires_demat"));

        if (!wallet) {
            String eventId = debitQueue.queueDebitAfterOtpVerification(
                    id,
                    customer.id());

            audit(
                    customer.id(),
                    "INVESTMENT_DEBIT_QUEUED",
                    "INVESTMENT_ORDER",
                    String.valueOf(id));

            return Map.of(
                    "message",
                    "Investment authorised. Your bank-account debit is being processed.",
                    "referenceNumber",
                    col(order, "reference_number"),
                    "status",
                    "DEBIT_PENDING",
                    "eventId",
                    eventId);
        }

        // Lock the wallet before checking and deducting purchase funds.
        Map<String, Object> profile =
                profileRowForUpdate(customer.id());

        if (decimal(col(profile, "wallet_balance")).compareTo(amount) < 0) {
            fail(id, "INSUFFICIENT_WALLET_BALANCE");

            throw new IllegalArgumentException(
                    "Insufficient Trading Wallet balance.");
        }

        jdbc.update("""
                UPDATE investment_profiles
                SET wallet_balance = wallet_balance - ?,
                    updated_at = SYSTIMESTAMP
                WHERE user_id = ?
                """, amount, customer.id());

        jdbc.update("""
                INSERT INTO trading_wallet_transactions(
                    user_id,
                    profile_id,
                    linked_account_id,
                    order_id,
                    transaction_type,
                    amount,
                    status,
                    reference_number,
                    completed_at
                )
                VALUES (?, ?, ?, ?, 'PURCHASE', ?, 'COMPLETED', ?, SYSTIMESTAMP)
                """,
                customer.id(),
                col(profile, "profile_id"),
                col(order, "source_account_id"),
                id,
                amount,
                "W" + col(order, "reference_number"));

        BigDecimal quantity = decimal(col(order, "quantity"));

        String folioOrDematReference =
                String.valueOf(profile(customer.id()).get("dematNumber"));

        // SETTLEMENT CHANGE: market holdings are not redeemable before settlement.
        boolean marketProduct = Set.of("EQUITY", "ETF").contains(
                String.valueOf(col(product, "product_type")));
        String holdingStatus = marketProduct ? "SETTLEMENT_PENDING" : "ACTIVE";
        String status = marketProduct ? "SETTLEMENT_PENDING" : "ALLOTTED";

        jdbc.update("""
                INSERT INTO investment_holdings(
                    user_id,
                    product_id,
                    source_order_id,
                    folio_or_demat_ref,
                    quantity,
                    invested_amount,
                    average_cost,
                    current_value,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                customer.id(),
                col(order, "product_id"),
                id,
                folioOrDematReference,
                quantity,
                amount,
                amount.divide(quantity, 4, RoundingMode.HALF_UP),
                amount,
                holdingStatus);

        jdbc.update("""
                UPDATE investment_orders
                SET status = ?,
                    authorised_at = SYSTIMESTAMP,
                    otp_hash = NULL,
                    otp_expiry = NULL,
                    updated_at = SYSTIMESTAMP
                WHERE order_id = ?
                """, status, id);

        audit(
                customer.id(),
                "INVESTMENT_AUTHORISED",
                "INVESTMENT_ORDER",
                String.valueOf(id));
        String customerMessage = marketProduct
                ? "Investment authorised and your Trading Wallet has been debited. "
                        + "Market settlement will complete automatically in about one minute."
                : "Investment authorised and your Trading Wallet has been debited.";

        return Map.of(
                "message",
                customerMessage,
                "referenceNumber",
                col(order, "reference_number"),
                "status",
                status);
    }

    @Transactional
    public Map<String, Object> createSchedule(
            String email,
            InvestmentRequests.ScheduleRequest request) {

        Customer customer = customer(email);

        Map<String, Object> product = product(request.productId());
        String type = String.valueOf(col(product, "product_type"));

        if (!Set.of("MUTUAL_FUND", "RD", "PPF").contains(type)) {
            throw new IllegalArgumentException(
                    "Only Mutual Funds, RD, and PPF support recurring instructions.");
        }

        requireOwnedActiveAccount(request.accountId(), customer.id());

        if (request.debitDay() < 1 || request.debitDay() > 28) {
            throw new IllegalArgumentException(
                    "Choose a monthly debit day from 1 to 28.");
        }

        String schedule = "MUTUAL_FUND".equals(type) ? "SIP" : type;

        jdbc.update("""
                INSERT INTO investment_schedules(
                    user_id,
                    source_account_id,
                    product_id,
                    schedule_type,
                    amount,
                    debit_day,
                    next_debit_date,
                    tenure_months,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                customer.id(),
                request.accountId(),
                request.productId(),
                schedule,
                request.amount(),
                request.debitDay(),
                java.sql.Date.valueOf(
                        firstFutureDebitDate(request.debitDay())),
                request.tenureMonths());

        audit(
                customer.id(),
                "INVESTMENT_SCHEDULE_CREATED",
                "INVESTMENT_PRODUCT",
                String.valueOf(request.productId()));

        return Map.of(
                "message",
                "Your " + schedule + " auto-debit instruction is active. "
                        + "Each future debit will require scheduled processing.");
    }


    @Transactional
        public Map<String, Object> resendOrderOtp(
                String email,
                long orderId) {

        Customer customer = customer(email);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT order_id,
                        order_kind,
                        status,
                        otp_hash,
                        updated_at
                FROM investment_orders
                WHERE order_id = ?
                AND user_id = ?
                FOR UPDATE
                """,
                orderId,
                customer.id()
        );

        if (rows.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investment order was not found."
                );
        }

        Map<String, Object> order = rows.get(0);

        if (!"PENDING_OTP".equals(col(order, "status"))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "OTP can only be resent for a pending order."
                );
        }

        LocalDateTime now = LocalDateTime.now();

        Timestamp lastUpdated = timestamp(col(order, "updated_at"));

        if (lastUpdated != null
                && now.isBefore(
                        lastUpdated.toLocalDateTime().plusSeconds(60))) {

                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait one minute before requesting another OTP."
                );
        }

        String previousHash = (String) col(order, "otp_hash");
        String newOtp;

        // Ensure the replacement differs from the previous OTP.
        do {
                newOtp = otp();
        } while (previousHash != null
                && encoder.matches(newOtp, previousHash));

        LocalDateTime expiresAt = now.plusMinutes(5);

        jdbc.update("""
                UPDATE investment_orders
                SET otp_hash = ?,
                        otp_expiry = ?,
                        updated_at = ?
                WHERE order_id = ?
                AND user_id = ?
                AND status = 'PENDING_OTP'
                """,
                encoder.encode(newOtp),
                Timestamp.valueOf(expiresAt),
                Timestamp.valueOf(now),
                orderId,
                customer.id()
        );

        String purpose = "REDEEM".equals(col(order, "order_kind"))
                ? "investment redemption"
                : "investment authorisation";

        otpEmails.resendOtp(
                new IdentityOtpEmailClient.OtpEmailRequest(
                        customer.email(),
                        newOtp,
                        purpose
                )
        );

        audit(
                customer.id(),
                "INVESTMENT_OTP_RESENT",
                "INVESTMENT_ORDER",
                String.valueOf(orderId)
        );

        return Map.of(
                "orderId", orderId,
                "status", "PENDING_OTP",
                "expiresAt", expiresAt,
                "message",
                "A replacement OTP email was requested. "
                        + "The previous OTP is no longer valid."
        );
        }

        public List<Map<String, Object>> orders(
        String email,
        String status) {

    Customer customer = customer(email);

    String normalizedStatus = status == null || status.isBlank()
            ? null
            : status.trim().toUpperCase(Locale.ROOT);

    String sql = """
            SELECT o.order_id,
                   o.user_id,
                   o.source_account_id,
                   o.product_id,
                   o.schedule_id,
                   o.order_kind,
                   o.investment_mode,
                   o.amount,
                   o.quantity,
                   o.selected_tenure_months,
                   o.status,
                   o.reference_number,
                   o.banking_transaction_id,
                   o.banking_reference_number,
                   o.failure_code,
                   o.failure_reason,
                   o.requested_at,
                   o.authorised_at,
                   o.debited_at,
                   o.allotted_at,
                   p.product_code,
                   p.product_name,
                   p.product_type
            FROM investment_orders o
            JOIN investment_products p
              ON p.product_id = o.product_id
            WHERE o.user_id = ?
            """;

    List<Map<String, Object>> rows;

    if (normalizedStatus == null) {
        rows = jdbc.queryForList(
                sql + """
                        ORDER BY o.requested_at DESC,
                                 o.order_id DESC
                        """,
                customer.id()
        );
    } else {
        rows = jdbc.queryForList(
                sql + """
                        AND o.status = ?
                        ORDER BY o.requested_at DESC,
                                 o.order_id DESC
                        """,
                customer.id(),
                normalizedStatus
        );
    }

    return rows.stream()
            .map(row -> {
                Map<String, Object> response =
                        adminOrderResponse(row);

                /*
                 * The authenticated customer should not receive their
                 * internal user ID in each history row.
                 */
                response.remove("customerUserId");

                return response;
            })
            .toList();
}

public Map<String, Object> monthlyStatement(
        String email,
        int year,
        int month) {

    if (month < 1 || month > 12) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Month must be between 1 and 12."
        );
    }

    Customer customer = customer(email);

    YearMonth statementMonth = YearMonth.of(year, month);

    LocalDateTime from = statementMonth
            .atDay(1)
            .atStartOfDay();

    LocalDateTime to = statementMonth
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay();

    List<Map<String, Object>> entries = jdbc.queryForList("""
            SELECT o.order_id,
                   o.order_kind,
                   o.investment_mode,
                   o.amount,
                   o.quantity,
                   o.selected_tenure_months,
                   o.status,
                   o.reference_number,
                   o.failure_code,
                   o.failure_reason,
                   o.requested_at,
                   o.authorised_at,
                   o.debited_at,
                   o.allotted_at,
                   p.product_code,
                   p.product_name,
                   p.product_type,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM investment_holdings funded_holding
                       WHERE funded_holding.source_order_id = o.order_id
                         AND funded_holding.user_id = o.user_id
                   ) THEN 1 ELSE 0 END AS has_purchase_holding
            FROM investment_orders o
            JOIN investment_products p
              ON p.product_id = o.product_id
            WHERE o.user_id = ?
              AND o.requested_at >= ?
              AND o.requested_at < ?
            ORDER BY o.requested_at ASC,
                     o.order_id ASC
            """,
            customer.id(),
            Timestamp.valueOf(from),
            Timestamp.valueOf(to)
    ).stream()
            .map(this::statementEntryResponse)
            .toList();

    BigDecimal successfulPurchases = entries.stream()
            .filter(entry -> "BUY".equals(
                    entry.get("orderKind")
            ))
            .filter(entry -> Set.of(
                    "ALLOTTED",
                    "SETTLEMENT_PENDING"
            ).contains(entry.get("status")))
            // A holding links this BUY to an actual purchase, including a
            // historical purchase whose holding was subsequently redeemed.
            .filter(entry -> Boolean.TRUE.equals(entry.get("fundedPurchase")))
            .map(entry -> decimal(entry.get("amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal completedRedemptions = entries.stream()
            .filter(entry -> "REDEEM".equals(
                    entry.get("orderKind")
            ))
            .filter(entry -> "REDEEMED".equals(
                    entry.get("status")
            ))
            .map(entry -> decimal(entry.get("amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<Map<String, Object>> activeHoldings = jdbc.queryForList("""
            SELECT h.holding_id,
                   p.product_code,
                   p.product_name,
                   p.product_type,
                   h.quantity,
                   h.invested_amount,
                   h.current_value,
                   h.current_value - h.invested_amount gain_loss,
                   h.status
            FROM investment_holdings h
            JOIN investment_products p
              ON p.product_id = h.product_id
            WHERE h.user_id = ?
              AND h.status = 'ACTIVE'
            ORDER BY h.opened_at DESC
            """,
            customer.id()
    ).stream()
            .map(this::statementHoldingResponse)
            .toList();

    BigDecimal currentInvestedAmount = activeHoldings.stream()
            .map(row -> decimal(row.get("investedAmount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal currentPortfolioValue = activeHoldings.stream()
            .map(row -> decimal(row.get("currentValue")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("statementMonth", statementMonth.toString());
    response.put("statementFrom", statementMonth.atDay(1));
    response.put("statementTo", statementMonth.atEndOfMonth());
    response.put("totalSuccessfulPurchases", successfulPurchases);
    response.put("totalCompletedRedemptions", completedRedemptions);
    response.put("netInvestmentForMonth",
            successfulPurchases.subtract(completedRedemptions));
    response.put("currentInvestedAmount", currentInvestedAmount);
    response.put("currentPortfolioValue", currentPortfolioValue);
    response.put("currentGainLoss",
            currentPortfolioValue.subtract(currentInvestedAmount));
    response.put("entries", entries);
    response.put("activeHoldings", activeHoldings);

    return response;
}

private Map<String, Object> statementEntryResponse(
        Map<String, Object> row) {

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("orderId", col(row, "order_id"));
    response.put("orderKind", col(row, "order_kind"));
    response.put("investmentMode", col(row, "investment_mode"));
    response.put("productCode", col(row, "product_code"));
    response.put("productName", col(row, "product_name"));
    response.put("productType", col(row, "product_type"));
    response.put("amount", col(row, "amount"));
    response.put("quantity", col(row, "quantity"));
    String status = String.valueOf(col(row, "status"));
    String mode = String.valueOf(col(row, "investment_mode"));
    boolean buy = "BUY".equals(col(row, "order_kind"));
    boolean hasHolding = decimal(col(row, "has_purchase_holding")).signum() > 0;

    // Compatibility: old instruction-only orders were marked ALLOTTED.
    // Real AUTO_DEBIT purchases have a source-order holding and remain purchases.
    boolean legacyInstruction = buy
            && Set.of("SIP", "AUTO_DEBIT").contains(mode)
            && "ALLOTTED".equals(status)
            && !hasHolding;
    boolean instruction = "INSTRUCTION_ACTIVE".equals(status) || legacyInstruction;

    response.put("status", instruction ? "INSTRUCTION_ACTIVE" : status);
    response.put("activityType", instruction
            ? "SCHEDULE_INSTRUCTION"
            : (buy ? "PURCHASE" : "REDEMPTION"));
    response.put("fundedPurchase", buy && hasHolding
            && Set.of("ALLOTTED", "SETTLEMENT_PENDING").contains(status));
    response.put("referenceNumber", col(row, "reference_number"));
    response.put("failureCode", col(row, "failure_code"));
    response.put("failureReason", col(row, "failure_reason"));
    response.put("requestedAt", col(row, "requested_at"));
    response.put("authorisedAt", col(row, "authorised_at"));
    response.put("debitedAt", col(row, "debited_at"));
    response.put("allottedAt", col(row, "allotted_at"));

    return response;
}

private Map<String, Object> statementHoldingResponse(
        Map<String, Object> row) {

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("holdingId", col(row, "holding_id"));
    response.put("productCode", col(row, "product_code"));
    response.put("productName", col(row, "product_name"));
    response.put("productType", col(row, "product_type"));
    response.put("quantity", col(row, "quantity"));
    response.put("investedAmount", col(row, "invested_amount"));
    response.put("currentValue", col(row, "current_value"));
    response.put("gainLoss", col(row, "gain_loss"));
    response.put("status", col(row, "status"));

    return response;
}



public List<Map<String, Object>> adminOrders(
        String adminEmail,
        String status) {

    requireActiveAdmin(adminEmail);

    String normalizedStatus = status == null || status.isBlank()
            ? null
            : status.trim().toUpperCase(Locale.ROOT);

    String sql = """
            SELECT o.order_id,
                   o.user_id,
                   o.source_account_id,
                   o.product_id,
                   o.schedule_id,
                   o.order_kind,
                   o.investment_mode,
                   o.amount,
                   o.quantity,
                   o.selected_tenure_months,
                   o.status,
                   o.reference_number,
                   o.banking_transaction_id,
                   o.banking_reference_number,
                   o.failure_code,
                   o.failure_reason,
                   o.requested_at,
                   o.authorised_at,
                   o.debited_at,
                   o.allotted_at,
                   p.product_code,
                   p.product_name,
                   p.product_type
            FROM investment_orders o
            JOIN investment_products p
              ON p.product_id = o.product_id
            """;

    List<Map<String, Object>> rows;

    if (normalizedStatus == null) {
        rows = jdbc.queryForList(
                sql + """
                        ORDER BY o.requested_at DESC,
                                 o.order_id DESC
                        """
        );
    } else {
        rows = jdbc.queryForList(
                sql + """
                        WHERE o.status = ?
                        ORDER BY o.requested_at DESC,
                                 o.order_id DESC
                        """,
                normalizedStatus
        );
    }

    return rows.stream()
            .map(this::adminOrderResponse)
            .toList();
}

public List<Map<String, Object>> maturedFixedDeposits(String adminEmail) {
    requireActiveAdmin(adminEmail);
    return jdbc.queryForList("""
            SELECT h.holding_id,
                   h.user_id,
                   p.product_name,
                   h.invested_amount,
                   h.current_value,
                   h.tenure_months,
                   h.opened_at,
                   ADD_MONTHS(CAST(h.opened_at AS DATE), h.tenure_months) maturity_date,
                   h.status
              FROM investment_holdings h
              JOIN investment_products p ON p.product_id = h.product_id
             WHERE p.product_type = 'FD'
               AND h.status = 'MATURED'
             ORDER BY maturity_date, h.holding_id
            """).stream().map(row -> {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("holdingId", col(row, "holding_id"));
        result.put("customerUserId", col(row, "user_id"));
        result.put("productName", col(row, "product_name"));
        result.put("investedAmount", col(row, "invested_amount"));
        result.put("recordedValue", col(row, "current_value"));
        result.put("tenureMonths", col(row, "tenure_months"));
        result.put("openedAt", col(row, "opened_at"));
        result.put("maturityDate", col(row, "maturity_date"));
        result.put("status", col(row, "status"));
        return result;
    }).toList();
}

public List<Map<String, Object>> adminSchedules(
        String adminEmail) {

    requireActiveAdmin(adminEmail);

    return jdbc.queryForList("""
            SELECT s.schedule_id,
                   s.user_id,
                   s.source_account_id,
                   s.product_id,
                   p.product_code,
                   p.product_name,
                   s.schedule_type,
                   s.amount,
                   s.debit_day,
                   s.next_debit_date,
                   s.last_debit_date,
                   s.last_order_id,
                   s.tenure_months,
                   s.installments_completed,
                   s.status,
                   s.created_at,
                   s.updated_at,
                   s.completed_at
            FROM investment_schedules s
            JOIN investment_products p
              ON p.product_id = s.product_id
            ORDER BY s.updated_at DESC,
                     s.schedule_id DESC
            """)
            .stream()
            .map(this::adminScheduleResponse)
            .toList();
}

private void requireActiveAdmin(String email) {

    IdentityClient.IdentityUserResponse admin =
            identities.findUserByEmail(email);

    if (admin == null
            || !"ADMIN".equals(admin.role())
            || !"ACTIVE".equals(admin.status())) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only an active administrator can access investment administration."
        );
    }
}

private Map<String, Object> adminOrderResponse(
        Map<String, Object> row) {

    Map<String, Object> response = new LinkedHashMap<>();

    response.put("orderId", col(row, "order_id"));
    response.put("customerUserId", col(row, "user_id"));
    response.put("accountId", col(row, "source_account_id"));
    response.put("productId", col(row, "product_id"));
    response.put("productCode", col(row, "product_code"));
    response.put("productName", col(row, "product_name"));
    response.put("productType", col(row, "product_type"));
    response.put("scheduleId", col(row, "schedule_id"));
    response.put("orderKind", col(row, "order_kind"));
    response.put("investmentMode", col(row, "investment_mode"));
    response.put("amount", col(row, "amount"));
    response.put("quantity", col(row, "quantity"));
    response.put("tenureMonths", col(row, "selected_tenure_months"));
    response.put("status", col(row, "status"));
    response.put("referenceNumber", col(row, "reference_number"));
    response.put(
            "bankingTransactionId",
            col(row, "banking_transaction_id")
    );
    response.put(
            "bankingReferenceNumber",
            col(row, "banking_reference_number")
    );
    response.put("failureCode", col(row, "failure_code"));
    response.put("failureReason", col(row, "failure_reason"));
    response.put("requestedAt", col(row, "requested_at"));
    response.put("authorisedAt", col(row, "authorised_at"));
    response.put("debitedAt", col(row, "debited_at"));
    response.put("allottedAt", col(row, "allotted_at"));

    return response;
}

private Map<String, Object> adminScheduleResponse(
        Map<String, Object> row) {

    Map<String, Object> response = scheduleResponse(row);

    response.put("customerUserId", col(row, "user_id"));
    response.put(
            "installmentsCompleted",
            col(row, "installments_completed")
    );
    response.put("completedAt", col(row, "completed_at"));

    return response;
}

    


        public List<Map<String, Object>> schedules(String email) {

        Customer customer = customer(email);

        return jdbc.queryForList("""
                SELECT s.schedule_id,
                        s.source_account_id,
                        s.product_id,
                        p.product_code,
                        p.product_name,
                        s.schedule_type,
                        s.amount,
                        s.debit_day,
                        s.next_debit_date,
                        s.last_debit_date,
                        s.last_order_id,
                        s.tenure_months,
                        s.status,
                        s.created_at,
                        s.updated_at
                FROM investment_schedules s
                JOIN investment_products p
                ON p.product_id = s.product_id
                WHERE s.user_id = ?
                ORDER BY s.schedule_id DESC
                """, customer.id())
                .stream()
                .map(this::scheduleResponse)
                .toList();
        }

        @Transactional
        public Map<String, Object> pauseSchedule(
                String email,
                long scheduleId) {

        Customer customer = customer(email);

        Map<String, Object> schedule = ownedScheduleForUpdate(
                scheduleId,
                customer.id()
        );

        String status = String.valueOf(col(schedule, "status"));

        if ("CANCELLED".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A cancelled schedule cannot be paused."
                );
        }

        if ("PAUSED".equals(status)) {
                Map<String, Object> response = scheduleResponse(schedule);
                response.put("message", "This schedule is already paused.");
                return response;
        }

        jdbc.update("""
                UPDATE investment_schedules
                SET status = 'PAUSED',
                        updated_at = SYSTIMESTAMP
                WHERE schedule_id = ?
                AND user_id = ?
                AND status = 'ACTIVE'
                """,
                scheduleId,
                customer.id()
        );

        audit(
                customer.id(),
                "INVESTMENT_SCHEDULE_PAUSED",
                "INVESTMENT_SCHEDULE",
                String.valueOf(scheduleId)
        );

        Map<String, Object> response = scheduleResponse(
                ownedSchedule(scheduleId, customer.id())
        );

        response.put(
                "message",
                "Recurring investment schedule paused. "
                        + "No future debit will be created while it is paused."
        );

        return response;
        }

        @Transactional
        public Map<String, Object> resumeSchedule(
                String email,
                long scheduleId) {

        Customer customer = customer(email);

        Map<String, Object> schedule = ownedScheduleForUpdate(
                scheduleId,
                customer.id()
        );

        String status = String.valueOf(col(schedule, "status"));

        if ("CANCELLED".equals(status)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A cancelled schedule cannot be resumed."
                );
        }

        if ("ACTIVE".equals(status)) {
                Map<String, Object> response = scheduleResponse(schedule);
                response.put("message", "This schedule is already active.");
                return response;
        }

        int debitDay = number(
                col(schedule, "debit_day")
        ).intValue();

        LocalDate today = LocalDate.now();

        LocalDate nextDebitDate = today.withDayOfMonth(debitDay);

        /*
        * Resume only for a future monthly debit. It does not immediately
        * collect missed instalments from the paused period.
        */
        if (!nextDebitDate.isAfter(today)) {
                nextDebitDate = nextDebitDate.plusMonths(1);
        }

        jdbc.update("""
                UPDATE investment_schedules
                SET status = 'ACTIVE',
                        next_debit_date = ?,
                        updated_at = SYSTIMESTAMP
                WHERE schedule_id = ?
                AND user_id = ?
                AND status = 'PAUSED'
                """,
                java.sql.Date.valueOf(nextDebitDate),
                scheduleId,
                customer.id()
        );

        audit(
                customer.id(),
                "INVESTMENT_SCHEDULE_RESUMED",
                "INVESTMENT_SCHEDULE",
                String.valueOf(scheduleId)
        );

        Map<String, Object> response = scheduleResponse(
                ownedSchedule(scheduleId, customer.id())
        );

        response.put(
                "message",
                "Recurring investment schedule resumed. "
                        + "The next debit will be on "
                        + nextDebitDate + "."
        );

        return response;
        }

        @Transactional
        public Map<String, Object> cancelSchedule(
                String email,
                long scheduleId) {

        Customer customer = customer(email);

        Map<String, Object> schedule = ownedScheduleForUpdate(
                scheduleId,
                customer.id()
        );

        String status = String.valueOf(col(schedule, "status"));

        if ("CANCELLED".equals(status)) {
                Map<String, Object> response = scheduleResponse(schedule);
                response.put("message", "This schedule is already cancelled.");
                return response;
        }

        jdbc.update("""
                UPDATE investment_schedules
                SET status = 'CANCELLED',
                        updated_at = SYSTIMESTAMP
                WHERE schedule_id = ?
                AND user_id = ?
                AND status IN ('ACTIVE', 'PAUSED')
                """,
                scheduleId,
                customer.id()
        );

        audit(
                customer.id(),
                "INVESTMENT_SCHEDULE_CANCELLED",
                "INVESTMENT_SCHEDULE",
                String.valueOf(scheduleId)
        );

        Map<String, Object> response = scheduleResponse(
                ownedSchedule(scheduleId, customer.id())
        );

        response.put(
                "message",
                "Recurring investment schedule cancelled. "
                        + "No future debit will be created."
        );

        return response;
        }

        private Map<String, Object> ownedScheduleForUpdate(
                long scheduleId,
                Long userId) {

        List<Map<String, Object>> schedules = jdbc.queryForList("""
                SELECT s.schedule_id,
                        s.source_account_id,
                        s.product_id,
                        p.product_code,
                        p.product_name,
                        s.schedule_type,
                        s.amount,
                        s.debit_day,
                        s.next_debit_date,
                        s.last_debit_date,
                        s.last_order_id,
                        s.tenure_months,
                        s.status,
                        s.created_at,
                        s.updated_at
                FROM investment_schedules s
                JOIN investment_products p
                ON p.product_id = s.product_id
                WHERE s.schedule_id = ?
                AND s.user_id = ?
                FOR UPDATE
                """,
                scheduleId,
                userId
        );

        if (schedules.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investment schedule was not found."
                );
        }

        return schedules.get(0);
        }

        private Map<String, Object> ownedSchedule(
                long scheduleId,
                Long userId) {

        List<Map<String, Object>> schedules = jdbc.queryForList("""
                SELECT s.schedule_id,
                        s.source_account_id,
                        s.product_id,
                        p.product_code,
                        p.product_name,
                        s.schedule_type,
                        s.amount,
                        s.debit_day,
                        s.next_debit_date,
                        s.last_debit_date,
                        s.last_order_id,
                        s.tenure_months,
                        s.status,
                        s.created_at,
                        s.updated_at
                FROM investment_schedules s
                JOIN investment_products p
                ON p.product_id = s.product_id
                WHERE s.schedule_id = ?
                AND s.user_id = ?
                """,
                scheduleId,
                userId
        );

        if (schedules.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Investment schedule was not found."
                );
        }

        return schedules.get(0);
        }

        private Map<String, Object> scheduleResponse(
                Map<String, Object> row) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("scheduleId", col(row, "schedule_id"));
        response.put("accountId", col(row, "source_account_id"));
        response.put("productId", col(row, "product_id"));
        response.put("productCode", col(row, "product_code"));
        response.put("productName", col(row, "product_name"));
        response.put("scheduleType", col(row, "schedule_type"));
        response.put("amount", col(row, "amount"));
        response.put("debitDay", col(row, "debit_day"));
        response.put("nextDebitDate", col(row, "next_debit_date"));
        response.put("lastDebitDate", col(row, "last_debit_date"));
        response.put("lastOrderId", col(row, "last_order_id"));
        response.put("tenureMonths", col(row, "tenure_months"));
        response.put("status", col(row, "status"));
        response.put("createdAt", col(row, "created_at"));
        response.put("updatedAt", col(row, "updated_at"));

        return response;
        }

    private Map<String, Object> completeRedemption(
            Customer customer,
            Map<String, Object> order,
            BigDecimal amount,
            long orderId) {

        Map<String, Object> holding = jdbc.queryForMap("""
                SELECT *
                FROM investment_holdings
                WHERE holding_id = ?
                  AND user_id = ?
                FOR UPDATE
                """,
                col(order, "holding_id"),
                customer.id());

        BigDecimal quantity = decimal(col(order, "quantity"));

        BigDecimal remaining = decimal(col(holding, "quantity"))
                .subtract(quantity);

        if (remaining.signum() < 0) {
            throw new IllegalArgumentException(
                    "This holding no longer has enough units to redeem.");
        }

        String state = remaining.signum() == 0
        ? "CLOSED"
        : "ACTIVE";

Map<String, Object> holdingProduct = product(
        number(col(holding, "product_id"))
);

boolean marketProduct = Set.of(
        "EQUITY",
        "ETF",
        "MUTUAL_FUND"
).contains(
        String.valueOf(
                col(holdingProduct, "product_type")
        )
);

BigDecimal remainingAmount = decimal(
        col(holding, "invested_amount")
).subtract(
        decimal(col(holding, "average_cost"))
                .multiply(quantity)
).max(BigDecimal.ZERO);

BigDecimal remainingCurrentValue;

if (remaining.signum() == 0) {
    remainingCurrentValue = BigDecimal.ZERO;

} else if (marketProduct
        && col(holdingProduct, "current_price") != null) {

    remainingCurrentValue = remaining.multiply(
            decimal(col(holdingProduct, "current_price"))
    ).setScale(2, RoundingMode.HALF_UP);

} else {
    /*
     * Keeps accrued value proportional for FD, RD, and PPF.
     */
    remainingCurrentValue = decimal(
            col(holding, "current_value")
    ).multiply(remaining)
            .divide(
                    decimal(col(holding, "quantity")),
                    2,
                    RoundingMode.HALF_UP
            );
}

jdbc.update("""
        UPDATE investment_holdings
        SET quantity = ?,
            invested_amount = ?,
            current_value = ?,
            status = ?,
            closed_at = CASE WHEN ? = 'CLOSED'
                             THEN SYSTIMESTAMP
                             ELSE NULL END,
            updated_at = SYSTIMESTAMP
        WHERE holding_id = ?
        """,
        remaining,
        remainingAmount,
        remainingCurrentValue,
        state,
        state,
        col(holding, "holding_id")
);

boolean wallet = "Y".equals(
        col(holdingProduct, "requires_demat")
);

        if (wallet) {
            Map<String, Object> profile = profileRow(customer.id());

            jdbc.update("""
                    UPDATE investment_profiles
                    SET wallet_balance = wallet_balance + ?,
                        updated_at = SYSTIMESTAMP
                    WHERE user_id = ?
                    """, amount, customer.id());

            jdbc.update("""
                    INSERT INTO trading_wallet_transactions(
                        user_id,
                        profile_id,
                        linked_account_id,
                        order_id,
                        transaction_type,
                        amount,
                        status,
                        reference_number,
                        completed_at
                    )
                    VALUES (?, ?, ?, ?, 'SALE', ?, 'COMPLETED', ?, SYSTIMESTAMP)
                    """,
                    customer.id(),
                    col(profile, "profile_id"),
                    col(order, "source_account_id"),
                    orderId,
                    amount,
                    "W" + col(order, "reference_number"));

        } else {
            credit(
                    customer,
                    number(col(order, "source_account_id")),
                    amount,
                    "INVESTMENT_REDEMPTION",
                    String.valueOf(col(order, "reference_number")));
        }

        jdbc.update("""
                UPDATE investment_orders
                SET status = 'REDEEMED',
                    authorised_at = SYSTIMESTAMP,
                    otp_hash = NULL,
                    otp_expiry = NULL,
                    updated_at = SYSTIMESTAMP
                WHERE order_id = ?
                """, orderId);

        audit(
                customer.id(),
                "INVESTMENT_REDEEMED",
                "INVESTMENT_ORDER",
                String.valueOf(orderId));

        return Map.of(
                "message",
                wallet
                        ? "Sale completed and proceeds were credited to your Trading Wallet."
                        : "Redemption completed and proceeds were credited to your savings account.",
                "referenceNumber",
                col(order, "reference_number"),
                "status",
                "REDEEMED");
    }

    private Customer customer(String email) {
        IdentityClient.IdentityUserResponse user =
                identities.findUserByEmail(email);

        if (user == null
                || !"CUSTOMER".equals(user.role())
                || !"ACTIVE".equals(user.status())) {

            throw new IllegalArgumentException(
                    "Customer was not found or is inactive.");
        }

        return new Customer(user.userId(), user.email());
    }

    private void requireOwnedActiveAccount(
            Long accountId,
            Long userId) {

        if (!accounts.hasActiveOwnedAccount(accountId, userId).active()) {
            throw new IllegalArgumentException(
                    "Choose one of your active savings accounts.");
        }
    }

    private void debit(
            Customer customer,
            Long accountId,
            BigDecimal amount,
            String type,
            String reference) {

        debits.debit(
                new BankingDebitClient.AccountDebitRequest(
                        customer.id(),
                        accountId,
                        amount,
                        type,
                        reference));
    }

    private void credit(
            Customer customer,
            Long accountId,
            BigDecimal amount,
            String type,
            String reference) {

        credits.credit(
                new BankingCreditClient.AccountCreditRequest(
                        customer.id(),
                        accountId,
                        amount,
                        type,
                        reference));
    }

    private void sendOtp(
            Customer customer,
            String otp,
            String purpose) {

        otpEmails.sendOtp(
                new IdentityOtpEmailClient.OtpEmailRequest(
                        customer.email(),
                        otp,
                        purpose));
    }

    private void audit(
        Long userId,
        String action,
        String type,
        String id) {

    audits.create(
            new IdentityAuditClient.CreateAuditEventRequest(
                    userId,
                    action,
                    type,
                    id,
                    "INVESTMENT-SERVICE"
            )
    );

    // Audit remains unchanged; customer notifications are a separate side effect.
    notifyInvestmentAction(userId, action, id);
}

    private void notifyInvestmentAction(Long actorUserId, String action, String id) {
        String message = switch (action) {
            case "INVESTMENT_PAN_FORMAT_VERIFIED" ->
                    "Your investment PAN format check was completed.";
            case "DEMAT_OPENED" ->
                    "Your Demat account is now active.";
            case "TRADING_WALLET_FUND_COMPLETED" ->
                    "Your Trading Wallet funding completed. Transaction: " + id;
            case "TRADING_WALLET_WITHDRAW_COMPLETED" ->
                    "Your Trading Wallet withdrawal completed. Transaction: " + id;
            case "INVESTMENT_SCHEDULE_AUTHORISED" ->
                    "Your recurring investment instruction is active. No money was debited today. Order: " + id;
            case "INVESTMENT_SCHEDULE_CREATED" ->
                    "Your recurring investment schedule was created. Product: " + id;
            case "INVESTMENT_SCHEDULE_PAUSED" ->
                    "Your recurring investment schedule was paused. Schedule: " + id;
            case "INVESTMENT_SCHEDULE_RESUMED" ->
                    "Your recurring investment schedule was resumed. Schedule: " + id;
            case "INVESTMENT_SCHEDULE_CANCELLED" ->
                    "Your recurring investment schedule was cancelled. Schedule: " + id;
            case "INVESTMENT_DEBIT_QUEUED" ->
                    "Your investment was authorised. Your bank-account debit is being processed. Order: " + id;
            case "INVESTMENT_AUTHORISED" ->
                    "Your Trading Wallet investment purchase was authorised. Order: " + id;
            case "INVESTMENT_REDEEMED" ->
                    "Your investment redemption completed and the proceeds were credited. Order: " + id;
            case "INVESTMENT_MARKET_ORDER_SETTLED" ->
                    "Your market investment order was settled. No additional funds were moved. Order: " + id;
            default -> null;
        };

        if (message == null) {
            return; // OTP issuance remains an email/audit action, not a payment notification.
        }

        Long recipient = actorUserId;
        if ("INVESTMENT_MARKET_ORDER_SETTLED".equals(action)) {
            // The audit actor is the admin, but the notification belongs to the customer.
            recipient = jdbc.queryForObject(
                    "SELECT user_id FROM investment_orders WHERE order_id = ?",
                    Long.class, Long.valueOf(id));
        }

        investmentNotifications.afterCommit(recipient,
                Set.of("INVESTMENT_PAN_FORMAT_VERIFIED", "DEMAT_OPENED").contains(action)
                        ? "SECURITY" : "PAYMENT",
                message);
    }

    public void notifyStatementEmailSent(String email, String statementMonth) {
        Customer customer = customer(email);
        investmentNotifications.afterCommit(customer.id(), "STATEMENT",
                "Your Investment statement PDF for " + statementMonth
                        + " was sent to your registered email.");
    }

    private boolean profileExists(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM investment_profiles
                WHERE user_id = ?
                """, Integer.class, userId) > 0;
    }

    private Map<String, Object> profileRow(Long userId) {
        return jdbc.queryForMap("""
                SELECT *
                FROM investment_profiles
                WHERE user_id = ?
                """, userId);
    }


private Map<String, Object> profileRowForUpdate(Long userId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT *
            FROM investment_profiles
            WHERE user_id = ?
            FOR UPDATE
            """, userId);

    if (rows.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Investment profile was not found."
        );
    }

    return rows.get(0);
}
    private Map<String, Object> profile(Long userId) {
        Map<String, Object> output = new LinkedHashMap<>();

        if (!profileExists(userId)) {
            output.put("panStatus", "NOT_STARTED");
            output.put("dematStatus", "NOT_REQUESTED");
            output.put("walletBalance", BigDecimal.ZERO);
            return output;
        }

        Map<String, Object> profile = profileRow(userId);

        output.put("panNumber", col(profile, "pan_number"));
        output.put(
                "panStatus",
                "DEMO_VERIFIED".equals(col(profile, "kyc_status"))
                        ? "VERIFIED"
                        : col(profile, "kyc_status"));
        output.put("dematNumber", col(profile, "demat_number"));
        output.put("dematStatus", col(profile, "demat_status"));
        output.put("linkedAccountId", col(profile, "linked_account_id"));
        output.put("walletBalance", col(profile, "wallet_balance"));

        return output;
    }

    private void requireVerifiedPan(Long userId) {
        if (!"VERIFIED".equals(profile(userId).get("panStatus"))) {
            throw new IllegalArgumentException(
                    "Verify PAN format before opening a Demat account.");
        }
    }

    private Map<String, Object> product(long productId) {
        return jdbc.queryForMap("""
                SELECT *
                FROM investment_products
                WHERE product_id = ?
                  AND active = 'Y'
                """, productId);
    }

    private void verifyPendingOtp(
                Map<String, Object> row,
                String otp) {

        if (!"PENDING_OTP".equals(col(row, "status"))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This request has already been processed."
                );
        }

        Timestamp expiry = timestamp(col(row, "otp_expiry"));

        if (expiry == null
                || expiry.toLocalDateTime().isBefore(LocalDateTime.now())
                || !encoder.matches(
                        otp,
                        String.valueOf(col(row, "otp_hash")))) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The OTP is invalid or expired."
                );
        }
        }
    private void fail(long orderId, String failureCode) {
        jdbc.update("""
                UPDATE investment_orders
                SET status = 'FAILED',
                    failure_code = ?,
                    updated_at = SYSTIMESTAMP
                WHERE order_id = ?
                """, failureCode, orderId);
    }

    private List<Map<String, Object>> chartPoints(Map<String, Object> product) {
        BigDecimal end = col(product, "current_price") == null
                ? new BigDecimal("100")
                : decimal(col(product, "current_price"));

        BigDecimal annual = col(product, "past_return_1y") == null
                ? BigDecimal.ZERO
                : decimal(col(product, "past_return_1y"));

        BigDecimal start = end.divide(
                BigDecimal.ONE.add(
                        annual.divide(
                                new BigDecimal("100"),
                                8,
                                RoundingMode.HALF_UP)),
                4,
                RoundingMode.HALF_UP);

        List<Map<String, Object>> points = new ArrayList<>();
        YearMonth first = YearMonth.now().minusMonths(11);

        for (int index = 0; index < 12; index++) {
            BigDecimal price = start.add(
                    end.subtract(start).multiply(
                            BigDecimal.valueOf(
                                    index / 11.0
                                            + Math.sin(index * 1.85) * .035)))
                    .setScale(2, RoundingMode.HALF_UP);

            points.add(Map.of(
                    "label",
                    first.plusMonths(index)
                            .getMonth()
                            .toString()
                            .substring(0, 3),
                    "value",
                    price.max(new BigDecimal("0.01"))));
        }

        return points;
    }

    private Map<String, Object> productResponse(Map<String, Object> row) {
        Map<String, Object> output = new LinkedHashMap<>();

        output.put("id", col(row, "id", "product_id"));
        output.put("code", col(row, "code", "product_code"));
        output.put("name", col(row, "name", "product_name"));
        output.put("productType", col(row, "product_type"));
        output.put("requiresDemat", col(row, "requires_demat"));
        output.put("assetSize", col(row, "asset_size"));
        output.put("riskLevel", col(row, "risk_level"));
        output.put("pastReturn1y", col(row, "past_return_1y"));
        output.put("lockInMonths", col(row, "lock_in_months"));
        output.put("tenureMonths", col(row, "tenure_months"));
        output.put("minimumAmount", col(row, "minimum_amount"));
        output.put("currentPrice", col(row, "current_price"));
        output.put("annualRate", col(row, "annual_rate"));
        output.put("exchangeName", col(row, "exchange_name"));

        return output;
    }

    private Map<String, Object> holdingResponse(Map<String, Object> row) {
        Map<String, Object> response = new LinkedHashMap<>(Map.of(
                "id", col(row, "id"),
                "productName", col(row, "product_name"),
                "productType", col(row, "product_type"),
                "quantity", col(row, "quantity"),
                "investedAmount", col(row, "invested_amount"),
                "currentValue", col(row, "current_value"),
                "gainLoss", col(row, "gain_loss"),
                "status", col(row, "status")));
        response.put("tenureMonths", col(row, "tenure_months"));
        response.put("maturityDate", col(row, "maturity_date"));
        return response;
    }

    private String description(String type, String name) {
        return switch (type) {
            case "EQUITY" ->
                    name + " is a simulated direct-equity investment. "
                            + "It requires an active Demat account and carries market risk.";
            case "ETF" ->
                    name + " is a simulated exchange-traded fund. "
                            + "It requires an active Demat account and is bought in units.";
            case "MUTUAL_FUND" ->
                    name + " is a simulated mutual-fund scheme that can be "
                            + "started as a lump sum or monthly SIP.";
            case "FD" ->
                    name + " is a simulated bank fixed deposit with an "
                            + "interest rate and stated tenure.";
            case "RD" ->
                    name + " is a simulated recurring deposit with a fixed "
                            + "monthly contribution.";
            case "PPF" ->
                    name + " is a simulated government-backed long-term "
                            + "savings product with a lock-in period.";
            default ->
                    name + " is a simulated investment product.";
        };
    }

    private List<String> purchaseOptions(String type) {
        return switch (type) {
            case "MUTUAL_FUND" -> List.of("Lump sum", "Monthly SIP");
            case "RD", "PPF" -> List.of("Lump sum", "Monthly auto-debit");
            default -> List.of("Lump sum");
        };
    }

    private List<String> payoutOptions(String type) {
        return "FD".equals(type)
                ? List.of("At maturity", "Monthly interest", "Quarterly interest")
                : List.of("Growth / reinvestment");
    }

    private static Object col(
            Map<String, Object> row,
            String... keys) {

        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }

            if (row.containsKey(key.toUpperCase(Locale.ROOT))) {
                return row.get(key.toUpperCase(Locale.ROOT));
            }
        }

        return null;
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(value));
    }

    private static Long number(Object value) {
        return value instanceof Number number
                ? number.longValue()
                : Long.valueOf(String.valueOf(value));
    }

    private static Timestamp timestamp(Object value) {
        return value instanceof Timestamp timestamp
                ? timestamp
                : null;
    }

        private LocalDate firstFutureDebitDate(int debitDay) {

        LocalDate today = LocalDate.now();

        LocalDate candidate = today.withDayOfMonth(debitDay);

        return candidate.isAfter(today)
                ? candidate
                : candidate.plusMonths(1);
        }

    private static String otp() {
        return String.format(
                "%06d",
                new java.security.SecureRandom().nextInt(1_000_000));
    }

    private static String token() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private record Customer(Long id, String email) {
    }

        public String getCustomerEmail(String email) {
        return customer(email).email();
        }

@Transactional(readOnly = true)
public List<Map<String, Object>> portfolioHistory(
        String email,
        int days) {

    if (days < 1 || days > 90) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "History days must be between 1 and 90."
        );
    }

    Customer customer = customer(email);

    return jdbc.queryForList("""
            SELECT snapshot_at,
                   invested_amount,
                   current_value,
                   gain_loss
              FROM investment_portfolio_snapshots
             WHERE user_id = ?
               AND snapshot_at >=
                   SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')
             ORDER BY snapshot_at
            """,
            customer.id(),
            days
    ).stream()
            .map(row -> {
                Map<String, Object> output =
                        new LinkedHashMap<>();

                output.put(
                        "capturedAt",
                        col(row, "snapshot_at")
                );

                output.put(
                        "investedAmount",
                        col(row, "invested_amount")
                );

                output.put(
                        "currentValue",
                        col(row, "current_value")
                );

                output.put(
                        "gainLoss",
                        col(row, "gain_loss")
                );

                return output;
            })
            .toList();
}

    
}

