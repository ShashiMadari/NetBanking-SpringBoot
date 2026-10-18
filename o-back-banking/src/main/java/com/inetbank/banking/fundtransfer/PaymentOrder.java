package com.inetbank.banking.fundtransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_order_id")
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_number", nullable = false, length = 40)
    private String referenceNumber;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "otp_hash", length = 255)
    private String otpHash;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PaymentOrder() {
    }

    public Long getId() { return id; }
    public Long getSourceAccountId() { return sourceAccountId; }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public String getPaymentType() { return paymentType; }
    public BigDecimal getAmount() { return amount; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}