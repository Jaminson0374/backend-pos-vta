package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_line_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"oc_id", "product_id"}))
@Getter
@Setter
class PurchaseLineItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oc_id", nullable = false)
    private PurchaseOrderEntity purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "ordered_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal orderedQty;

    @Column(name = "received_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "tax_type", length = 10)
    private String taxType = "EXENTO";

    @Column(name = "line_number", nullable = false)
    private int lineNumber;
}
