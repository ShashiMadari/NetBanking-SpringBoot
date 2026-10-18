package com.inetbank.banking.fundtransfer;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository
        extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByIdAndSourceAccountId(
            Long paymentOrderId,
            Long sourceAccountId);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);
}