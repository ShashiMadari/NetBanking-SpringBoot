package com.inetbank.banking.billpayment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "billers")
public class Biller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biller_id")
    private Long id;

    @Column(name = "biller_name", nullable = false, length = 150)
    private String billerName;

    @Column(name = "biller_category", nullable = false, length = 20)
    private String billerCategory;

    @Column(name = "consumer_number_label", nullable = false, length = 80)
    private String consumerNumberLabel;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Biller() {
    }

    public Long getId() { return id; }
    public String getBillerName() { return billerName; }
    public String getBillerCategory() { return billerCategory; }
    public String getConsumerNumberLabel() { return consumerNumberLabel; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}