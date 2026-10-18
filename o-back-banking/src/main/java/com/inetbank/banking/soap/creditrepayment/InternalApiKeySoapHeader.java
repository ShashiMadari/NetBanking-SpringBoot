package com.inetbank.banking.soap.creditrepayment;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(
        name = "InternalApiKey",
        namespace = "http://inetbank.com/soap/credit-repayment"
)
public class InternalApiKeySoapHeader {

    @XmlElement(
            namespace = "http://inetbank.com/soap/credit-repayment",
            required = true
    )
    private String apiKey;

    public InternalApiKeySoapHeader() {
    }

    public InternalApiKeySoapHeader(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}