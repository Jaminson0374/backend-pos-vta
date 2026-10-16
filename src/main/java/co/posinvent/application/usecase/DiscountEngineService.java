package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.model.Product;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic discount engine (M2 / WU-26).
 *
 * <p>Discounts are applied BEFORE IVA: the line discount (automatic promotion plus
 * manual discount) reduces the taxable base, and the document discount is prorated
 * per line so the 0/5/19 breakdown still balances.</p>
 *
 * <p>Calculation is pure and deterministic; the only external inputs are the active
 * promotion of a product and the company discount ceilings, both read through
 * repositories. Token issuance/validation for discount authorization is out of scope
 * here (WU-27); this service only evaluates the policy and raises the error.</p>
 */
@Service
public class DiscountEngineService {

    public static final String DISCOUNT_NONE = "NONE";
    public static final String DISCOUNT_PCT = "PCT";
    public static final String DISCOUNT_AMOUNT = "AMOUNT";

    static final BigDecimal HUNDRED = new BigDecimal("100");
    static final BigDecimal DEFAULT_MAX_DISCOUNT_PCT = new BigDecimal("30");
    static final BigDecimal DEFAULT_CASHIER_MAX_DISCOUNT_PCT = new BigDecimal("5");
    static final String ROLE_CASHIER = "CAJERO";

    private final ProductRepository productRepo;
    private final CompanyConfigRepository companyConfigRepo;

    public DiscountEngineService(ProductRepository productRepo, CompanyConfigRepository companyConfigRepo) {
        this.productRepo = productRepo;
        this.companyConfigRepo = companyConfigRepo;
    }

    // ── Promotion resolution ──────────────────────────────────────────────

    /**
     * Best active promotion for a product on a given date (0% when none applies).
     * A promotion is active when {@code is_active} is true and the date is within
     * {@code [start_date, end_date]}. At most one is active per product (DB-enforced),
     * so picking the largest discount is equivalent to picking the customer's best.
     */
    public Optional<Product.ProductPromotion> activePromotion(UUID productId, LocalDate date) {
        if (productId == null) {
            return Optional.empty();
        }
        var day = date != null ? date : LocalDate.now();
        return productRepo.findById(productId)
                .map(Product::promotions)
                .orElseGet(List::of)
                .stream()
                .filter(Product.ProductPromotion::isActive)
                .filter(p -> p.discountPct() != null)
                .filter(p -> p.startDate() != null && p.endDate() != null)
                .filter(p -> !day.isBefore(p.startDate()) && !day.isAfter(p.endDate()))
                .max((a, b) -> a.discountPct().compareTo(b.discountPct()));
    }

