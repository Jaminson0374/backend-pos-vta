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
    BigDecimal discountTotal
) {
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
}
