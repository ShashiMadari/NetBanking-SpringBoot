package com.example.inetbank.credit.integration;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;

import com.example.inetbank.credit.soap.creditrepayment.CreditRepaymentDebitSoapRequest;
import com.example.inetbank.credit.soap.creditrepayment.CreditRepaymentDebitSoapResponse;
import com.example.inetbank.credit.soap.creditrepayment.InternalApiKeySoapHeader;

@Service
public class BankingCreditRepaymentClient {

    private static final String BANKING_SERVICE_ID = "O-BANK-BANKING";

    private final WebServiceTemplate webServiceTemplate;
    private final Jaxb2Marshaller creditRepaymentSoapMarshaller;
    private final LoadBalancerClient loadBalancerClient;
    private final String bankingApiKey;

    public BankingCreditRepaymentClient(
            WebServiceTemplate creditRepaymentWebServiceTemplate,
            Jaxb2Marshaller creditRepaymentSoapMarshaller,
            LoadBalancerClient loadBalancerClient,
            @Value("${app.internal.banking-api-key}")
            String bankingApiKey) {

        this.webServiceTemplate = creditRepaymentWebServiceTemplate;
        this.creditRepaymentSoapMarshaller =
                creditRepaymentSoapMarshaller;
        this.loadBalancerClient = loadBalancerClient;
        this.bankingApiKey = bankingApiKey;
    }

    public BankingCreditRepaymentDebitResponse debit(
            Long userId,
            Long sourceAccountId,
            BigDecimal amount,
            String referenceNumber) {

        ServiceInstance bankingInstance =
                loadBalancerClient.choose(BANKING_SERVICE_ID);

        if (bankingInstance == null) {
            throw new IllegalStateException(
                    "O-BANK-BANKING is not available in Eureka."
            );
        }

        String endpointUri =
                bankingInstance.getUri().toString() + "/ws";

        CreditRepaymentDebitSoapRequest request =
                new CreditRepaymentDebitSoapRequest();

        request.setUserId(userId);
        request.setSourceAccountId(sourceAccountId);
        request.setAmount(amount);
        request.setReferenceNumber(referenceNumber);

        CreditRepaymentDebitSoapResponse response =
                (CreditRepaymentDebitSoapResponse)
                        webServiceTemplate.marshalSendAndReceive(
                                endpointUri,
                                request,
                                message -> {
                                    SoapMessage soapMessage =
                                            (SoapMessage) message;

                                    creditRepaymentSoapMarshaller.marshal(
                                            new InternalApiKeySoapHeader(
                                                    bankingApiKey
                                            ),
                                            soapMessage.getSoapHeader()
                                                    .getResult()
                                    );
                                }
                        );

        if (response == null) {
            throw new IllegalStateException(
                    "Banking SOAP service returned no response."
            );
        }

        return new BankingCreditRepaymentDebitResponse(
                response.getTransactionId(),
                response.getReferenceNumber(),
                response.getAmount(),
                response.isAlreadyProcessed(),
                response.getMessage()
        );
    }
}