    /** Active promotion percentage for a product on a given date; 0 when none. */
    public BigDecimal activePromotionPct(UUID productId, LocalDate date) {
        return activePromotion(productId, date)
                .map(Product.ProductPromotion::discountPct)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    // ── Line discount ─────────────────────────────────────────────────────

    /**
     * Line-level discount for a gross amount. The automatic promotion and the manual
     * discount accumulate (D8): for {@code PCT} the percentages add (clamped to 100%);
     * for {@code AMOUNT} the promotion amount is added to the fixed amount. The result
     * is clamped to {@code [0, gross]} with 2 decimals HALF_UP.
     */
    public BigDecimal calculateLineDiscount(
            BigDecimal gross, BigDecimal promoPct, String discountType, BigDecimal discountValue) {
        var base = scale2(gross);
        var promo = nullToZero(promoPct);
        var value = nullToZero(discountValue);
        var type = normalizeType(discountType);

        BigDecimal raw = switch (type) {
            case DISCOUNT_PCT -> {
                var pct = promo.add(value).max(BigDecimal.ZERO).min(HUNDRED);
                yield base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
            case DISCOUNT_AMOUNT -> {
                var promoAmount = base.multiply(promo).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                yield promoAmount.add(value);
            }
            default -> base.multiply(promo).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };

        return raw.max(BigDecimal.ZERO).min(base).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Document discount ─────────────────────────────────────────────────

    /** Document-level discount over the already line-discounted net, clamped to that net. */
    public BigDecimal calculateDocumentDiscount(
            BigDecimal netBeforeDocument, String discountType, BigDecimal discountValue) {
        var base = scale2(netBeforeDocument);
        var value = nullToZero(discountValue);
        var type = normalizeType(discountType);

        BigDecimal raw = switch (type) {
            case DISCOUNT_PCT -> base.multiply(value.max(BigDecimal.ZERO).min(HUNDRED))
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case DISCOUNT_AMOUNT -> value;
            default -> BigDecimal.ZERO;
        };

        return raw.max(BigDecimal.ZERO).min(base).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Full document calculation ─────────────────────────────────────────

    /**
     * Computes line discounts, prorated document discount, final taxable bases and taxes.
     *
     * <p>Invariant: {@code total = net + tax0 + tax5 + tax19} and
     * {@code net = netBeforeDocument − documentDiscount}.</p>
     */
    public DocumentResult calculate(
            List<LineInput> lines, String documentDiscountType, BigDecimal documentDiscountValue) {
        var inputs = lines == null ? List.<LineInput>of() : lines;
        var results = new ArrayList<LineResult>(inputs.size());

        var netBeforeDoc = BigDecimal.ZERO.setScale(2);
        var lineDiscountTotal = BigDecimal.ZERO.setScale(2);

        for (var in : inputs) {
            var gross = scale2(in.gross());
            var lineDiscount = calculateLineDiscount(gross, in.promoPct(), in.discountType(), in.discountValue());
            var baseGravable = gross.subtract(lineDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            netBeforeDoc = netBeforeDoc.add(baseGravable);
            lineDiscountTotal = lineDiscountTotal.add(lineDiscount);
            results.add(new LineResult(
                    in.promotionId(), gross, scale2(in.taxRate()), lineDiscount, baseGravable,
                    BigDecimal.ZERO.setScale(2), baseGravable, BigDecimal.ZERO.setScale(2),
                    normalizeType(in.discountType()), scale2(in.discountValue())));
        }

        var documentDiscount = calculateDocumentDiscount(netBeforeDoc, documentDiscountType, documentDiscountValue);

        // Prorate the document discount per line; push the rounding residue to the last line
        // so sum(allocations) == documentDiscount exactly.
        var allocated = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < results.size(); i++) {
            var current = results.get(i);
            BigDecimal lineDoc;
            if (i == results.size() - 1) {
                lineDoc = documentDiscount.subtract(allocated).max(BigDecimal.ZERO);
            } else if (netBeforeDoc.signum() == 0) {
                lineDoc = BigDecimal.ZERO.setScale(2);
            } else {
                lineDoc = documentDiscount.multiply(current.baseGravable())
                        .divide(netBeforeDoc, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(lineDoc);
            }

            var finalBase = current.baseGravable().subtract(lineDoc).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            var tax = finalBase.multiply(current.taxRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            results.set(i, new LineResult(
                    current.promotionId(), current.gross(), current.taxRate(), current.lineDiscount(),
                    current.baseGravable(), lineDoc, finalBase, tax,
                    current.discountType(), current.discountValue()));
        }

        var net = netBeforeDoc.subtract(documentDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        var tax0 = BigDecimal.ZERO.setScale(2);
        var tax5 = BigDecimal.ZERO.setScale(2);
        var tax19 = BigDecimal.ZERO.setScale(2);
        for (var r : results) {
            if (r.taxRate().compareTo(BigDecimal.ZERO) == 0) {
                tax0 = tax0.add(r.taxAmount());
            } else if (r.taxRate().compareTo(new BigDecimal("5")) == 0) {
                tax5 = tax5.add(r.taxAmount());
            } else {
                tax19 = tax19.add(r.taxAmount());
            }
        }

        var total = net.add(tax0).add(tax5).add(tax19);
        var discountTotal = lineDiscountTotal.add(documentDiscount);

        return new DocumentResult(
                results, netBeforeDoc, net, tax0, tax5, tax19, total,
                lineDiscountTotal, documentDiscount, discountTotal);
    }

    // ── Policy ────────────────────────────────────────────────────────────

    /**
     * Authorization is required when a CAJERO exceeds the manual cashier ceiling or when
     * the total discount exceeds the global ceiling.
     */
    public boolean requiresDiscountAuthorization(
            String role, BigDecimal manualPct, BigDecimal totalDiscountPct) {
        var max = configMaxDiscountPct();
        var cashierMax = configCashierMaxDiscountPct();
        var total = nullToZero(totalDiscountPct);
        var manual = nullToZero(manualPct);

        if (total.compareTo(max) > 0) {
            return true;
        }
        return ROLE_CASHIER.equalsIgnoreCase(role) && manual.compareTo(cashierMax) > 0;
    }

    /** Throws when the discount policy demands an authorization that was not granted. */
    public void validateAuthorization(
            String role, BigDecimal manualPct, BigDecimal totalDiscountPct) {
        if (requiresDiscountAuthorization(role, manualPct, totalDiscountPct)) {
            throw new BusinessException("DISCOUNT_AUTHORIZATION_REQUIRED",
                    "El descuento requiere autorización de un administrador");
        }
    }

    /** Largest manual percentage discount applied across the lines (0 when none). */
    public BigDecimal manualDiscountPct(List<LineInput> lines) {
        return (lines == null ? List.<LineInput>of() : lines).stream()
                .filter(l -> DISCOUNT_PCT.equals(normalizeType(l.discountType())))
                .map(l -> nullToZero(l.discountValue()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /** Total discount as a percentage of the gross line total (0 when the total is zero). */
    public BigDecimal totalDiscountPct(List<LineInput> lines, BigDecimal discountTotal) {
        var grossTotal = (lines == null ? List.<LineInput>of() : lines).stream()
                .map(LineInput::gross)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grossTotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return nullToZero(discountTotal).multiply(HUNDRED).divide(grossTotal, 2, RoundingMode.HALF_UP);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private BigDecimal configMaxDiscountPct() {
        return companyConfigRepo.findConfig()
                .map(CompanyConfig::maxDiscountPct)
                .filter(Objects::nonNull)
                .orElse(DEFAULT_MAX_DISCOUNT_PCT);
    }

    private BigDecimal configCashierMaxDiscountPct() {
        return companyConfigRepo.findConfig()
                .map(CompanyConfig::cashierMaxDiscountPct)
                .filter(Objects::nonNull)
                .orElse(DEFAULT_CASHIER_MAX_DISCOUNT_PCT);
    }

    static String normalizeType(String discountType) {
        if (discountType == null || discountType.isBlank()) {
            return DISCOUNT_NONE;
        }
        return discountType.trim().toUpperCase();
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ── Result types ──────────────────────────────────────────────────────

    /** Input line for the discount calculation (gross amount and per-line discount data). */
    public record LineInput(
            UUID promotionId,
            BigDecimal gross,
            BigDecimal taxRate,
            BigDecimal promoPct,
            String discountType,
            BigDecimal discountValue
    ) {}

    /** Per-line result: line discount, prorated document discount, final base and tax. */
    public record LineResult(
            UUID promotionId,
            BigDecimal gross,
            BigDecimal taxRate,
            BigDecimal lineDiscount,
            BigDecimal baseGravable,
            BigDecimal documentAllocated,
            BigDecimal finalBase,
            BigDecimal taxAmount,
            String discountType,
            BigDecimal discountValue
    ) {}

    /** Document-level totals after discounts, with the 0/5/19 tax breakdown. */
    public record DocumentResult(
            List<LineResult> lines,
            BigDecimal netBeforeDocument,
            BigDecimal net,
            BigDecimal tax0,
            BigDecimal tax5,
            BigDecimal tax19,
            BigDecimal total,
            BigDecimal lineDiscountTotal,
            BigDecimal documentDiscount,
            BigDecimal discountTotal
    ) {}
}
