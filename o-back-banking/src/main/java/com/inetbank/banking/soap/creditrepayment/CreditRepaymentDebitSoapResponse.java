package com.inetbank.banking.soap.creditrepayment;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "CreditRepaymentDebitResponse",
        propOrder = {
                "transactionId",
                "referenceNumber",
                "amount",
                "alreadyProcessed",
                "message"
        }
)
@XmlRootElement(
        name = "CreditRepaymentDebitResponse",
        namespace = "http://inetbank.com/soap/credit-repayment"
)
public class CreditRepaymentDebitSoapResponse {

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private Long transactionId;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private String referenceNumber;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private BigDecimal amount;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private boolean alreadyProcessed;

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private String message;

    public CreditRepaymentDebitSoapResponse() {
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isAlreadyProcessed() {
        return alreadyProcessed;
    }

    public void setAlreadyProcessed(boolean alreadyProcessed) {
        this.alreadyProcessed = alreadyProcessed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}