package com.inetbank.banking.billpayment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillerService {

    private final BillerRepository billers;

    BillerService(BillerRepository billers) {
        this.billers = billers;
    }

    @Transactional(readOnly = true)
    public List<BillerResponse> activeBillers() {
        return billers.findByStatusOrderByBillerNameAsc("ACTIVE")
                .stream()
                .map(BillerResponse::from)
                .toList();
    }
}