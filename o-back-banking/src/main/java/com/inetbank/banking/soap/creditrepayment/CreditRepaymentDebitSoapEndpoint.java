package com.inetbank.banking.soap.creditrepayment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.server.endpoint.annotation.SoapHeader;

import com.inetbank.banking.internalpayment.CreditRepaymentDebitRequest;
import com.inetbank.banking.internalpayment.CreditRepaymentDebitResponse;
import com.inetbank.banking.internalpayment.CreditRepaymentDebitService;

@Endpoint
public class CreditRepaymentDebitSoapEndpoint {

    private static final String NAMESPACE_URI =
            "http://inetbank.com/soap/credit-repayment";

    private final CreditRepaymentDebitService debitService;
    private final String internalApiKey;

    public CreditRepaymentDebitSoapEndpoint(
            CreditRepaymentDebitService debitService,
            @Value("${app.internal.banking-api-key}")
            String internalApiKey) {

        this.debitService = debitService;
        this.internalApiKey = internalApiKey;
    }

    @PayloadRoot(
            namespace = NAMESPACE_URI,
            localPart = "CreditRepaymentDebitRequest"
    )
    @ResponsePayload
    public CreditRepaymentDebitSoapResponse debit(
            @RequestPayload CreditRepaymentDebitSoapRequest request,
            @SoapHeader(
                    "{" + NAMESPACE_URI + "}InternalApiKey"
            )
            SoapHeaderElement apiKeyHeader) {

        String providedApiKey = apiKeyHeader == null
        ? null
        : apiKeyHeader.getText();

        if (providedApiKey == null
                || providedApiKey.isBlank()
                || !MessageDigest.isEqual(
                        internalApiKey.getBytes(StandardCharsets.UTF_8),
                        providedApiKey.getBytes(StandardCharsets.UTF_8))) {

        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid internal API key."
        );
        }

        if (!MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key."
            );
        }

        CreditRepaymentDebitResponse result = debitService.debit(
                new CreditRepaymentDebitRequest(
                        request.getUserId(),
                        request.getSourceAccountId(),
                        request.getAmount(),
                        request.getReferenceNumber()
                )
        );

        CreditRepaymentDebitSoapResponse response =
                new CreditRepaymentDebitSoapResponse();

        response.setTransactionId(result.transactionId());
        response.setReferenceNumber(result.referenceNumber());
        response.setAmount(result.amount());
        response.setAlreadyProcessed(result.alreadyProcessed());
        response.setMessage(result.message());

        return response;
    }
}