package co.posinvent.application.service;

import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.SaleItem;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.DianResolutionRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectronicInvoicePayloadServiceTest {

    @Mock private SalesDocumentRepository salesDocumentRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CompanyConfigRepository companyConfigRepository;
    @Mock private DianResolutionRepository dianResolutionRepository;

    private ElectronicInvoicePayloadService service;

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID MISSING_PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ElectronicInvoicePayloadService(
                salesDocumentRepository,
                thirdPartyRepository,
                productRepository,
                companyConfigRepository,
                dianResolutionRepository);
    }

    @Test
    void buildsRequestFromPersistedSalesData() {
        when(salesDocumentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(document(List.of(saleItem(PRODUCT_ID)))));
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("P-001", "Producto Uno")));
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-13"), List.of())));
        when(dianResolutionRepository.findActive())
                .thenReturn(Optional.of(activeResolution("RES-2026", "PIN-RES")));

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.documentNumber()).isEqualTo("FV-001");
        assertThat(request.issueDate()).isEqualTo("2026-10-01");
        assertThat(request.clientNit()).isEqualTo("900123456");
        assertThat(request.clientName()).isEqualTo("Cliente Test");
        assertThat(request.totalAmount()).isEqualByComparingTo("120500");

        assertThat(request.items()).hasSize(1);
        var item = request.items().get(0);
        assertThat(item.productCode()).isEqualTo("P-001");
        assertThat(item.productName()).isEqualTo("Producto Uno");
        assertThat(item.quantity()).isEqualByComparingTo("2");
        assertThat(item.unitPrice()).isEqualByComparingTo("50000");
        assertThat(item.taxType()).isEqualTo("IVA_19");
        assertThat(item.taxRate()).isEqualByComparingTo("19");
        assertThat(item.taxAmount()).isEqualByComparingTo("19000");
        assertThat(item.subtotal()).isEqualByComparingTo("100000");

        // Only non-null, non-zero totals are exposed, keyed by rate.
        assertThat(request.taxTotals()).containsOnlyKeys("0", "19");
        assertThat(request.taxTotals().get("0")).isEqualByComparingTo("1000");
        assertThat(request.taxTotals().get("19")).isEqualByComparingTo("19000");

        var company = request.companyInfo();
        assertThat(company.nit()).isEqualTo("900111222");
        assertThat(company.name()).isEqualTo("Mi Empresa");
        assertThat(company.address()).isEqualTo("Calle 1 # 2-3");
        assertThat(company.phone()).isEqualTo("3001234567");
        assertThat(company.email()).isEqualTo("empresa@test.co");
        assertThat(company.economicActivity()).isEqualTo("4711");
        assertThat(company.taxRegime()).isEqualTo("ORDINARIO");
        assertThat(company.resolutionNumber()).isEqualTo("RES-2026");
        assertThat(company.softwarePin()).isEqualTo("PIN-RES");
    }

    @Test
    void fallsBackToProductIdWhenProductMissing() {
        when(salesDocumentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(document(List.of(saleItem(MISSING_PRODUCT_ID)))));
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(productRepository.findById(MISSING_PRODUCT_ID)).thenReturn(Optional.empty());
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-13"), List.of())));
        when(dianResolutionRepository.findActive())
                .thenReturn(Optional.of(activeResolution("RES-2026", "PIN-RES")));

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.items()).hasSize(1);
        var item = request.items().get(0);
        assertThat(item.productCode()).isEqualTo(MISSING_PRODUCT_ID.toString());
        assertThat(item.productName()).isEqualTo(MISSING_PRODUCT_ID.toString());
    }

    @Test
    void fallsBackToCompanyConfigSoftwarePinWhenNoActiveResolution() {
        when(salesDocumentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(document(List.of())));
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-13"), List.of())));
        when(dianResolutionRepository.findActive()).thenReturn(Optional.empty());

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.companyInfo().softwarePin()).isEqualTo("PIN-CONFIG");
        assertThat(request.companyInfo().resolutionNumber()).isNull();
    }

    @Test
    void derivesSimpleTaxRegimeFromFiscalResponsibilityCode() {
        when(salesDocumentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(document(List.of())));
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-47"), List.of())));
        when(dianResolutionRepository.findActive()).thenReturn(Optional.empty());

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.companyInfo().taxRegime()).isEqualTo("SIMPLE");
    }

    @Test
    void handlesMissingSalesDocumentWithoutInventingValues() {
        when(salesDocumentRepository.findById(DOC_ID)).thenReturn(Optional.empty());
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-13"), List.of())));
        when(dianResolutionRepository.findActive()).thenReturn(Optional.empty());

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.documentNumber()).isEqualTo("FV-001");
        assertThat(request.issueDate()).isEqualTo(LocalDate.now().toString());
        assertThat(request.clientNit()).isNull();
        assertThat(request.clientName()).isNull();
        assertThat(request.items()).isEmpty();
        assertThat(request.taxTotals()).isEmpty();
        assertThat(request.totalAmount()).isNull();
    }

    @Test
    void propagatesLineAndDocumentDiscountsToTheRequest() {
        when(salesDocumentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(discountedDocument(List.of(discountedSaleItem(PRODUCT_ID)))));
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product("P-001", "Producto Uno")));
        when(companyConfigRepository.findConfig())
                .thenReturn(Optional.of(companyConfig(List.of("O-13"), List.of())));
        when(dianResolutionRepository.findActive())
                .thenReturn(Optional.of(activeResolution("RES-2026", "PIN-RES")));

        var request = service.build(DOC_ID, "FV-001");

        assertThat(request.items()).hasSize(1);
        assertThat(request.items().get(0).discountAmount()).isEqualByComparingTo("5000");
        assertThat(request.discountTotal()).isEqualByComparingTo("5000");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SaleItem saleItem(UUID productId) {
        return new SaleItem(
                UUID.randomUUID(), DOC_ID, productId,
                new BigDecimal("2"), new BigDecimal("50000"),
                "IVA_19", new BigDecimal("19"),
                new BigDecimal("19000"), new BigDecimal("100000"),
                1, null);
    }

    private SaleItem discountedSaleItem(UUID productId) {
        return new SaleItem(
                UUID.randomUUID(), DOC_ID, productId,
                new BigDecimal("2"), new BigDecimal("50000"),
                "IVA_19", new BigDecimal("19"),
                new BigDecimal("19000"), new BigDecimal("100000"),
                1, null,
                SaleItem.DISCOUNT_AMOUNT, new BigDecimal("5000"), new BigDecimal("5000"), null);
    }

    private SalesDocument document(List<SaleItem> items) {
        return new SalesDocument(
                DOC_ID, SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED,
                "FV-001", CLIENT_ID, null, null, null, null,
                new BigDecimal("100000"), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("19000"), new BigDecimal("120500"),
                null,
                OffsetDateTime.parse("2026-10-01T10:15:30-05:00"),
                OffsetDateTime.parse("2026-10-01T10:15:30-05:00"),
                items, null, false, null);
    }

    private SalesDocument discountedDocument(List<SaleItem> items) {
        return new SalesDocument(
                DOC_ID, SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED,
                "FV-001", CLIENT_ID, null, null, null, null,
                new BigDecimal("95000"), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("18050"), new BigDecimal("114050"),
                null,
                OffsetDateTime.parse("2026-10-01T10:15:30-05:00"),
                OffsetDateTime.parse("2026-10-01T10:15:30-05:00"),
                items, null, false, null,
                new BigDecimal("5000"), new BigDecimal("5000"), SalesDocument.DISCOUNT_NONE,
                BigDecimal.ZERO, null, null);
    }

    private ThirdParty client() {
        return new ThirdParty(
                CLIENT_ID, "900123456", "Cliente Test", ThirdParty.ThirdPartyType.CLIENT,
                null, BigDecimal.ZERO, BigDecimal.ZERO,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }

    private Product product(String code, String name) {
        return new Product(
                PRODUCT_ID, code, name, null, null, null,
                null, null, null, null, null, null, null,
                new BigDecimal("40000"), new BigDecimal("25"), "IVA_19", new BigDecimal("50000"),
                "PROMEDIO_PONDERADO", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, false, false, false, false, false, true,
                null, null, null, null, null, null, null,
                true, 0, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private CompanyConfig companyConfig(List<String> fiscalCodes, List<String> taxCodes) {
        return new CompanyConfig(
                1L, "Mi Empresa", "900111222", "Calle 1 # 2-3", "3001234567", "empresa@test.co",
                "4711", "JURIDICA", null,
                false,
                taxCodes, fiscalCodes, List.of(),
                null, "COP", null, null, null, null, null, null, null, null,
                null, "PIN-CONFIG", null, null, null, null, null, null, null,
                false, BigDecimal.ZERO, null, null);
    }

    private DianResolution activeResolution(String number, String pin) {
        return new DianResolution(
                UUID.randomUUID(), number, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                "FV", 1L, 1000L, 1L, pin, true, OffsetDateTime.now());
    }
}
