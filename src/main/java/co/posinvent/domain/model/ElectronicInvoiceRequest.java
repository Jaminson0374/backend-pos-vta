package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ElectronicInvoiceRequest(
    String documentNumber,
    String issueDate,
    String clientNit,
    String clientName,
    List<InvoiceItem> items,
    Map<String, BigDecimal> taxTotals,
    BigDecimal totalAmount,
    CompanyInfo companyInfo,
    BigDecimal discountTotal,
    ClientInfo client,
    List<Payment> payments,
    String currency,
    String observations
) {
    public ElectronicInvoiceRequest {
        if (payments == null) payments = List.of();
    }

    /** Backwards-compatible constructor for callers that only need the original core fields. */
    public ElectronicInvoiceRequest(
            String documentNumber,
            String issueDate,
            String clientNit,
            String clientName,
            List<InvoiceItem> items,
            Map<String, BigDecimal> taxTotals,
            BigDecimal totalAmount,
            CompanyInfo companyInfo,
            BigDecimal discountTotal
    ) {
        this(documentNumber, issueDate, clientNit, clientName, items, taxTotals, totalAmount,
                companyInfo, discountTotal, null, List.of(), null, null);
    }
    public record InvoiceItem(
        String productCode,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String taxType,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal subtotal,
        BigDecimal discountAmount
    ) {}

    public record CompanyInfo(
        String nit,
        String name,
        String address,
        String phone,
        String email,
        String economicActivity,
        String taxRegime,
        String resolutionNumber,
        String softwarePin
    ) {}

    /** Fiscal identity of the acquirer as required by Siigo's invoice customer object. */
    public record ClientInfo(
        String identificationTypeCode,
        String identification,
        String dv,
        String personType,
        String name,
        String lastName,
        String address,
        String cityName,
        String municipalityCode,
        String departmentCode,
        String departmentName,
        String postalCode,
        String countryCode,
        String taxLevelCode,
        String email,
        String phone
    ) {}

    /** One tender line. `dianCode` is metadata; Siigo receives its own payment type id. */
    public record Payment(
        String method,
        String dianCode,
        BigDecimal amount,
        String dueDate
    ) {}
}
