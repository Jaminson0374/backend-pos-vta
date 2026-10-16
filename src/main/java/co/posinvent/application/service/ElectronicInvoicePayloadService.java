package co.posinvent.application.service;

import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.SaleItem;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.DianResolutionRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    /** Tax rate keys used by the sales document totals, kept in deterministic order. */
    private static final String TAX_RATE_0 = "0";
    private static final String TAX_RATE_5 = "5";
    private static final String TAX_RATE_19 = "19";

    /** DIAN fiscal responsibility code for "Regimen Simple de Tributacion". */
    private static final String SIMPLE_REGIME_FISCAL_CODE = "O-47";
    private static final String SIMPLE_REGIME = "SIMPLE";
    private static final String ORDINARY_REGIME = "ORDINARIO";

    private final SalesDocumentRepository salesDocumentRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ProductRepository productRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final DianResolutionRepository dianResolutionRepository;

    public ElectronicInvoicePayloadService(
            SalesDocumentRepository salesDocumentRepository,
            ThirdPartyRepository thirdPartyRepository,
            ProductRepository productRepository,
            CompanyConfigRepository companyConfigRepository,
            DianResolutionRepository dianResolutionRepository
    ) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.productRepository = productRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.dianResolutionRepository = dianResolutionRepository;
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
                document != null ? document.discountTotal() : null
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
                saleItem.discountAmount()
        );
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
        putIfApplicable(totals, TAX_RATE_0, document.totalTax0());
        putIfApplicable(totals, TAX_RATE_5, document.totalTax5());
        putIfApplicable(totals, TAX_RATE_19, document.totalTax19());
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
