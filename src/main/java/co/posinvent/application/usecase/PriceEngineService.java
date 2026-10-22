package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.CustomPriceRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.TaxRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class PriceEngineService {

    private static final BigDecimal TAX_0 = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CustomPriceRepository customPriceRepo;
    private final ProductRepository productRepo;
    private final ThirdPartyRepository thirdPartyRepo;
    private final TaxRepository taxRepo;

    public PriceEngineService(
            CustomPriceRepository customPriceRepo,
            ProductRepository productRepo,
            ThirdPartyRepository thirdPartyRepo,
            TaxRepository taxRepo
    ) {
        this.customPriceRepo = customPriceRepo;
        this.productRepo = productRepo;
        this.thirdPartyRepo = thirdPartyRepo;
        this.taxRepo = taxRepo;
    }

    public PriceResult resolvePrice(UUID productId, UUID clientId) {
        // Tier 1: Custom price
        var client = thirdPartyRepo.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", clientId));

        var customPrice = customPriceRepo.findByClientIdAndProductId(clientId, productId);
        if (customPrice.isPresent()) {
            var cp = customPrice.get();
            var rate = resolveTaxRate(cp.taxType());
            var amount = calculateTax(cp.price(), rate);
            return new PriceResult(cp.price(), cp.taxType(), rate, amount);
        }

        // Tier 2: Price list entry
        if (client.priceListId() != null) {
            var product = productRepo.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));

            for (var entry : product.priceEntries()) {
                if (entry.priceListId().equals(client.priceListId())) {
                    var rate = resolveTaxRate(product.taxType());
                    var amount = calculateTax(entry.price(), rate);
                    return new PriceResult(entry.price(), product.taxType(), rate, amount);
                }
            }
        }

        // Tier 3: Fallback to salePrice
        var product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", productId));

        var rate = resolveTaxRate(product.taxType());
        var amount = calculateTax(product.salePrice(), rate);
        return new PriceResult(product.salePrice(), product.taxType(), rate, amount);
    }

    private BigDecimal resolveTaxRate(String taxType) {
        if (taxType == null || taxType.isBlank()) return TAX_0;
        return taxRepo.findByCode(taxType)
                .map(tax -> tax.percentage())
                .orElse(TAX_0);
    }

    private BigDecimal calculateTax(BigDecimal price, BigDecimal rate) {
        if (price == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return price.multiply(rate)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
