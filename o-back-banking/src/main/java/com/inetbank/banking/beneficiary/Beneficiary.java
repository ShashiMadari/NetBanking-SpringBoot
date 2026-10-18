package com.inetbank.banking.beneficiary;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "beneficiary_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "beneficiary_name", nullable = false, length = 150)
    private String beneficiaryName;

    @Column(name = "beneficiary_account_number", nullable = false, length = 34)
    private String beneficiaryAccountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 20)
    private String ifscCode;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Beneficiary() {
    }

    public Beneficiary(Long userId, String beneficiaryName,
                       String beneficiaryAccountNumber, String ifscCode,
                       String bankName) {
        this.userId = userId;
        this.beneficiaryName = beneficiaryName;
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
        this.ifscCode = ifscCode;
        this.bankName = bankName;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getBeneficiaryAccountNumber() { return beneficiaryAccountNumber; }
    public String getIfscCode() { return ifscCode; }
    public String getBankName() { return bankName; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

        public void approve() {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException(
                    "Only pending beneficiaries can be approved.");
        }
        status = "ACTIVE";
    }

    public void reject() {
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException(
                    "Only pending beneficiaries can be rejected.");
        }
        status = "BLOCKED";
    }

    public void block() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException(
                    "Only active beneficiaries can be blocked.");
        }
        status = "BLOCKED";
    }
}