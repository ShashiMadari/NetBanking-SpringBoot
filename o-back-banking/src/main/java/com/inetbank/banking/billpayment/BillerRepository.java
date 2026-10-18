package com.inetbank.banking.billpayment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillerRepository extends JpaRepository<Biller, Long> {

    List<Biller> findByStatusOrderByBillerNameAsc(String status);
}