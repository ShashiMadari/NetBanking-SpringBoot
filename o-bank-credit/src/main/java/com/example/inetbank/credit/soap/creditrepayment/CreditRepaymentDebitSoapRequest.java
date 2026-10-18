package com.example.inetbank.credit.soap.creditrepayment;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "CreditRepaymentDebitRequest",
        propOrder = {
                "userId",
                "sourceAccountId",
                "amount",
                "referenceNumber"
        }
)
@XmlRootElement(
        name = "CreditRepaymentDebitRequest",
        namespace = "http://inetbank.com/soap/credit-repayment"
)
public class CreditRepaymentDebitSoapRequest {

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private Long userId;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private Long sourceAccountId;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private BigDecimal amount;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private String referenceNumber;

    public CreditRepaymentDebitSoapRequest() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }
}