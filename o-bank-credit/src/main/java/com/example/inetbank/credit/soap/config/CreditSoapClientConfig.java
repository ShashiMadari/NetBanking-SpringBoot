package com.example.inetbank.credit.soap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

import com.example.inetbank.credit.soap.creditrepayment.CreditRepaymentDebitSoapRequest;
import com.example.inetbank.credit.soap.creditrepayment.CreditRepaymentDebitSoapResponse;
import com.example.inetbank.credit.soap.creditrepayment.InternalApiKeySoapHeader;

@Configuration
public class CreditSoapClientConfig {

    @Bean
    Jaxb2Marshaller creditRepaymentSoapMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

        marshaller.setClassesToBeBound(
                CreditRepaymentDebitSoapRequest.class,
                CreditRepaymentDebitSoapResponse.class,
                InternalApiKeySoapHeader.class
        );

        return marshaller;
    }

    @Bean
    WebServiceTemplate creditRepaymentWebServiceTemplate(
            Jaxb2Marshaller creditRepaymentSoapMarshaller) {

        WebServiceTemplate template = new WebServiceTemplate();

        template.setMarshaller(creditRepaymentSoapMarshaller);
        template.setUnmarshaller(creditRepaymentSoapMarshaller);

        return template;
    }
}