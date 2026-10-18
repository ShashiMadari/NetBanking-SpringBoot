package com.inetbank.banking.soap.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs
@Configuration
public class BankingSoapConfig {

    public static final String NAMESPACE =
            "http://inetbank.com/soap/credit-repayment";

    @Bean
    ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {

        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);

        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "creditRepaymentDebit")
    DefaultWsdl11Definition creditRepaymentDebitWsdl(
            XsdSchema creditRepaymentDebitSchema) {

        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("CreditRepaymentDebitPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(creditRepaymentDebitSchema);

        return wsdl;
    }

    @Bean
    XsdSchema creditRepaymentDebitSchema() {
        return new SimpleXsdSchema(
                new ClassPathResource(
                        "soap/credit-repayment-debit.xsd"
                )
        );
    }
}