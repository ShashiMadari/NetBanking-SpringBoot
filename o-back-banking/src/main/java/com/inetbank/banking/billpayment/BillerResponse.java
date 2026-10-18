package com.inetbank.banking.billpayment;

public record BillerResponse(
        Long id,
        String billerName,
        String billerCategory,
        String consumerNumberLabel,
        String status
) {
    static BillerResponse from(Biller biller) {
        return new BillerResponse(
                biller.getId(),
                biller.getBillerName(),
                biller.getBillerCategory(),
                biller.getConsumerNumberLabel(),
                biller.getStatus()
        );
    }
}