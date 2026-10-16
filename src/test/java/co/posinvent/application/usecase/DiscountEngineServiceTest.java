package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DiscountEngineService}: line discounts (promotion + manual),
 * exact proration of the document discount and the authorization policy.
 */
@ExtendWith(MockitoExtension.class)
class DiscountEngineServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private CompanyConfigRepository companyConfigRepo;

    private DiscountEngineService engine;
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new DiscountEngineService(productRepo, companyConfigRepo);
    }

    // ── Promotion resolution ──────────────────────────────────────────────

    @Test
    void activePromotionPctReturnsZeroWhenNoPromotion() {
        when(productRepo.findById(productId)).thenReturn(Optional.of(productWith(List.of())));

        assertThat(engine.activePromotionPct(productId, LocalDate.now())).isEqualByComparingTo("0");
    }

    @Test
    void activePromotionPctReturnsZeroWhenPromotionOutOfRange() {
        var promo = new Product.ProductPromotion(UUID.randomUUID(), "Old", new BigDecimal("20"),
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(10), true);
        when(productRepo.findById(productId)).thenReturn(Optional.of(productWith(List.of(promo))));

        assertThat(engine.activePromotionPct(productId, LocalDate.now())).isEqualByComparingTo("0");
    }

    @Test
    void activePromotionPctReturnsPctWhenInRangeAndActive() {
        var promo = new Product.ProductPromotion(UUID.randomUUID(), "Live", new BigDecimal("15"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), true);
        when(productRepo.findById(productId)).thenReturn(Optional.of(productWith(List.of(promo))));

        assertThat(engine.activePromotionPct(productId, LocalDate.now())).isEqualByComparingTo("15");
    }

    // ── Line discount ─────────────────────────────────────────────────────

    @Test
    void lineDiscountAddsPromotionAndManualPercentage() {
        // gross 1000, promo 10% + manual 5% -> 15% -> 150.00
        var discount = engine.calculateLineDiscount(
                new BigDecimal("1000.00"), new BigDecimal("10"), "PCT", new BigDecimal("5"));

        assertThat(discount).isEqualByComparingTo("150.00");
    }

    @Test
    void lineDiscountAddsPromotionAndManualAmount() {
        // gross 1000, promo 10% (100) + fixed 50 -> 150.00
        var discount = engine.calculateLineDiscount(
                new BigDecimal("1000.00"), new BigDecimal("10"), "AMOUNT", new BigDecimal("50"));

        assertThat(discount).isEqualByComparingTo("150.00");
    }

    @Test
    void lineDiscountIsClampedToGross() {
        var discount = engine.calculateLineDiscount(
                new BigDecimal("1000.00"), BigDecimal.ZERO, "AMOUNT", new BigDecimal("2000"));

        assertThat(discount).isEqualByComparingTo("1000.00");
    }

    // ── Document discount + proration ─────────────────────────────────────

    @Test
    void documentDiscountIsProratedExactlyWithResidueOnLastLine() {
        var lines = List.of(
                line(new BigDecimal("333.33"), BigDecimal.ZERO),
                line(new BigDecimal("333.33"), BigDecimal.ZERO),
                line(new BigDecimal("333.34"), BigDecimal.ZERO));

        var result = engine.calculate(lines, "PCT", new BigDecimal("10"));

        // netBeforeDocument = 1000.00, documentDiscount = 100.00
        assertThat(result.documentDiscount()).isEqualByComparingTo("100.00");

        var allocatedSum = result.lines().stream()
                .map(DiscountEngineService.LineResult::documentAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocatedSum).isEqualByComparingTo("100.00");

        // Rounding residue (100 - 33.33 - 33.33 = 33.34) lands on the last line.
        assertThat(result.lines().get(2).documentAllocated()).isEqualByComparingTo("33.34");
        assertThat(result.net()).isEqualByComparingTo("900.00");
    }

    @Test
    void documentDiscountReducesTaxBaseAcrossRates() {
        // EXENTO 1000 + IVA_19 2000, 10% document discount -> 300 prorated (100 / 200)
        var lines = List.of(
                line(new BigDecimal("1000.00"), BigDecimal.ZERO),
                line(new BigDecimal("2000.00"), new BigDecimal("19")));

        var result = engine.calculate(lines, "PCT", new BigDecimal("10"));

        assertThat(result.net()).isEqualByComparingTo("2700.00");
        assertThat(result.tax0()).isEqualByComparingTo("0.00");
        assertThat(result.tax19()).isEqualByComparingTo("342.00"); // 1800 * 19%
        assertThat(result.total()).isEqualByComparingTo("3042.00");
        assertThat(result.discountTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void totalAlwaysEqualsNetPlusTaxes() {
        var lines = List.of(
                line(new BigDecimal("100.00"), new BigDecimal("19")),
                line(new BigDecimal("250.00"), new BigDecimal("5")));

        var result = engine.calculate(lines, "PCT", new BigDecimal("7"));

        assertThat(result.total()).isEqualByComparingTo(
                result.net().add(result.tax0()).add(result.tax5()).add(result.tax19()));
    }

    // ── Authorization policy ──────────────────────────────────────────────

    @Test
    void cashierBelowCeilingDoesNotRequireAuthorization() {
        assertThat(engine.requiresDiscountAuthorization(
                "CAJERO", new BigDecimal("5"), new BigDecimal("5"))).isFalse();
    }

    @Test
    void cashierAboveCeilingRequiresAuthorization() {
        assertThat(engine.requiresDiscountAuthorization(
                "CAJERO", new BigDecimal("6"), new BigDecimal("6"))).isTrue();
    }

    @Test
    void adminWithinGlobalCeilingDoesNotRequireAuthorization() {
        assertThat(engine.requiresDiscountAuthorization(
                "ADMIN", new BigDecimal("20"), new BigDecimal("25"))).isFalse();
    }

    @Test
    void totalAboveGlobalCeilingRequiresAuthorization() {
        assertThat(engine.requiresDiscountAuthorization(
                "ADMIN", BigDecimal.ZERO, new BigDecimal("35"))).isTrue();
    }

    @Test
    void validateAuthorizationThrowsWhenRequired() {
        assertThatThrownBy(() -> engine.validateAuthorization(
                "CAJERO", new BigDecimal("10"), new BigDecimal("10")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DISCOUNT_AUTHORIZATION_REQUIRED");
    }

    @Test
    void globalCeilingIsReadFromCompanyConfig() {
        when(companyConfigRepo.findConfig()).thenReturn(Optional.of(
                config(new BigDecimal("20"), new BigDecimal("5"))));

        assertThat(engine.requiresDiscountAuthorization(
                "ADMIN", BigDecimal.ZERO, new BigDecimal("25"))).isTrue();
        assertThat(engine.requiresDiscountAuthorization(
                "ADMIN", BigDecimal.ZERO, new BigDecimal("20"))).isFalse();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private DiscountEngineService.LineInput line(BigDecimal gross, BigDecimal taxRate) {
        return new DiscountEngineService.LineInput(
                null, gross, taxRate, BigDecimal.ZERO, "NONE", BigDecimal.ZERO);
    }

    private Product productWith(List<Product.ProductPromotion> promotions) {
        return new Product(
                productId, "P-1", "Product", null, null, null,
                null, null, null, null, null, null, null,
                BigDecimal.ZERO, null, "EXENTO", new BigDecimal("10.00"), null,
                null, null, null, null,
                false, false, false, false, false, false, false,
                null, null, null,
                null, null, null, null,
                true, 0, null, null,
                List.of(), List.of(), List.of(), promotions, List.of(), List.of());
    }

    private CompanyConfig config(BigDecimal max, BigDecimal cashierMax) {
        return new CompanyConfig(
                1L, "Company", "NIT", null, null, null, null, null, null, false,
                List.of(), List.of(), List.of(), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, false, null, null, null, null, max, cashierMax);
    }
}
