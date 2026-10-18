package com.example.inetbank.credit.soap.creditrepayment;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(
        name = "InternalApiKey",
        namespace = "http://inetbank.com/soap/credit-repayment"
)
public class InternalApiKeySoapHeader {

    @XmlValue
    private String value;

    public InternalApiKeySoapHeader() {
    }

    public InternalApiKeySoapHeader(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}