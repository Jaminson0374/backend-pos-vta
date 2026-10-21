package co.posinvent.application.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.DianPaymentMeans;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.SaleItem;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.DianResolutionRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalePaymentRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the real {@link ElectronicInvoiceRequest} payload from persisted sales data.
 *
 * <p>The {@link #build} method is read-only transactional on purpose: the sales document adapter
 * maps the lazy {@code items} collection, so reading a document outside an open session would throw
 * a {@code LazyInitializationException}. The caller (the send job) invokes the HTTP provider OUTSIDE
 * this transaction, once the payload has been materialized.
 */
@Service
public class ElectronicInvoicePayloadService {

    /** DIAN fiscal responsibility code for "Regimen Simple de Tributacion". */
    private static final String SIMPLE_REGIME_FISCAL_CODE = "O-47";
    private static final String SIMPLE_REGIME = "SIMPLE";
    private static final String ORDINARY_REGIME = "ORDINARIO";

    private final SalesDocumentRepository salesDocumentRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ProductRepository productRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final DianResolutionRepository dianResolutionRepository;
    private final SalePaymentRepository salePaymentRepository;

    public ElectronicInvoicePayloadService(
            SalesDocumentRepository salesDocumentRepository,
            ThirdPartyRepository thirdPartyRepository,
            ProductRepository productRepository,
            CompanyConfigRepository companyConfigRepository,
            DianResolutionRepository dianResolutionRepository,
            SalePaymentRepository salePaymentRepository
    ) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.productRepository = productRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.dianResolutionRepository = dianResolutionRepository;
        this.salePaymentRepository = salePaymentRepository;
    }

    @Transactional(readOnly = true)
    public ElectronicInvoiceRequest build(UUID salesDocumentId, String documentNumber) {
        var document = salesDocumentRepository.findById(salesDocumentId).orElse(null);
        var client = document != null ? resolveClient(document.clientId()) : null;

        return new ElectronicInvoiceRequest(
                resolveDocumentNumber(documentNumber, document),
                resolveIssueDate(document),
                client != null ? client.numIdentification() : null,
                client != null ? client.name() : null,
                resolveItems(document),
                resolveTaxTotals(document),
                document != null ? document.totalAmount() : null,
                resolveCompanyInfo(),
                document != null ? document.discountTotal() : null,
                client != null ? toClientInfo(client) : null,
                resolvePayments(document),
                resolveCurrency(),
                document != null ? "Venta POS " + document.documentNumber() : null
        );
    }

    /**
     * Uses the provided invoice number when present; otherwise falls back to the document's own
     * number (the queue re-send path does not carry it).
     */
    private String resolveDocumentNumber(String documentNumber, SalesDocument document) {
        if (documentNumber != null && !documentNumber.isBlank()) {
            return documentNumber;
        }
        return document != null ? document.documentNumber() : null;
    }

    private ThirdParty resolveClient(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        return thirdPartyRepository.findById(clientId).orElse(null);
    }

    private String resolveIssueDate(SalesDocument document) {
        if (document != null && document.createdAt() != null) {
            return document.createdAt().toLocalDate().toString();
        }
        return LocalDate.now().toString();
    }

    private List<ElectronicInvoiceRequest.InvoiceItem> resolveItems(SalesDocument document) {
        if (document == null || document.items() == null) {
            return List.of();
        }
        var items = new ArrayList<ElectronicInvoiceRequest.InvoiceItem>(document.items().size());
        for (var saleItem : document.items()) {
            items.add(toInvoiceItem(saleItem));
        }
        return items;
    }

    private ElectronicInvoiceRequest.InvoiceItem toInvoiceItem(SaleItem saleItem) {
        var product = saleItem.productId() != null
                ? productRepository.findById(saleItem.productId()).orElse(null)
                : null;
        // Fall back to the raw product id when the product record is missing, never to fake data.
        var fallback = saleItem.productId() != null ? saleItem.productId().toString() : null;
        return new ElectronicInvoiceRequest.InvoiceItem(
                product != null ? product.productCode() : fallback,
                product != null ? product.name() : fallback,
                saleItem.quantity(),
                saleItem.unitPrice(),
                saleItem.taxType(),
                saleItem.taxRate(),
                saleItem.taxAmount(),
                saleItem.subtotal(),
                saleItem.subtotal().subtract(saleItem.netAmount())
        );
    }

    private ElectronicInvoiceRequest.ClientInfo toClientInfo(ThirdParty client) {
        var personType = client.personType() == ThirdParty.PersonType.JURIDICA
                ? "Company"
                : "Person";
        return new ElectronicInvoiceRequest.ClientInfo(
                client.dianIdentificationTypeCode(),
                client.numIdentification(),
                client.dv(),
                personType,
                client.registrationName() != null ? client.registrationName() : client.name(),
                client.lastName(),
                client.address(),
                client.cityName(),
                client.municipalityCode(),
                client.departmentCode(),
                client.departmentName(),
                client.postalCode(),
                client.countryCode(),
                client.dianTaxLevelCode(),
                client.email(),
                client.phone()
        );
    }

    /**
     * Siigo requires the sum of payments to equal the invoice total. POS tenders may exceed the
     * total because they include change, so we cap each line at the remaining amount.
     */
    private List<ElectronicInvoiceRequest.Payment> resolvePayments(SalesDocument document) {
        if (document == null || document.id() == null || document.totalAmount() == null
                || document.totalAmount().signum() <= 0) {
            return List.of();
        }
        var persisted = salePaymentRepository.findBySalesDocumentId(document.id());
        if (persisted.isEmpty()) {
            throw new BusinessException("DIAN_PAYMENT_TOTAL_MISMATCH",
                    "La factura no tiene medios de pago persistidos para enviar a Siigo.");
        }
        var remaining = document.totalAmount();
        var result = new ArrayList<ElectronicInvoiceRequest.Payment>();
        for (var payment : persisted) {
            if (remaining.signum() <= 0) break;
            var amount = payment.amount() != null ? payment.amount() : BigDecimal.ZERO;
            if (amount.signum() <= 0) continue;
            var allocated = amount.min(remaining).setScale(2, RoundingMode.HALF_UP);
            var means = DianPaymentMeans.fromInternal(payment.method());
            result.add(new ElectronicInvoiceRequest.Payment(
                    payment.method(), means.code(), allocated, resolveIssueDate(document)));
            remaining = remaining.subtract(allocated);
        }
        if (remaining.signum() > 0) {
            throw new BusinessException("DIAN_PAYMENT_TOTAL_MISMATCH",
                    "La suma de pagos no cubre el total de la factura. Faltan " + remaining);
        }
        return result;
    }

    private String resolveCurrency() {
        return companyConfigRepository.findConfig()
                .map(CompanyConfig::currency)
                .orElse("COP");
    }

    /**
     * Maps the document totals by DIAN tax rate. Null or zero totals are omitted so the payload
     * only carries taxes that actually apply; the insertion order stays 0, 5, 19.
     */
    private Map<String, BigDecimal> resolveTaxTotals(SalesDocument document) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        if (document == null) {
            return totals;
        }
        var summary = document.taxSummary();
        for (var subtotal : summary.subtotals()) {
            var rate = subtotal.rate().stripTrailingZeros().toPlainString();
            putIfApplicable(totals, rate, subtotal.taxAmount());
        }
        return totals;
    }

    private void putIfApplicable(Map<String, BigDecimal> totals, String rate, BigDecimal amount) {
        if (amount != null && amount.signum() != 0) {
            totals.put(rate, amount);
        }
    }

    private ElectronicInvoiceRequest.CompanyInfo resolveCompanyInfo() {
        var config = companyConfigRepository.findConfig().orElse(null);
        if (config == null) {
            return null;
        }
        var resolution = dianResolutionRepository.findActive().orElse(null);
        var softwarePin = resolution != null && resolution.softwarePin() != null
                ? resolution.softwarePin()
                : config.softwarePin();
        return new ElectronicInvoiceRequest.CompanyInfo(
                config.nit(),
                config.companyName(),
                config.address(),
                config.phone(),
                config.email(),
                config.economicActivity(),
                resolveTaxRegime(config),
                resolution != null ? resolution.resolutionNumber() : null,
                softwarePin
        );
    }

    /**
     * Derives the issuer tax regime from the DIAN responsibility codes. Any code list (fiscal first,
     * then tax) containing {@code O-47} ("Regimen Simple de Tributacion") yields {@code SIMPLE};
     * otherwise {@code ORDINARIO}.
     */
    private String resolveTaxRegime(CompanyConfig config) {
        if (containsCode(config.fiscalResponsibilityCodes(), SIMPLE_REGIME_FISCAL_CODE)
                || containsCode(config.taxResponsibilityCodes(), SIMPLE_REGIME_FISCAL_CODE)) {
            return SIMPLE_REGIME;
        }
        return ORDINARY_REGIME;
    }

    private boolean containsCode(List<String> codes, String code) {
        return codes != null && codes.contains(code);
    }
}
