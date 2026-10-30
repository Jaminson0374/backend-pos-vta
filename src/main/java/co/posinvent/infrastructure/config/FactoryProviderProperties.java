package co.posinvent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for The Factory HKA DIAN provider ({@code app.dian.thefactory}).
 *
 * <p>The sandbox is a SOAP 1.1 service. Credentials have no inline defaults on purpose:
 * they must come from environment variables. Non-secret routing values fall back to the
 * verified sandbox contract so the adapter can boot without credentials and fail only when
 * an invoice is actually sent.</p>
 *
 * <p>{@link #toString()} masks both tokens because Spring may log bound properties.</p>
 */
@ConfigurationProperties(prefix = "app.dian.thefactory")
public record FactoryProviderProperties(
        String tokenEmpresa,
        String tokenPassword,
        @DefaultValue("https://demoemision21.thefactoryhka.com.co/ws/v1.0/Service.svc")
        String baseUrl,
        @DefaultValue("true")
        boolean testEnv,
        @DefaultValue("5000")
        int connectTimeoutMs,
        @DefaultValue("15000")
        int readTimeoutMs,
        /** HKA registered numbering range, for example {@code FV-1}. Required to send. */
        String numberingRange,
        @DefaultValue("01")
        String documentType,
        @DefaultValue("05")
        String operationType,
        @DefaultValue("COP")
        String currency,
        @DefaultValue("01")
        String taxCodeIva,
        @DefaultValue("04")
        String taxCodeInc,
        @DefaultValue("UN")
        String unitMeasure,
        @DefaultValue("1")
        String paymentMethod,
        @DefaultValue("00")
        String discountCode,
        @DefaultValue("222222222222")
        String finalConsumerIdentification,
        @DefaultValue("13")
        String finalConsumerIdentificationType,
        @DefaultValue("Consumidor final")
        String finalConsumerName
) {

    @Override
    public String toString() {
        return "FactoryProviderProperties["
                + "baseUrl=" + baseUrl
                + ", testEnv=" + testEnv
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", numberingRange=" + numberingRange
                + ", documentType=" + documentType
                + ", operationType=" + operationType
                + ", currency=" + currency
                + ", taxCodeIva=" + taxCodeIva
                + ", taxCodeInc=" + taxCodeInc
                + ", unitMeasure=" + unitMeasure
                + ", paymentMethod=" + paymentMethod
                + ", discountCode=" + discountCode
                + ", tokenEmpresa=***, tokenPassword=***]";
    }
}
