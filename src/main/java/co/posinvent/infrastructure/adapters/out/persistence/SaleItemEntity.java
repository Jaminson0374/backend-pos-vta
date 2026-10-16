package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_items")
@Getter
@Setter
public class SaleItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private SalesDocumentEntity document;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_type", nullable = false, length = 20)
    private String taxType;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "discount_type", nullable = false, length = 10)
    private String discountType = "NONE";

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "promotion_id")
    private UUID promotionId;
}
