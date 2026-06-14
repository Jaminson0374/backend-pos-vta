package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_return_lines")
@Getter
@Setter
public class PurchaseReturnLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private PurchaseReturnEntity purchaseReturn;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "return_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal returnQty;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;
}
