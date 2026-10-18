package com.inetbank.banking.beneficiary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndBeneficiaryAccountNumberAndIfscCode(
            Long userId,
            String beneficiaryAccountNumber,
            String ifscCode);

    List<Beneficiary> findByStatusOrderByCreatedAtAsc(String status);
}