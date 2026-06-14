package co.posinvent.application.usecase;

import co.posinvent.domain.model.CustomPrice;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CustomPriceRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceEngineServiceTest {

    @Mock private CustomPriceRepository customPriceRepo;
    @Mock private ProductRepository productRepo;
    @Mock private ThirdPartyRepository thirdPartyRepo;

    private PriceEngineService service;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID PRICE_LIST_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PriceEngineService(customPriceRepo, productRepo, thirdPartyRepo);
    }

    // ── Tier 1: Custom price ──────────────────────────────────────────

    @Test
    void resolvePrice_returnsCustomPriceWhenExists() {
        var customPrice = new CustomPrice(
                null, CLIENT_ID, PRODUCT_ID,
                new BigDecimal("25000"), "IVA_19", BigDecimal.ZERO
        );

        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.of(customPrice));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.unitPrice()).isEqualByComparingTo("25000");
        assertThat(result.taxType()).isEqualTo("IVA_19");
        assertThat(result.taxRate()).isEqualByComparingTo("19");
        assertThat(result.taxAmount()).isEqualByComparingTo("4750.00");
    }

    // ── Tier 2: Price list entry ──────────────────────────────────────

    @Test
    void resolvePrice_usesPriceListWhenNoCustomPrice() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithPriceEntry(PRICE_LIST_ID)));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.unitPrice()).isEqualByComparingTo("1.2075");
        assertThat(result.taxType()).isEqualTo("IVA_5");
        assertThat(result.taxRate()).isEqualByComparingTo("5");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.06");
    }

    // ── Tier 3: Fallback to salePrice ─────────────────────────────────

    @Test
    void resolvePrice_fallsBackToSalePriceWhenNoCustomNorPriceList() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithoutPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithNoMatchingEntry()));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.unitPrice()).isEqualByComparingTo("30000");
        assertThat(result.taxType()).isEqualTo("IVA_19");
        assertThat(result.taxRate()).isEqualByComparingTo("19");
        assertThat(result.taxAmount()).isEqualByComparingTo("5700.00");
    }

    @Test
    void resolvePrice_usesSalePriceWhenClientHasNoPriceList() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithoutPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithSalePriceOnly()));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.unitPrice()).isEqualByComparingTo("30000");
        assertThat(result.taxType()).isEqualTo("EXENTO");
        assertThat(result.taxRate()).isEqualByComparingTo("0");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
    }

    // ── Tax rate resolution ───────────────────────────────────────────

    @Test
    void resolvePrice_iva19_taxRate() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithoutPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithTax("IVA_19", new BigDecimal("10000"))));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.taxRate()).isEqualByComparingTo("19");
        assertThat(result.taxAmount()).isEqualByComparingTo("1900.00");
    }

    @Test
    void resolvePrice_iva8_taxRate() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithoutPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithTax("IVA_8", new BigDecimal("10000"))));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.taxRate()).isEqualByComparingTo("8");
        assertThat(result.taxAmount()).isEqualByComparingTo("800.00");
    }

    @Test
    void resolvePrice_exento_taxRate() {
        when(thirdPartyRepo.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientWithoutPriceList()));
        when(customPriceRepo.findByClientIdAndProductId(CLIENT_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(productRepo.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithTax("EXENTO", new BigDecimal("10000"))));

        var result = service.resolvePrice(PRODUCT_ID, CLIENT_ID);

        assertThat(result.taxRate()).isEqualByComparingTo("0");
        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ThirdParty clientWithPriceList() {
        return makeClient(PRICE_LIST_ID);
    }

    private ThirdParty clientWithoutPriceList() {
        return makeClient(null);
    }

    private ThirdParty makeClient(UUID priceListId) {
        return new ThirdParty(
                CLIENT_ID, "123", "Cliente Test", ThirdParty.ThirdPartyType.CLIENT,
                priceListId, BigDecimal.ZERO, BigDecimal.ZERO,
                ThirdParty.PersonType.NATURAL, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null
        );
    }

    private Product productWithPriceEntry(UUID matchingPriceListId) {
        return makeProduct(
                "IVA_5",
                new BigDecimal("20000"),
                List.of(
                        new Product.ProductPriceEntry(null, UUID.randomUUID(), new BigDecimal("1.1550"), new BigDecimal("10")),
                        new Product.ProductPriceEntry(null, matchingPriceListId, new BigDecimal("1.2075"), new BigDecimal("15"))
                )
        );
    }

    private Product productWithNoMatchingEntry() {
        return makeProduct(
                "IVA_19",
                new BigDecimal("30000"),
                List.of(
                        new Product.ProductPriceEntry(null, UUID.randomUUID(), new BigDecimal("1.3090"), new BigDecimal("10"))
                )
        );
    }

    private Product productWithSalePriceOnly() {
        return makeProduct("EXENTO", new BigDecimal("30000"), List.of());
    }

    private Product productWithTax(String taxType, BigDecimal salePrice) {
        return makeProduct(taxType, salePrice, List.of());
    }

    private Product makeProduct(String taxType, BigDecimal salePrice,
                                 List<Product.ProductPriceEntry> priceEntries) {
        return new Product(
                PRODUCT_ID, "P001", "Producto Test", null, null, null,
                null, null, null, null, null, null, null,
                BigDecimal.ONE, BigDecimal.TEN,
                taxType, salePrice, "PROMEDIO_PONDERADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.ZERO,
                false, false, false, false, false, false,                 true,
                null, null, null, null, null, null, null, false, 0,
                null, null,
                List.of(), List.of(), List.of(), List.of(), priceEntries,
                List.of()
        );
    }
}
